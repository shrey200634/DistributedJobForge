$batchSize = 500
$totalJobs = 10000
$batches = $totalJobs / $batchSize

Write-Host "Starting Load Test: Submitting $totalJobs jobs in $batches batches of $batchSize..."

for ($i = 0; $i -lt $batches; $i++) {
    $jobs = @()
    for ($j = 0; $j -lt $batchSize; $j++) {
        $idx = ($i * $batchSize) + $j
        
        # Mix in some failures and retries to light up the DLQ and Retry dashboards
        $command = "echo load test job $idx"
        $maxRetries = 0
        if ($idx % 20 -eq 0) {
            $command = "exit 1" # Fails on purpose
            $maxRetries = 2     # Will retry twice, then DLQ
        }

        $jobs += @{
            clientRefId = "load-job-$idx"
            idempotencyKey = "load-test-v2-$idx"
            type = "SHELL"
            priority = 5
            timeoutS = 10
            MaxRetries = $maxRetries
            payload = @{ command = $command }
        }
    }

    $body = @{ jobs = $jobs } | ConvertTo-Json -Depth 6

    Write-Host "Submitting batch $($i + 1)/$batches..."
    $response = Invoke-RestMethod -Uri "http://localhost:8081/api/v1/jobs/batch" -Method Post -ContentType "application/json" -Body $body
    
    Start-Sleep -Milliseconds 500
}

Write-Host "Load Test Submitted! Check Grafana now to watch the workers process them."
