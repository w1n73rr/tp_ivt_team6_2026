# Run from any directory: powershell -File backend/scripts/check-quiz-crud.ps1
# All database data is disposable. No developer .env or existing PostgreSQL is used.
$ErrorActionPreference = 'Stop'
$quizBackendRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$quizTestContainer = 'ty3-quiz-check-' + [guid]::NewGuid().ToString('N').Substring(0, 8)
$quizTestCreated = $false
$quizTestEnvironment = @{}
foreach ($quizTestVariable in @('TEST_DATABASE_URL', 'TEST_DATABASE_USERNAME', 'TEST_DATABASE_PASSWORD', 'ML_LIVE_TEST')) {
    $quizTestEnvironment[$quizTestVariable] = [Environment]::GetEnvironmentVariable($quizTestVariable, 'Process')
}

try {
    docker run -d --name $quizTestContainer -e POSTGRES_DB=quiz_crud_test -e POSTGRES_USER=quiz_test -e POSTGRES_PASSWORD=integration-only-password -p 127.0.0.1::5432 postgres:16-alpine
    if ($LASTEXITCODE -ne 0) { throw 'Could not start isolated PostgreSQL. Start Docker Desktop and retry.' }
    $quizTestCreated = $true
    $quizTestReady = $false
    for ($quizTestAttempt = 0; $quizTestAttempt -lt 30; $quizTestAttempt++) {
        docker exec $quizTestContainer pg_isready -U quiz_test -d quiz_crud_test 2>$null | Out-Null
        if ($LASTEXITCODE -eq 0) { $quizTestReady = $true; break }
        Start-Sleep -Seconds 1
    }
    if (-not $quizTestReady) { throw 'Isolated PostgreSQL did not become ready.' }
    $quizTestBinding = docker port $quizTestContainer 5432/tcp
    if ($LASTEXITCODE -ne 0 -or $quizTestBinding -notmatch '^127\.0\.0\.1:(\d+)$') {
        throw 'Could not discover the isolated PostgreSQL port.'
    }
    $env:TEST_DATABASE_URL = 'jdbc:postgresql://127.0.0.1:' + $Matches[1] + '/quiz_crud_test'
    $env:TEST_DATABASE_USERNAME = 'quiz_test'
    $env:TEST_DATABASE_PASSWORD = 'integration-only-password'
    $env:ML_LIVE_TEST = 'false'
    Push-Location -LiteralPath $quizBackendRoot
    try {
        # A new database must really be exercised even when the previous test task was up-to-date.
        & .\gradlew.bat build --rerun-tasks
        if ($LASTEXITCODE -ne 0) { throw 'Backend build or tests failed. See backend/build/reports/tests/test/index.html.' }
    } finally {
        Pop-Location
    }
} finally {
    foreach ($quizTestVariable in $quizTestEnvironment.Keys) {
        [Environment]::SetEnvironmentVariable($quizTestVariable, $quizTestEnvironment[$quizTestVariable], 'Process')
    }
    if ($quizTestCreated) {
        docker rm -f -v $quizTestContainer
        if ($LASTEXITCODE -ne 0) { Write-Warning "Could not remove disposable test container: $quizTestContainer" }
    }
}
