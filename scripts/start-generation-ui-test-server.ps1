param(
    [string]$EnvFile = '.env.generation.local',
    [string]$DatabaseContainer = 'generation-acceptance-exam-ai-db-1',
    [string]$GradleInitScript = '',
    [switch]$Offline
)
$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
Set-Location $root
if (!(Test-Path -LiteralPath $EnvFile)) { throw 'Create a private server env file from .env.example first' }
$previous = @{}
function Set-TestEnv([string]$name, [string]$value) {
    if (!$previous.ContainsKey($name)) { $previous[$name] = [Environment]::GetEnvironmentVariable($name, 'Process') }
    [Environment]::SetEnvironmentVariable($name, $value, 'Process')
}
try {
    foreach ($line in Get-Content -LiteralPath $EnvFile) {
        if ($line -match '^([^#=]+)=(.*)$' -and $Matches[1] -ne 'OPENROUTER_API_KEY') { Set-TestEnv $Matches[1] $Matches[2] }
    }
    Set-TestEnv 'OPENROUTER_API_KEY' ''
    $exists = docker exec $DatabaseContainer psql -U $env:POSTGRES_USER -d postgres -At -c "SELECT 1 FROM pg_database WHERE datname='generation_ui_controlled'"
    if ($LASTEXITCODE -ne 0) { throw 'PostgreSQL container is unavailable' }
    if ($exists -ne '1') { docker exec $DatabaseContainer createdb -U $env:POSTGRES_USER generation_ui_controlled }
    Set-TestEnv 'SPRING_DATASOURCE_URL' "jdbc:postgresql://localhost:$($env:POSTGRES_PORT)/generation_ui_controlled"
    Set-TestEnv 'SPRING_DATASOURCE_USERNAME' $env:POSTGRES_USER
    Set-TestEnv 'SPRING_DATASOURCE_PASSWORD' $env:POSTGRES_PASSWORD
    Set-TestEnv 'JWT_EXPIRATION' '86400000'
    Set-TestEnv 'S3_ENDPOINT' "http://localhost:$($env:S3_PORT)"
    Set-TestEnv 'S3_BUCKET_NAME' 'generation-ui-controlled'
    Set-TestEnv 'SERVER_PORT' '18083'
    Set-TestEnv 'GENERATION_JOB_TIMEOUT_SECONDS' '12'
    Set-TestEnv 'APP_CORS_ALLOWED_ORIGINS' 'http://localhost:13002'
    $arguments = @('-p', "$root/backend", 'generationUiServer', '--no-daemon', '--no-watch-fs')
    if ($Offline) { $arguments += '--offline' }
    if ($GradleInitScript) { $arguments += @('-I', $GradleInitScript) }
    & "$root/backend/gradlew.bat" @arguments
    if ($LASTEXITCODE -ne 0) { throw 'Test backend stopped with an error' }
} finally {
    foreach ($name in $previous.Keys) { [Environment]::SetEnvironmentVariable($name, $previous[$name], 'Process') }
}
