[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$stateFile = Join-Path $PSScriptRoot '.presentation-processes.json'

if (-not (Test-Path -LiteralPath $stateFile)) {
    Write-Host "Não existe estado de arranque em $stateFile. Nada foi terminado." -ForegroundColor Yellow
    exit 0
}

$parsedState = Get-Content -Raw -LiteralPath $stateFile | ConvertFrom-Json
$entries = @($parsedState | ForEach-Object { $_ })
$allProcesses = @(Get-CimInstance Win32_Process)
$childrenByParent = @{}
foreach ($processInfo in $allProcesses) {
    $parentId = [int]$processInfo.ParentProcessId
    if (-not $childrenByParent.ContainsKey($parentId)) {
        $childrenByParent[$parentId] = [System.Collections.Generic.List[int]]::new()
    }
    $childrenByParent[$parentId].Add([int]$processInfo.ProcessId)
}

function Get-Descendants {
    param([int]$RootProcessId)

    $result = [System.Collections.Generic.List[int]]::new()
    if ($childrenByParent.ContainsKey($RootProcessId)) {
        foreach ($childId in $childrenByParent[$RootProcessId]) {
            foreach ($descendantId in (Get-Descendants -RootProcessId $childId)) {
                $result.Add($descendantId)
            }
            $result.Add($childId)
        }
    }
    return $result
}

$reverseEntries = @($entries)
[array]::Reverse($reverseEntries)
foreach ($entry in $reverseEntries) {
    $processId = [int]$entry.ProcessId
    if ($processId -eq $PID) {
        Write-Host "Ignorado $($entry.Role): o PID coincide com este script." -ForegroundColor Yellow
        continue
    }
    $process = Get-Process -Id $processId -ErrorAction SilentlyContinue
    if (-not $process) {
        Write-Host "$($entry.Title): já não está em execução."
        continue
    }
    if ($entry.StartTimeUtc -is [DateTime]) {
        $expectedStart = $entry.StartTimeUtc.ToUniversalTime()
    } else {
        $expectedStart = [DateTime]::ParseExact(
            [string]$entry.StartTimeUtc,
            'o',
            [Globalization.CultureInfo]::InvariantCulture,
            [Globalization.DateTimeStyles]::RoundtripKind
        ).ToUniversalTime()
    }
    $actualStart = $process.StartTime.ToUniversalTime()
    if ([Math]::Abs(($actualStart - $expectedStart).TotalSeconds) -gt 2) {
        Write-Host "$($entry.Title): PID $processId foi reutilizado; não será terminado." -ForegroundColor Red
        continue
    }

    $tree = @(Get-Descendants -RootProcessId $processId)
    foreach ($childId in $tree) {
        if ($childId -ne $PID) {
            Stop-Process -Id $childId -Force -ErrorAction SilentlyContinue
        }
    }
    Stop-Process -Id $processId -Force -ErrorAction SilentlyContinue
    Write-Host "$($entry.Title): terminado." -ForegroundColor Green
}

Write-Host 'Paragem concluída. O ficheiro de estado foi preservado para auditoria.'
