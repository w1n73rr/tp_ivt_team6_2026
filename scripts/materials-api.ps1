# Run from any directory. No access to the developer's existing DB or .env.
$ErrorActionPreference = 'Stop'
$repo = Split-Path $PSScriptRoot -Parent
Set-Location -LiteralPath $repo
$docker = (Get-Command docker -ErrorAction SilentlyContinue).Source
if (-not $docker) {
    $docker = Join-Path $env:LOCALAPPDATA 'Programs\DockerDesktop\resources\bin\docker.exe'
}
if (-not (Test-Path -LiteralPath $docker)) { throw 'Start Docker Desktop and make docker available.' }
$project = 'tpivt-api41-' + [guid]::NewGuid().ToString('N').Substring(0, 10)
$revision = (& git rev-parse HEAD)
if ($LASTEXITCODE -ne 0) { throw 'Cannot determine tested revision.' }
$env:API_TEST_VERSION = $revision
$compose = @('compose', '-p', $project, '--env-file', '.env.ci.example',
    '-f', 'docker-compose.yml', '-f', 'compose.ci.yml', '-f', 'compose.api-test.yml')
$result = 1
$cleanupResult = 0
$started = Get-Date -Format o
try {
    & $docker @compose config --quiet
    if ($LASTEXITCODE -ne 0) { throw 'Invalid test Compose configuration.' }
    & $docker @compose up -d --build --wait --wait-timeout 300 exam-ai-backend
    if ($LASTEXITCODE -ne 0) { throw 'Disposable stack failed to start. HTTP checks were not executed.' }
    & $docker @compose run --build --rm api-tests
    $result = $LASTEXITCODE
} catch {
    Write-Host $_.Exception.Message
    $result = 1
} finally {
    # Unique project + tmpfs from compose.ci.yml. Never use the developer's Compose project.
    & $docker @compose down --remove-orphans
    $cleanupResult = $LASTEXITCODE
    $report = Join-Path $repo 'api-tests\build\reports'
    New-Item -ItemType Directory -Force -Path $report | Out-Null
    $branch = (& git branch --show-current)
    $changes = (& git status --short) -join "`n"
    $testHash = (Get-FileHash (Join-Path $repo 'api-tests\src\test\java\tech\genna\TY3\tests\api\MaterialsApiTest.java')).Hash
    @"
# Materials API run

Started: $started
Finished: $(Get-Date -Format o)
Revision: $revision
Branch: $branch
Test source SHA256: $testHash
Environment: Windows host, Docker Desktop, isolated Linux Compose project $project
Services: real Backend, PostgreSQL 16, SeaweedFS 4.48; no UI, no ML, no mocks
Command: powershell -ExecutionPolicy Bypass -File scripts/materials-api.ps1
Run exit code: $result
Cleanup exit code: $cleanupResult

Only this run's timestamps are authoritative. If startup failed, older HTML/audit files are not evidence of execution.
HTTP audit: materials-http.md
Assertions and expected/actual values: tests/test/index.html; ../test-results/test/*.xml

Working-tree changes (tests may be uncommitted):
$changes

Acceptance: PR, peer review, CI link, independent reproduction and defect links must be filled in by the team.
No production logic was changed by this task. New production unit tests are not applicable.
"@ | Set-Content -LiteralPath (Join-Path $report 'run-summary.md') -Encoding UTF8
}
if ($cleanupResult -ne 0) { Write-Host "Test stack cleanup failed: $project"; exit 1 }
exit $result
