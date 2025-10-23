# Runs one load test and stores everything needed to report and reproduce it in benchmark-results\<run id>\
#   .\scripts\benchmark\run.ps1 -Test portfolio -Label cache-on
param(
    [Parameter(Mandatory = $true)][ValidateSet("portfolio", "orders", "price-update", "rag")][string]$Test,
    [Parameter(Mandatory = $true)][string]$Label,
    [int]$Vus = 0,
    [int]$WarmupSeconds = -1,
    [int]$MainSeconds = 60,
    [int]$Users = 0,
    # orders only: how long to wait for the outbox backlog to drain
    [int]$DrainMinutes = 15
)
$ErrorActionPreference = "Stop"

# A terminal opened before installing k6 does not see it on PATH yet; look in the usual install folders too
$k6 = (Get-Command k6 -ErrorAction SilentlyContinue).Source
if (-not $k6) {
    $k6 = @("$env:ProgramFiles\k6\k6.exe", "$env:LOCALAPPDATA\Microsoft\WinGet\Links\k6.exe") |
        Where-Object { Test-Path $_ } | Select-Object -First 1
}
if (-not $k6) { throw "k6 not found. Install it (winget install k6) and open a new terminal." }

$root = (Resolve-Path "$PSScriptRoot\..\..").Path
$runId = "$Test-$Label-$(Get-Date -Format 'yyyyMMdd-HHmmss')"
$out = Join-Path $root "benchmark-results\$runId"
New-Item -ItemType Directory -Force -Path $out | Out-Null

function Invoke-Psql([string]$sql) {
    (docker exec finintel-postgres psql -U finintel -d finintel -tAc $sql | Out-String).Trim()
}

# Defaults per test: VUs, warm-up seconds, user pool size
$defaults = @{ "portfolio" = @(20, 15, 1000); "orders" = @(20, 0, 20000); "price-update" = @(1, 5, 0); "rag" = @(5, 10, 0) }
if ($Vus -le 0) { $Vus = $defaults[$Test][0] }
if ($WarmupSeconds -lt 0) { $WarmupSeconds = $defaults[$Test][1] }
if ($Users -le 0) { $Users = $defaults[$Test][2] }

& "$PSScriptRoot\env.ps1" | Out-File -Encoding utf8 "$out\environment.txt"
"test=$Test label=$Label vus=$Vus warmup=${WarmupSeconds}s main=${MainSeconds}s users=$Users" |
    Out-File -Encoding utf8 "$out\parameters.txt"

$k6Args = @("run", "-e", "VUS=$Vus", "-e", "WARMUP_SECONDS=$WarmupSeconds", "-e", "MAIN_SECONDS=$MainSeconds",
    "-e", "USERS=$Users", "-e", "RUN_ID=$runId", "-e", "SUMMARY_FILE=$out\k6-summary.json")
if ($Test -ne "rag") {
    $userMin = Invoke-Psql "SELECT min(id) FROM users WHERE email LIKE 'bench-%@bench.test'"
    if (-not $userMin) { throw "No benchmark users found. Seed them first (see docs/performance.md)." }
    $k6Args += @("-e", "USER_MIN=$userMin")
}
$k6Args += "$PSScriptRoot\k6\$Test.js"

$start = [DateTimeOffset]::UtcNow.ToUnixTimeSeconds()
& $k6 @k6Args | Tee-Object -FilePath "$out\k6-output.txt"
$end = [DateTimeOffset]::UtcNow.ToUnixTimeSeconds()

# Resource use during the measured phase only
& "$PSScriptRoot\prom-snapshot.ps1" -From ($start + $WarmupSeconds) -To $end |
    Tee-Object -FilePath "$out\prometheus.txt"

if ($Test -eq "orders") {
    Write-Host "Waiting for the order pipeline to drain..."
    $deadline = (Get-Date).AddMinutes($DrainMinutes)
    do {
        Start-Sleep -Seconds 2
        $pending = [int](Invoke-Psql "SELECT count(*) FROM outbox_events WHERE published_at IS NULL")
    } while ($pending -gt 0 -and (Get-Date) -lt $deadline)
    if ($pending -gt 0) { Write-Host "Outbox still has $pending events after $DrainMinutes minutes" }
    Start-Sleep -Seconds 5 # the last events are still being consumed
    Get-Content "$PSScriptRoot\sql\order-latency.sql" |
        docker exec -i finintel-postgres psql -U finintel -d finintel -v "prefix=bench-$runId-%" |
        Tee-Object -FilePath "$out\order-pipeline.txt"
}

Write-Host "Results saved in $out"
