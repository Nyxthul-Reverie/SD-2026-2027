$ErrorActionPreference='Stop'
$root=Split-Path $PSScriptRoot -Parent
$javac=(Get-Command javac).Source
$out=Join-Path $root 'testes/out'
New-Item -ItemType Directory -Force -Path $out | Out-Null
$cp=(Join-Path $root 'raft04-no/out')+';'+(Join-Path $root 'raft04-produtor/out')
& $javac --release 11 -encoding UTF-8 -cp $cp -d $out (Join-Path $PSScriptRoot 'ProtocolTest.java')
if($LASTEXITCODE -ne 0){throw 'Compilacao dos testes falhou'}
$dir=Join-Path $root ('execucoes/unit-'+[Guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $dir | Out-Null
Push-Location -LiteralPath $dir
try {
    & java -cp "$out;$cp" raft04.ProtocolTest $dir
    if($LASTEXITCODE -ne 0){throw 'ProtocolTest falhou'}
} finally { Pop-Location }
