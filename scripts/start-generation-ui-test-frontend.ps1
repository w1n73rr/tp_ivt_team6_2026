param([string]$FrontendImage = 'generation-acceptance-exam-ai-frontend:latest')
$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
docker build --build-arg "FRONTEND_IMAGE=$FrontendImage" -t generation-ui-controlled-frontend "$root/ui-tests/environment"
if ($LASTEXITCODE -ne 0) { throw 'Build the current application frontend image first' }
$existing = docker ps -a --filter 'name=^/generation-ui-controlled-frontend$' --format '{{.Names}}'
if ($existing -eq 'generation-ui-controlled-frontend') { docker rm -f generation-ui-controlled-frontend | Out-Null }
docker run -d --name generation-ui-controlled-frontend -p 127.0.0.1:13002:80 generation-ui-controlled-frontend
if ($LASTEXITCODE -ne 0) { throw 'Controlled frontend failed to start' }
