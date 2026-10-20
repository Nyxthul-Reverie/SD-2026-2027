# Parte A - mostra os 4 ficheiros de p0 e compara cada replica com o lider.
param([Parameter(Mandatory)][string]$Dir, [int]$Tail = 15)

function Get-Lines([string]$Path) {
    if (-not (Test-Path -LiteralPath $Path)) { return $null }
    return @(Get-Content -LiteralPath $Path | Where-Object { -not [string]::IsNullOrWhiteSpace($_) })
}

$names = 'lider-p0.txt', 'replica-1-p0.txt', 'replica-2-p0.txt', 'replica-3-p0.txt'
foreach ($name in $names) {
    $lines = Get-Lines (Join-Path $Dir $name)
    Write-Host ''
    Write-Host "===== $name =====" -ForegroundColor Cyan
    if ($null -eq $lines) { Write-Host '[NAO EXISTE]' -ForegroundColor Yellow; continue }
    if ($lines.Count -gt $Tail) { Write-Host "... ($($lines.Count - $Tail) linhas anteriores omitidas)" -ForegroundColor DarkGray }
    $lines | Select-Object -Last $Tail | ForEach-Object { Write-Host $_ }
}

$leader = Get-Lines (Join-Path $Dir 'lider-p0.txt')
if ($null -eq $leader) { $leader = @() }
$hash = { param($p) if (Test-Path -LiteralPath $p) { (Get-FileHash -LiteralPath $p -Algorithm SHA256).Hash.Substring(0, 12) } else { '-' } }

Write-Host ''
Write-Host '===== CONVERGENCIA p0 =====' -ForegroundColor Cyan
Write-Host ("{0,-18} {1,9} {2,9}  {3,-12}  {4}" -f 'ficheiro', 'registos', 'ult. seq', 'sha256', 'estado')
$all = $true
foreach ($name in $names) {
    $path = Join-Path $Dir $name
    $lines = Get-Lines $path
    if ($null -eq $lines) { $lines = @() }
    $lastSeq = if ($lines.Count -gt 0) { ($lines[-1] -split ';', 2)[0] } else { '0' }
    if ($name -eq 'lider-p0.txt') {
        $state = 'referencia'; $color = 'White'
    } elseif (($lines -join "`n") -eq ($leader -join "`n")) {
        $state = 'IGUAL'; $color = 'Green'
    } else {
        $all = $false
        $prefix = $lines.Count -le $leader.Count -and
            (($lines -join "`n") -eq (($leader | Select-Object -First $lines.Count) -join "`n"))
        if ($prefix) { $state = "PREFIXO (faltam $($leader.Count - $lines.Count))"; $color = 'Yellow' }
        else { $state = 'DIVERGENTE'; $color = 'Red' }
    }
    Write-Host ("{0,-18} {1,9} {2,9}  {3,-12}  {4}" -f $name, $lines.Count, $lastSeq, (& $hash $path), $state) -ForegroundColor $color
}
Write-Host ''
if ($all) { Write-Host 'RESULTADO: as 3 replicas convergiram com o lider.' -ForegroundColor Green }
else { Write-Host 'RESULTADO: ainda ha replicas por convergir.' -ForegroundColor Yellow }

