param([string]$Jdk)
$ErrorActionPreference = 'Stop'
$compiler = if ($Jdk) { Join-Path $Jdk 'bin/javac.exe' } else { (Get-Command javac -ErrorAction Stop).Source }
foreach ($project in @('raft04-no','raft04-produtor','raft04-processador')) {
    $outDir = Join-Path $PSScriptRoot ($project + '/out')
    New-Item -ItemType Directory -Force -Path $outDir | Out-Null
    $sources = @(Get-ChildItem -LiteralPath (Join-Path $PSScriptRoot ($project + '/src/raft04')) -Filter '*.java')
    if ($sources.Count -eq 0) { throw "Projeto ainda sem fontes: $project" }
    & $compiler --release 11 -encoding UTF-8 -d $outDir $sources.FullName
    if ($LASTEXITCODE -ne 0) { throw "Compilacao falhou: $project" }
    Write-Host "$project compilado para Java 11"
}
