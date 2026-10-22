# Parte A - arranca a Replica3 com -Ddescartar=<proximo seq de p0>.
# Pre-condicoes: a task "A - Replica3" ja foi terminada e a R3 esta igual ao lider.
param([Parameter(Mandatory)][string]$Dir)

$ErrorActionPreference = 'Stop'
Set-Location -LiteralPath $Dir
$leader = Join-Path $Dir 'lider-p0.txt'
$r3 = Join-Path $Dir 'replica-3-p0.txt'

# 1. Nao pode haver outra Replica3 a escrever no mesmo ficheiro.
$running = @(Get-CimInstance Win32_Process -Filter "Name='java.exe'" |
    Where-Object { $_.CommandLine -match 'rep02\.PartitionReplica\s+3\s' })
if ($running.Count -gt 0) {
    Write-Host "ABORTADO: a Replica3 ainda esta em execucao (PID $($running.ProcessId -join ', '))." -ForegroundColor Red
    Write-Host 'Termine primeiro a task "A - Replica3" (icone do caixote no terminal).' -ForegroundColor Red
    exit 1
}

function Get-Lines([string]$Path) {
    if (-not (Test-Path -LiteralPath $Path)) { return @() }
    return @(Get-Content -LiteralPath $Path | Where-Object { -not [string]::IsNullOrWhiteSpace($_) })
}

# 2. A R3 tem de estar convergida, para que o seq descartado seja exatamente o proximo.
$leaderLines = Get-Lines $leader
$r3Lines = Get-Lines $r3
if (($leaderLines -join "`n") -ne ($r3Lines -join "`n")) {
    Write-Host "ABORTADO: replica-3-p0.txt ($($r3Lines.Count) registos) difere de lider-p0.txt ($($leaderLines.Count) registos)." -ForegroundColor Red
    exit 1
}

$last = 0L
if ($leaderLines.Count -gt 0) { $last = [long](($leaderLines[-1] -split ';', 2)[0]) }
$seq = $last + 1

Write-Host '==================================================' -ForegroundColor Magenta
Write-Host " A - Replica3 Recovery   (ultimo seq = $last)" -ForegroundColor Magenta
Write-Host " -Ddescartar=$seq  -> o REG $seq sera DESCARTADO" -ForegroundColor Magenta
Write-Host ''
Write-Host ' No Producer (teste 4.1 c):' -ForegroundColor Magenta
Write-Host "   S01 20        -> $seq DESCARTADO e mais nada: perda do ultimo registo NAO detetada" -ForegroundColor Magenta
Write-Host "   rajada 10 1   -> $($seq+1)..$($seq+10) EM ESPERA (10 retidos, ainda sem TCP)" -ForegroundColor Magenta
Write-Host "   S01 21        -> $($seq+11) e o 11.o retido -> RECUPERACAO $seq..$seq por TCP" -ForegroundColor Magenta
Write-Host '==================================================' -ForegroundColor Magenta

& java "-Ddescartar=$seq" -cp '..\rep02-replica\out' rep02.PartitionReplica 3 0

