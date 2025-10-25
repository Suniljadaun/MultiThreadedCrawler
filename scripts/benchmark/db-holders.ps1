# Measures the holder lookup run on every price change (PositionRepository.findHolderIds):
# query plan plus 30 s of pgbench with 4 clients. Run once before and once after the index change.
#   .\scripts\benchmark\db-holders.ps1 -Label before-index
param([Parameter(Mandatory = $true)][string]$Label)
$ErrorActionPreference = "Continue"

$root = (Resolve-Path "$PSScriptRoot\..\..").Path
$out = Join-Path $root "benchmark-results\db-holders-$Label-$(Get-Date -Format 'yyyyMMdd-HHmmss')"
New-Item -ItemType Directory -Force -Path $out | Out-Null
& "$PSScriptRoot\env.ps1" | Out-File -Encoding utf8 "$out\environment.txt"

Get-Content "$PSScriptRoot\sql\holders-explain.sql" |
    docker exec -i finintel-postgres psql -U finintel -d finintel |
    Tee-Object -FilePath "$out\explain.txt"

docker cp "$PSScriptRoot\sql\holders-pgbench.sql" finintel-postgres:/tmp/holders.sql | Out-Null
docker exec finintel-postgres pgbench -U finintel -d finintel -n -f /tmp/holders.sql -c 4 -j 2 -T 30 2>&1 |
    Tee-Object -FilePath "$out\pgbench.txt"

Write-Host "Results saved in $out"
