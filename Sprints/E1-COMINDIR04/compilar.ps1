param([string]$Jdk = "$env:USERPROFILE/.jdks/openjdk-23")
$ErrorActionPreference = 'Stop'
$compiler = Join-Path $Jdk 'bin/javac.exe'
if (!(Test-Path $compiler)) { throw 'Indica o JDK: ./compilar.ps1 -Jdk C:/caminho/jdk' }
foreach ($project in Get-ChildItem $PSScriptRoot -Directory -Filter 'rep04-*') {
    $output = Join-Path $project.FullName 'out'
    New-Item -ItemType Directory -Force $output | Out-Null
    $sources = @(Get-ChildItem (Join-Path $project.FullName 'src/rep04') -Filter '*.java')
    & $compiler --release 11 -encoding UTF-8 -d $output $sources.FullName
    if ($LASTEXITCODE) { throw "Erro ao compilar $($project.Name)" }
    Write-Host "$($project.Name): compilado"
}
New-Item -ItemType Directory -Force (Join-Path $PSScriptRoot 'ensaios/sessao-inicial') | Out-Null
