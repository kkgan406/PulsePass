param(
    [int[]]$VusList = @(1,10,50,100),
    [string[]]$Strategies = @('A','B','C'),
    [string[]]$Modes = @('distinct','same'),
    [int]$DurationSeconds = 5,
    [int]$InitialStock = 100000
)
$ErrorActionPreference = 'Stop'
$env:Path = 'F:\Docker\Docker\resources\bin;' + $env:Path
$k6 = (Resolve-Path 'target\k6-unpacked\k6\k6.exe').Path
New-Item -ItemType Directory -Force 'benchmarks\results' | Out-Null
$rows = [System.Collections.Generic.List[object]]::new()
function Invoke-BenchSql([string]$sql) {
    $output = $sql | docker compose exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_PASSWORD" mysql -h127.0.0.1 -u pulsepass -D pulsepass_bench -N -B'
    if ($LASTEXITCODE -ne 0) { throw 'MySQL benchmark SQL failed' }
    return $output
}
function Metric($summary,[string]$name,[string]$field) {
    if ($null -eq $summary.metrics.$name) { return 0 }
    $value = $summary.metrics.$name.$field
    if ($null -eq $value) { return 0 }; return $value
}
foreach ($mode in $Modes) { foreach ($vus in $VusList) { foreach ($strategy in $Strategies) {
    if ($strategy -notin @('A','B','C') -or $mode -notin @('same','distinct') -or $vus -lt 1 -or $DurationSeconds -lt 1 -or $InitialStock -lt 1) { throw 'Invalid benchmark parameters' }
    $run = [guid]::NewGuid().ToString()
    $label = "${mode}-${vus}-${strategy}-stock${InitialStock}-${DurationSeconds}s"
    $summaryFile = "benchmarks/results/$label.json"
    $logFile = "benchmarks/results/$label.log"
    Invoke-BenchSql "INSERT INTO lab_inventory(run_id,initial_stock,available) VALUES ('$run',$InitialStock,$InitialStock);" | Out-Null
    $env:RUN_ID=$run; $env:STRATEGY=$strategy; $env:USER_MODE=$mode; $env:VUS="$vus"; $env:DURATION="${DurationSeconds}s"
    Write-Output "Running $label ($run)"
    & $k6 run --quiet --summary-export $summaryFile benchmarks/k6/purchase.js *> $logFile
    if ($LASTEXITCODE -ne 0) { throw "k6 failed for $label; see $logFile" }
    $summary=Get-Content -Raw $summaryFile | ConvertFrom-Json
    $sql="SELECT initial_stock,available,(SELECT COUNT(*) FROM lab_order WHERE run_id='$run'),(SELECT COUNT(*) FROM (SELECT user_id FROM lab_order WHERE run_id='$run' GROUP BY user_id HAVING COUNT(*)>1) d) FROM lab_inventory WHERE run_id='$run';"
    $db=Invoke-BenchSql $sql
    $parts=([string]($db | Select-Object -Last 1)).Split("`t")
    if ($parts.Count -ne 4) { throw "Unexpected database result for $label" }
    $initial=[int]$parts[0]; $remaining=[int]$parts[1]; $orders=[int]$parts[2]; $duplicate=[int]$parts[3]
    $row=[pscustomobject]@{mode=$mode;vus=$vus;strategy=$strategy;seconds=$DurationSeconds;runId=$run;requests=(Metric $summary 'http_reqs' 'count');rps=(Metric $summary 'http_reqs' 'rate');success=(Metric $summary 'outcome_ok' 'count');soldOut=(Metric $summary 'outcome_out_of_stock' 'count');duplicateReject=(Metric $summary 'outcome_duplicate_order' 'count');invalid=(Metric $summary 'outcome_invalid_argument' 'count');integrity=(Metric $summary 'outcome_integrity_error' 'count');deadlock=(Metric $summary 'outcome_deadlock' 'count');lockTimeout=(Metric $summary 'outcome_lock_timeout' 'count');httpTimeout=(Metric $summary 'outcome_http_timeout' 'count');systemError=(Metric $summary 'outcome_system_error' 'count');http0=(Metric $summary 'http_status_0' 'count');http201=(Metric $summary 'http_status_201' 'count');http400=(Metric $summary 'http_status_400' 'count');http404=(Metric $summary 'http_status_404' 'count');http409=(Metric $summary 'http_status_409' 'count');http500=(Metric $summary 'http_status_500' 'count');http503=(Metric $summary 'http_status_503' 'count');p50=(Metric $summary 'http_req_duration' 'p(50)');p95=(Metric $summary 'http_req_duration' 'p(95)');p99=(Metric $summary 'http_req_duration' 'p(99)');initial=$initial;remaining=$remaining;orders=$orders;duplicates=$duplicate;oversold=[Math]::Max(0,$orders-$initial);stockMismatch=($initial-$remaining-$orders)}
    $rows.Add($row)
    $rows | Export-Csv -NoTypeInformation -Encoding utf8 benchmarks/results/summary.csv
    Write-Output "Completed $label requests=$($row.requests) success=$($row.success) orders=$orders mismatch=$($row.stockMismatch)"
}}}
