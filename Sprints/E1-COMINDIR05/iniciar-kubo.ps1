param([switch]$EmSegundoPlano)
$ErrorActionPreference='Stop'
$repoPath=Join-Path $PSScriptRoot 'execucoes/kubo-isolado'
$env:IPFS_PATH=$repoPath
$binary=(Get-Command ipfs -ErrorAction Stop).Source
if (Get-NetTCPConnection -State Listen -LocalPort 5005 -ErrorAction SilentlyContinue) { throw 'Porta 5005 ocupada; nao iniciar outro daemon.' }
if (!(Test-Path -LiteralPath (Join-Path $repoPath 'config'))) {
    New-Item -ItemType Directory -Force -Path $repoPath | Out-Null
    & $binary init
    if($LASTEXITCODE -ne 0){throw 'Kubo init falhou'}
}
    $configPath=Join-Path $repoPath 'config'
    $config=Get-Content -LiteralPath $configPath -Raw | ConvertFrom-Json
    $config.Bootstrap=@()
    $config.Discovery.MDNS.Enabled=$false
    $config.Pubsub | Add-Member -MemberType NoteProperty -Name Enabled -Value $true -Force
    $config.Addresses.Swarm=@('/ip4/127.0.0.1/tcp/4005')
    $config.Addresses.API='/ip4/127.0.0.1/tcp/5005'
    $config.Addresses.Gateway='/ip4/127.0.0.1/tcp/8085'
    [IO.File]::WriteAllText($configPath,($config | ConvertTo-Json -Depth 100),(New-Object Text.UTF8Encoding $false))
if($EmSegundoPlano){
    $p=Start-Process -FilePath $binary -ArgumentList @('daemon') -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $repoPath 'daemon.log') -RedirectStandardError (Join-Path $repoPath 'daemon-erro.log')
    Write-Output ("KUBO_PID="+$p.Id)
}else{& $binary daemon}
