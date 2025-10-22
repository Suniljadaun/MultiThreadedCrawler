# Sends a little synthetic traffic so the Grafana dashboard has something to show.
# Needs the backend on :8080 and (optionally) the ai-service on :8000, with user 1 existing.
#   .\scripts\dev\generate-traffic.ps1 -Rounds 20

param([int]$Rounds = 20)

$backend = "http://localhost:8080/api/v1"
$ai = "http://localhost:8000/api/v1"
$symbols = @("ACME", "GLOBEX", "INITECH", "UMBRELLA", "STARK")
$questions = @(
    "What were the major revenue drivers for ACME in 2025?",
    "How many active merchants did Globex have at the end of the quarter?",
    "When does support for Initech's on-premise product end?",
    "What is the weather forecast in Mumbai tomorrow?"
)

for ($i = 1; $i -le $Rounds; $i++) {
    $symbol = $symbols[$i % $symbols.Count]
    # Limit 1000 fills at the seeded prices; every 5th order uses an unknown symbol and is rejected
    if ($i % 5 -eq 0) { $symbol = "NOPE" }
    $order = @{ userId = 1; symbol = $symbol; side = "BUY"; quantity = 1; price = 1000 } | ConvertTo-Json
    $key = [guid]::NewGuid().ToString()
    try {
        Invoke-RestMethod -Method Post -Uri "$backend/orders" -ContentType "application/json" `
            -Headers @{ "Idempotency-Key" = $key; "X-Request-ID" = "traffic-$i" } -Body $order | Out-Null
    } catch { Write-Host "order $i failed: $($_.Exception.Message)" }

    # Two reads: the second one should hit the cache
    Invoke-RestMethod -Uri "$backend/portfolio/1" | Out-Null
    Invoke-RestMethod -Uri "$backend/portfolio/1" | Out-Null

    # One request that fails validation, so the 4xx line is not empty
    try { Invoke-RestMethod -Uri "$backend/orders/abc" | Out-Null } catch { }

    try {
        $q = @{ question = $questions[$i % $questions.Count] } | ConvertTo-Json
        Invoke-RestMethod -Method Post -Uri "$ai/research/query" -ContentType "application/json" -Body $q | Out-Null
    } catch { Write-Host "ai-service not reachable, skipping research query" }

    Start-Sleep -Milliseconds 500
}
Write-Host "Sent $Rounds rounds. Open http://localhost:3000 (dashboard: FinIntel overview)."
