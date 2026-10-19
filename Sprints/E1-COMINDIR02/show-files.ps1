[CmdletBinding()]
param()

$taskRoot = $PSScriptRoot
$leaderRoot = Join-Path $taskRoot 'rep02-lider'
$replicaRoot = Join-Path $taskRoot 'rep02-replica'

$sections = @(
    @{ Title = 'LEADER P0'; Path = Join-Path $leaderRoot 'lider-p0.txt' },
    @{ Title = 'REPLICA 1 - P0'; Path = Join-Path $replicaRoot 'replica-1-p0.txt' },
    @{ Title = 'REPLICA 3 - P0'; Path = Join-Path $replicaRoot 'replica-3-p0.txt' },
    @{ Title = 'LEADER P1'; Path = Join-Path $leaderRoot 'lider-p1.txt' },
    @{ Title = 'REPLICA 1 - P1'; Path = Join-Path $replicaRoot 'replica-1-p1.txt' },
    @{ Title = 'REPLICA 2 - P1'; Path = Join-Path $replicaRoot 'replica-2-p1.txt' },
    @{ Title = 'LEADER P2'; Path = Join-Path $leaderRoot 'lider-p2.txt' },
    @{ Title = 'REPLICA 2 - P2'; Path = Join-Path $replicaRoot 'replica-2-p2.txt' },
    @{ Title = 'REPLICA 3 - P2'; Path = Join-Path $replicaRoot 'replica-3-p2.txt' }
)

foreach ($section in $sections) {
    Write-Host ''
    Write-Host '================================'
    Write-Host $section.Title -ForegroundColor Cyan
    Write-Host '================================'
    if (Test-Path -LiteralPath $section.Path) {
        $item = Get-Item -LiteralPath $section.Path
        if ($item.Length -eq 0) {
            Write-Host "[FICHEIRO VAZIO] $($section.Path)" -ForegroundColor Yellow
        } else {
            Get-Content -LiteralPath $section.Path
        }
    } else {
        Write-Host "[AINDA NÃO EXISTE] $($section.Path)" -ForegroundColor Yellow
    }
}
