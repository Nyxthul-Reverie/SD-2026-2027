param([ValidateSet('a','b','c','d','e','statefaults','networkfaults','faults','all')][string]$Suite='all')
$ErrorActionPreference='Stop'
$root=Split-Path $PSScriptRoot -Parent
$out=Join-Path $PSScriptRoot 'out'
New-Item -ItemType Directory -Force -Path $out | Out-Null
$cp=Join-Path $root 'raft04-no/out'
& javac --release 11 -encoding UTF-8 -cp $cp -d $out (Join-Path $PSScriptRoot 'Experiments.java')
if($LASTEXITCODE -ne 0){throw 'Compilacao dos ensaios falhou'}
& java '-Dfile.encoding=UTF-8' -cp "$out;$cp" raft04.Experiments $root $Suite
if($LASTEXITCODE -ne 0){throw 'Ensaio falhou; consultar execucoes/suite-*'}
