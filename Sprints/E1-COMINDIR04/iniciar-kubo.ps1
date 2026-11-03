$ErrorActionPreference = 'Stop'
$env:IPFS_PATH = Join-Path $PSScriptRoot 'ensaios/kubo-isolado'
$binary = (Get-Command ipfs -ErrorAction Stop).Source
if (!(Test-Path (Join-Path $env:IPFS_PATH 'config'))) {
    New-Item -ItemType Directory -Force $env:IPFS_PATH | Out-Null
    & $binary init
    if ($LASTEXITCODE) { throw 'Falha ao criar o repositorio Kubo de teste' }
    & $binary bootstrap rm --all
    & $binary config --json Discovery.MDNS.Enabled false
    & $binary config --json Pubsub.Enabled true
    # Usar array via config do PowerShell evita diferenças de aspas entre shells.
    $configPath = Join-Path $env:IPFS_PATH 'config'
    $config = Get-Content -LiteralPath $configPath -Raw | ConvertFrom-Json
    $config.Addresses.Swarm = @('/ip4/127.0.0.1/tcp/4003')
    $config.Addresses.API = '/ip4/127.0.0.1/tcp/5003'
    $config.Addresses.Gateway = '/ip4/127.0.0.1/tcp/8083'
    $json = $config | ConvertTo-Json -Depth 100
    [IO.File]::WriteAllText($configPath, $json, (New-Object Text.UTF8Encoding $false))
}
Write-Host 'Kubo de teste: API 5003. Manter esta janela aberta; Ctrl+C para terminar.'
& $binary daemon
