$ErrorActionPreference = "Stop"

Write-Host "============================================="
Write-Host "  DistributedJobForge End-to-End Test Suite  "
Write-Host "============================================="

# ---------------------------------------------------------
# Test 1: DAG Dependency Execution
# ---------------------------------------------------------
Write-Host "`n[Test 1] Submitting DAG Batch (Job C depends on Job A and Job B)..." -ForegroundColor Cyan

$jobAId = [guid]::NewGuid().ToString()
$jobBId = [guid]::NewGuid().ToString()

$batchPayload = @{
    jobs = @(
        @{
            clientRefId = "job-a"
            idempotencyKey = $jobAId
            type = "SHELL"
            priority = 5
            maxRetries = 1
            payload = @{ command = "sleep 2 && echo 'Job A Finished'" }
            dependsOn = @()
        },
        @{
            clientRefId = "job-b"
            idempotencyKey = $jobBId
            type = "SHELL"
            priority = 5
            maxRetries = 1
            payload = @{ command = "sleep 3 && echo 'Job B Finished'" }
            dependsOn = @()
        },
        @{
            clientRefId = "job-c"
            idempotencyKey = [guid]::NewGuid().ToString()
            type = "SHELL"
            priority = 10
            maxRetries = 1
            payload = @{ command = "echo 'Job C Executing after A and B!'" }
            dependsOn = @( "job-a", "job-b" )
        }
    )
} | ConvertTo-Json -Depth 10

try {
    $batchResponse = Invoke-RestMethod -Uri "http://localhost:8081/api/v1/jobs/batch" `
                                     -Method Post `
                                     -Body $batchPayload `
                                     -ContentType "application/json"
} catch {
    Write-Host "FATAL: Failed to submit batch: $_" -ForegroundColor Red
    exit 1
}

$jobC_UUID = $batchResponse[2].jobId
Write-Host "DAG Submitted Successfully. Target Child Job C ID: $jobC_UUID" -ForegroundColor Green

Write-Host "Polling Job C for completion (should wait for A and B)..."
$maxPolls = 20
$pollCount = 0
$status = "PENDING"

while ($pollCount -lt $maxPolls -and $status -ne "SUCCEEDED") {
    Start-Sleep -Seconds 1
    $pollCount++
    try {
        $check = Invoke-RestMethod -Uri "http://localhost:8080/api/v1/jobs/$jobC_UUID" -Method Get
        $status = $check.status
        Write-Host "  Poll $pollCount - Job C Status: $status"
    } catch {
        Write-Host "  Warning: Failed to poll status: $_" -ForegroundColor Yellow
    }
}

if ($status -eq "SUCCEEDED") {
    Write-Host "✅ Test 1 Passed! DAG resolved and executed perfectly." -ForegroundColor Green
} else {
    Write-Host "❌ Test 1 Failed! Job C never reached SUCCEEDED. Final status: $status" -ForegroundColor Red
    exit 1
}


# ---------------------------------------------------------
# Test 2: Error Handling, Retries & Dead Letter Queue (DLQ)
# ---------------------------------------------------------
Write-Host "`n[Test 2] Submitting Failing Job to test DLQ Pipeline..." -ForegroundColor Cyan

$failPayload = @{
    idempotencyKey = [guid]::NewGuid().ToString()
    type = "SHELL"
    priority = 5
    maxRetries = 2
    timeoutS = 5
    payload = @{ command = "invalid_command_that_does_not_exist" }
} | ConvertTo-Json

try {
    $failResponse = Invoke-RestMethod -Uri "http://localhost:8080/api/v1/jobs" `
                                     -Method Post `
                                     -Body $failPayload `
                                     -ContentType "application/json"
} catch {
    Write-Host "FATAL: Failed to submit failing job: $_" -ForegroundColor Red
    exit 1
}

$jobFail_UUID = $failResponse.jobId
Write-Host "Failing Job Submitted. ID: $jobFail_UUID" -ForegroundColor Green

Write-Host "Polling Failing Job (Expecting it to hit DLQ after 2 retries)..."
$maxPolls = 15
$pollCount = 0
$status = "PENDING"

while ($pollCount -lt $maxPolls -and $status -ne "DLQ") {
    Start-Sleep -Seconds 2
    $pollCount++
    try {
        $check = Invoke-RestMethod -Uri "http://localhost:8080/api/v1/jobs/$jobFail_UUID" -Method Get
        $status = $check.status
        $attempt = $check.retryCount
        Write-Host "  Poll $pollCount - Status: $status (Attempts: $attempt)"
    } catch {
        Write-Host "  Warning: Failed to poll status: $_" -ForegroundColor Yellow
    }
}

if ($status -eq "DLQ") {
    Write-Host "✅ Test 2 Passed! Job safely caught in Dead Letter Queue after retries." -ForegroundColor Green
} else {
    Write-Host "❌ Test 2 Failed! Job never reached DLQ. Final status: $status" -ForegroundColor Red
    exit 1
}

Write-Host "`n============================================="
Write-Host "  ALL TESTS PASSED SUCCESSFULLY! 🚀🚀🚀  "
Write-Host "============================================="
