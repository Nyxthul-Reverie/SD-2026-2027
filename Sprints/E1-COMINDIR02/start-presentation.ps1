[CmdletBinding()]
param(
    [switch]$RecoveryDemoP0
)

$ErrorActionPreference = 'Stop'

$taskRoot = $PSScriptRoot
$producerRoot = Join-Path $taskRoot 'rep02-produtor'
$leaderRoot = Join-Path $taskRoot 'rep02-lider'
$replicaRoot = Join-Path $taskRoot 'rep02-replica'
$kubo039 = 'C:\Users\admin\Apps\kubo_v0.39.0\kubo\ipfs.exe'
$kubo043 = 'C:\Users\admin\Apps\kubo_v0.43.1\kubo\ipfs.exe'
$ipfsRepo = 'C:\Users\admin\.ipfs-comindir02-039'
$apiUri = 'http://127.0.0.1:5001/api/v0'
$stateFile = Join-Path $taskRoot '.presentation-processes.json'
$started = [System.Collections.Generic.List[object]]::new()

function Assert-ExistingPath {
    param([string]$Path, [string]$Label)

    if (-not (Test-Path -LiteralPath $Path)) {
        throw "$Label não encontrado: $Path"
    }
}

function Get-ListeningProcess {
    param([int]$Port)

    $connections = @(Get-NetTCPConnection -State Listen -LocalPort $Port -ErrorAction SilentlyContinue)
    if ($connections.Count -eq 0) {
        return $null
    }
    if ($connections.Count -gt 1) {
        throw "Existem vários processos à escuta na porta $Port; verifique-os manualmente."
    }
    $processId = $connections[0].OwningProcess
    return Get-CimInstance Win32_Process -Filter "ProcessId=$processId"
}

function Wait-PortFree {
    param([int]$Port, [int]$Seconds = 10)

    $deadline = (Get-Date).AddSeconds($Seconds)
    do {
        if (-not (Get-ListeningProcess -Port $Port)) {
            return
        }
        Start-Sleep -Milliseconds 250
    } while ((Get-Date) -lt $deadline)
    throw "A porta $Port não ficou livre dentro de $Seconds segundos."
}

function New-EncodedPowerShellCommand {
    param([string[]]$Lines)

    $text = $Lines -join [Environment]::NewLine
    return [Convert]::ToBase64String([Text.Encoding]::Unicode.GetBytes($text))
}

function Start-PresentationWindow {
    param(
        [string]$Role,
        [string]$Title,
        [string]$WorkingDirectory,
        [string[]]$CommandLines
    )

    $safeTitle = $Title.Replace("'", "''")
    $safeDirectory = $WorkingDirectory.Replace("'", "''")
    $lines = @(
        "`$Host.UI.RawUI.WindowTitle = '$safeTitle'"
        "Set-Location -LiteralPath '$safeDirectory'"
        "Write-Host '$safeTitle' -ForegroundColor Cyan"
    ) + $CommandLines
    $encoded = New-EncodedPowerShellCommand -Lines $lines
    $process = Start-Process -FilePath 'powershell.exe' `
        -ArgumentList @('-NoLogo', '-NoExit', '-EncodedCommand', $encoded) `
        -WorkingDirectory $WorkingDirectory -WindowStyle Normal -PassThru
    Start-Sleep -Milliseconds 350
    if ($process.HasExited) {
        throw "A janela $Title terminou durante o arranque."
    }
    $entry = [pscustomobject]@{
        Role = $Role
        Title = $Title
        ProcessId = $process.Id
        StartTimeUtc = $process.StartTime.ToUniversalTime().ToString('o')
    }
    $started.Add($entry)
    return $process
}

function Compile-Project {
    param([string]$ProjectRoot, [string]$Label)

    Write-Host "A compilar $Label..." -ForegroundColor Yellow
    $sources = @(Get-ChildItem -LiteralPath (Join-Path $ProjectRoot 'src\rep02') -Filter '*.java' |
        Select-Object -ExpandProperty FullName)
    if ($sources.Count -eq 0) {
        throw "Não foram encontrados ficheiros Java em $ProjectRoot."
    }
    Push-Location $ProjectRoot
    try {
        & javac -d out $sources
        if ($LASTEXITCODE -ne 0) {
            throw "Compilação de $Label falhou com código $LASTEXITCODE."
        }
    } finally {
        Pop-Location
    }
    Write-Host "$Label compile = OK" -ForegroundColor Green
}

function Get-LastSequence {
    param([string]$Path)

    if (-not (Test-Path -LiteralPath $Path)) {
        return 0L
    }
    $lastLine = Get-Content -LiteralPath $Path | Where-Object { -not [string]::IsNullOrWhiteSpace($_) } |
        Select-Object -Last 1
    if ($null -eq $lastLine) {
        return 0L
    }
    $field = ($lastLine -split ';', 2)[0]
    $value = 0L
    if (-not [long]::TryParse($field, [ref]$value)) {
        throw "Não foi possível ler o último seq de $Path."
    }
    return $value
}

function Assert-RecoveryDemoState {
    $leaderFile = Join-Path $leaderRoot 'lider-p0.txt'
    $replicaFile = Join-Path $replicaRoot 'replica-3-p0.txt'
    $leaderExists = Test-Path -LiteralPath $leaderFile
    $replicaExists = Test-Path -LiteralPath $replicaFile

    if ($leaderExists -ne $replicaExists) {
        throw 'A demonstração de recuperação requer lider-p0.txt e replica-3-p0.txt ambos ausentes ou ambos presentes.'
    }
    if ($leaderExists) {
        $leaderHash = (Get-FileHash -LiteralPath $leaderFile -Algorithm SHA256).Hash
        $replicaHash = (Get-FileHash -LiteralPath $replicaFile -Algorithm SHA256).Hash
        if ($leaderHash -ne $replicaHash) {
            throw 'A demonstração de recuperação requer a R3 sincronizada com o líder p0. Execute primeiro a demonstração normal.'
        }
    }
    return (Get-LastSequence -Path $leaderFile) + 1L
}

try {
    Assert-ExistingPath -Path $producerRoot -Label 'Projeto produtor'
    Assert-ExistingPath -Path $leaderRoot -Label 'Projeto líder'
    Assert-ExistingPath -Path $replicaRoot -Label 'Projeto réplica'
    Assert-ExistingPath -Path $kubo039 -Label 'Kubo 0.39.0'
    Assert-ExistingPath -Path $ipfsRepo -Label 'Repositório IPFS COMINDIR02'

    Write-Host "Java:" -ForegroundColor Yellow
    & java -version
    if ($LASTEXITCODE -ne 0) { throw 'Java não está disponível.' }
    & javac -version
    if ($LASTEXITCODE -ne 0) { throw 'javac não está disponível.' }

    $recoverySequence = $null
    if ($RecoveryDemoP0) {
        $recoverySequence = Assert-RecoveryDemoState
        Write-Host "Recuperação armada: a R3 descartará o próximo seq de p0 ($recoverySequence)." -ForegroundColor Magenta
        Write-Host 'No Producer, use apenas S01 durante este teste: uma leitura a descartar e mais 11 para disparar a recuperação.' -ForegroundColor Magenta
    }

    $listener5001 = Get-ListeningProcess -Port 5001
    $reuseKubo = $false
    if ($listener5001) {
        $runningExecutable = [string]$listener5001.ExecutablePath
        Write-Host "Porta 5001 ocupada por PID $($listener5001.ProcessId): $runningExecutable" -ForegroundColor Yellow
        if ([string]::Equals($runningExecutable, $kubo039, [StringComparison]::OrdinalIgnoreCase)) {
            Write-Host 'Já está em execução o Kubo 0.39.0 esperado; será reutilizado.' -ForegroundColor Green
            $reuseKubo = $true
        } elseif ([string]::Equals($runningExecutable, $kubo043, [StringComparison]::OrdinalIgnoreCase)) {
            Write-Host "A terminar apenas o Kubo incompatível 0.43.1, PID $($listener5001.ProcessId)." -ForegroundColor Yellow
            Stop-Process -Id $listener5001.ProcessId -Force
            Wait-PortFree -Port 5001
        } else {
            throw "A porta 5001 pertence a um processo não autorizado: $runningExecutable (PID $($listener5001.ProcessId)). Nada foi terminado."
        }
    }

    foreach ($port in 7000, 7001, 7002) {
        $listener = Get-ListeningProcess -Port $port
        if ($listener) {
            throw "A porta $port já está ocupada por $($listener.ExecutablePath) (PID $($listener.ProcessId)). Execute stop-presentation.ps1 ou verifique manualmente."
        }
    }

    if (-not $reuseKubo) {
        $safeIpfsRepo = $ipfsRepo.Replace("'", "''")
        $safeKubo = $kubo039.Replace("'", "''")
        [void](Start-PresentationWindow -Role 'KUBO' -Title 'COMINDIR02 - KUBO' -WorkingDirectory $taskRoot -CommandLines @(
            "`$env:IPFS_PATH = '$safeIpfsRepo'"
            "& '$safeKubo' daemon --enable-pubsub-experiment"
            "Write-Host 'Kubo terminou. Esta janela permanece aberta para diagnóstico.' -ForegroundColor Yellow"
        ))
    }

    Write-Host 'A aguardar pela API do Kubo...' -ForegroundColor Yellow
    $apiReady = $false
    $deadline = (Get-Date).AddSeconds(30)
    do {
        try {
            $versionJson = & curl.exe --silent --show-error --fail --request POST "$apiUri/version" 2>$null
            if ($LASTEXITCODE -eq 0 -and $versionJson) {
                $versionResponse = $versionJson | ConvertFrom-Json
            }
            if ($versionResponse -and $versionResponse.Version) {
                $apiReady = $true
                break
            }
        } catch {
            $versionResponse = $null
        }
        if (-not $apiReady) {
            Start-Sleep -Milliseconds 500
        }
    } while ((Get-Date) -lt $deadline)
    if (-not $apiReady) {
        throw 'A API do Kubo não ficou pronta na porta 5001 dentro de 30 segundos.'
    }
    if ($versionResponse.Version -ne '0.39.0') {
        throw "A API 5001 respondeu com Kubo $($versionResponse.Version), não 0.39.0."
    }
    Write-Host "Kubo API = OK ($($versionResponse.Version))" -ForegroundColor Green
    $pubsubJson = & curl.exe --silent --show-error --fail --request POST "$apiUri/pubsub/ls" 2>$null
    if ($LASTEXITCODE -ne 0 -or -not $pubsubJson) {
        throw 'A API respondeu, mas o PubSub não está ativado. Pare o ambiente e volte a arrancar pelo script.'
    }
    Write-Host 'Kubo PubSub = OK' -ForegroundColor Green

    Compile-Project -ProjectRoot $producerRoot -Label 'rep02-produtor'
    Compile-Project -ProjectRoot $leaderRoot -Label 'rep02-lider'
    Compile-Project -ProjectRoot $replicaRoot -Label 'rep02-replica'

    [void](Start-PresentationWindow -Role 'R1' -Title 'COMINDIR02 - REPLICA R1' -WorkingDirectory $replicaRoot -CommandLines @(
        '& java -cp out rep02.PartitionReplica 1 ''0,1'''
        "Write-Host 'R1 terminou. Esta janela permanece aberta para diagnóstico.' -ForegroundColor Yellow"
    ))
    [void](Start-PresentationWindow -Role 'R2' -Title 'COMINDIR02 - REPLICA R2' -WorkingDirectory $replicaRoot -CommandLines @(
        '& java -cp out rep02.PartitionReplica 2 ''1,2'''
        "Write-Host 'R2 terminou. Esta janela permanece aberta para diagnóstico.' -ForegroundColor Yellow"
    ))
    $r3Command = if ($RecoveryDemoP0) {
        "& java '-Ddescartar=$recoverySequence' -cp out rep02.PartitionReplica 3 '2,0'"
    } else {
        "& java -cp out rep02.PartitionReplica 3 '2,0'"
    }
    [void](Start-PresentationWindow -Role 'R3' -Title 'COMINDIR02 - REPLICA R3' -WorkingDirectory $replicaRoot -CommandLines @(
        $r3Command
        "Write-Host 'R3 terminou. Esta janela permanece aberta para diagnóstico.' -ForegroundColor Yellow"
    ))
    Start-Sleep -Seconds 2

    [void](Start-PresentationWindow -Role 'P0' -Title 'COMINDIR02 - LEADER P0' -WorkingDirectory $leaderRoot -CommandLines @(
        '& java -cp out rep02.PartitionLeader 0 3'
        "Write-Host 'Leader P0 terminou. Esta janela permanece aberta para diagnóstico.' -ForegroundColor Yellow"
    ))
    [void](Start-PresentationWindow -Role 'P1' -Title 'COMINDIR02 - LEADER P1' -WorkingDirectory $leaderRoot -CommandLines @(
        '& java -cp out rep02.PartitionLeader 1 3'
        "Write-Host 'Leader P1 terminou. Esta janela permanece aberta para diagnóstico.' -ForegroundColor Yellow"
    ))
    [void](Start-PresentationWindow -Role 'P2' -Title 'COMINDIR02 - LEADER P2' -WorkingDirectory $leaderRoot -CommandLines @(
        '& java -cp out rep02.PartitionLeader 2 3'
        "Write-Host 'Leader P2 terminou. Esta janela permanece aberta para diagnóstico.' -ForegroundColor Yellow"
    ))
    Start-Sleep -Seconds 2

    [void](Start-PresentationWindow -Role 'PRODUCER' -Title 'COMINDIR02 - PRODUCER' -WorkingDirectory $producerRoot -CommandLines @(
        '& java -cp out rep02.Producer 3'
        "Write-Host 'Producer terminou. Esta janela permanece aberta para diagnóstico.' -ForegroundColor Yellow"
    ))

    $started | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath $stateFile -Encoding UTF8
    Write-Host ''
    Write-Host 'COMINDIR02 pronta. O Producer está interativo numa janela própria.' -ForegroundColor Green
    Write-Host "Estado dos processos: $stateFile"
} catch {
    if ($started.Count -gt 0) {
        $started | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath $stateFile -Encoding UTF8
    }
    Write-Error $_
    exit 1
}
