# Reads resource metrics from Prometheus for a time window (unix seconds)
param([long]$From, [long]$To, [string]$Prometheus = "http://localhost:9090")
$ErrorActionPreference = "Continue"

$range = [Math]::Max(10, $To - $From)
$queries = [ordered]@{
    "backend process CPU (max, 0-1)" = "max_over_time(process_cpu_usage{job=`"backend`"}[${range}s])"
    "machine CPU (max, 0-1)"         = "max_over_time(system_cpu_usage{job=`"backend`"}[${range}s])"
    "JVM heap used MB (max)"         = "max_over_time(sum(jvm_memory_used_bytes{job=`"backend`",area=`"heap`"})[${range}s:5s]) / 1048576"
    "DB pool active (max)"           = "max_over_time(max(hikaricp_connections_active{job=`"backend`"})[${range}s:5s])"
    "DB pool waiting threads (max)"  = "max_over_time(max(hikaricp_connections_pending{job=`"backend`"})[${range}s:5s])"
    "cache hit ratio"                = "sum(increase(portfolio_cache_total{result=`"hit`"}[${range}s])) / sum(increase(portfolio_cache_total{result=~`"hit|miss`"}[${range}s]))"
    "Kafka consumer lag (max)"       = "max_over_time(max(kafka_consumer_fetch_manager_records_lag_max{job=`"backend`"})[${range}s:5s])"
    "outbox pending (max)"           = "max_over_time(outbox_pending{job=`"backend`"}[${range}s])"
}

"Window: $range s ending $([DateTimeOffset]::FromUnixTimeSeconds($To).ToString('u'))"
foreach ($name in $queries.Keys) {
    $q = [uri]::EscapeDataString($queries[$name])
    try {
        $r = Invoke-RestMethod "$Prometheus/api/v1/query?query=$q&time=$To"
        $v = if ($r.data.result.Count -gt 0) { [math]::Round([double]$r.data.result[0].value[1], 3) } else { "n/a" }
    } catch {
        $v = "error: $($_.Exception.Message)"
    }
    "{0,-32} {1}" -f $name, $v
}
