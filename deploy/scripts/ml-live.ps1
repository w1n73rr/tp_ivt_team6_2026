param(
    [ValidateSet('single_choice','multiple_choice','true_false','remaining','all','choice_pair')][string]$Type = 'single_choice',
    [ValidateSet(5,10)][int]$Count = 5,
    [ValidateSet('easy','medium','hard')][string]$Difficulty = 'medium',
    [switch]$Reasoning,
    [ValidatePattern('^[A-Za-z0-9._/-]+:free$')][string]$Model = 'nvidia/nemotron-3.5-super-vl:free'
)
$ErrorActionPreference = 'Stop'
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "../..")).Path
$backendPath = Join-Path $repoRoot 'backend'
$resultPath = Join-Path $backendPath 'build/ml-live'
New-Item -ItemType Directory -Force -Path $resultPath | Out-Null
$statusPath = Join-Path $resultPath 'status.json'
$logPath = Join-Path $resultPath 'live-run.log'
$savedKey = $env:OPENROUTER_API_KEY
$savedLive = $env:ML_LIVE_TEST
$savedType = $env:ML_LIVE_TYPE
$savedCount = $env:ML_LIVE_COUNT
$savedDifficulty = $env:ML_LIVE_DIFFICULTY
$savedReasoning = $env:OPENROUTER_REASONING_ENABLED
$savedModel = $env:OPENROUTER_MODEL
$savedJava = $env:JAVA_HOME
$savedOptions = $env:JAVA_TOOL_OPTIONS
$savedGradle = $env:GRADLE_USER_HOME
$secret = $null
$pointer = [IntPtr]::Zero
try {
    $localEnvPath = Join-Path $repoRoot '.env.ml.local'
    Write-Host 'OpenRouter FREE model. Credential values are never printed.'
    if (-not [string]::IsNullOrWhiteSpace($savedKey)) {
        $env:OPENROUTER_API_KEY = $savedKey
    } elseif (Test-Path -LiteralPath $localEnvPath) {
        $line = Get-Content -LiteralPath $localEnvPath | Where-Object { $_ -match '^OPENROUTER_API_KEY=' } | Select-Object -First 1
        if (-not $line) { throw 'Local key entry missing.' }
        $env:OPENROUTER_API_KEY = $line.Substring('OPENROUTER_API_KEY='.Length).Trim()
        $line = $null
    } else {
    $secret = Read-Host 'Paste OpenRouter API key once to save in ignored .env.ml.local' -AsSecureString
    $pointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secret)
    $env:OPENROUTER_API_KEY = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer)
    [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer)
    $pointer = [IntPtr]::Zero
    }
    if (-not $env:OPENROUTER_API_KEY.StartsWith('sk-or-')) { throw 'Invalid key format; nothing sent.' }
    if ([string]::IsNullOrWhiteSpace($savedKey) -and -not (Test-Path -LiteralPath $localEnvPath)) {
        if ($env:OPENROUTER_API_KEY -notmatch '^sk-or-[A-Za-z0-9_-]+$') { throw 'Invalid key format.' }
        [System.IO.File]::WriteAllText($localEnvPath, "OPENROUTER_API_KEY=$($env:OPENROUTER_API_KEY)`n")
        Write-Host 'Key saved locally. Next runs will reuse it.'
    }
    $env:ML_LIVE_TEST = 'true'
    $env:ML_LIVE_TYPE = $Type
    $env:ML_LIVE_COUNT = [string]$Count
    $env:ML_LIVE_DIFFICULTY = $Difficulty
    $env:OPENROUTER_REASONING_ENABLED = $Reasoning.IsPresent.ToString().ToLowerInvariant()
    $env:OPENROUTER_MODEL = $Model
    $localJdk = Join-Path $env:USERPROFILE '.jdks/openjdk-25'
    if (Test-Path $localJdk) { $env:JAVA_HOME = $localJdk }
    # Workaround for Windows JDK Unix-domain sockets under a Cyrillic TEMP path.
    $env:JAVA_TOOL_OPTIONS = '-Djdk.net.unixdomain.tmpdir=C:/Windows/Temp -Dfile.encoding=COMPAT'
    Add-Type -Namespace MlWindowsPath -Name Native -MemberDefinition '[System.Runtime.InteropServices.DllImport("kernel32.dll", CharSet=System.Runtime.InteropServices.CharSet.Unicode)] public static extern uint GetShortPathName(string path, System.Text.StringBuilder buffer, uint size);'
    $cachePath = Join-Path $env:USERPROFILE '.gradle'
    $pathBuffer = New-Object System.Text.StringBuilder 1024
    if ([MlWindowsPath.Native]::GetShortPathName($cachePath, $pathBuffer, 1024) -gt 0) {
        $env:GRADLE_USER_HOME = $pathBuffer.ToString()
    }
    @{state='running'; type=$Type; count=$Count; difficulty=$Difficulty; model=$Model; reasoning=$Reasoning.IsPresent} | ConvertTo-Json | Set-Content $statusPath
    Push-Location $backendPath
    try {
        # Wait for the native process explicitly; stderr warnings are not PowerShell errors.
        $process = Start-Process -FilePath $env:ComSpec -ArgumentList @('/d', '/c',
            'gradlew.bat --no-daemon -Dorg.gradle.jvmargs=-Dfile.encoding=COMPAT test --tests tech.genna.TY3.ml.OpenRouterLiveTest --rerun-tasks --console=plain') `
            -WorkingDirectory $backendPath -WindowStyle Hidden -PassThru -Wait `
            -RedirectStandardOutput $logPath -RedirectStandardError (Join-Path $resultPath 'live-run.stderr.log')
        $resultCode = $process.ExitCode
    } finally { $ErrorActionPreference = 'Stop'; Pop-Location }
    @{state= $(if ($resultCode -eq 0) {'passed'} else {'failed'}); exitCode=$resultCode;
        type=$Type; count=$Count; difficulty=$Difficulty; model=$Model; reasoning=$Reasoning.IsPresent} | ConvertTo-Json | Set-Content $statusPath
    Write-Host "Test finished. Exit code: $resultCode. Output: $resultPath"
    Write-Host 'A passed test includes structural and model-assisted review; human content review is still needed.'
} catch {
    @{state='failed'; reason='Setup or test failed. Local credential file, if present, is retained.'} | ConvertTo-Json | Set-Content $statusPath
    Write-Host 'Test could not complete. Check setup and the safe local test log.'
} finally {
    if ($pointer -ne [IntPtr]::Zero) { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer) }
    if ($secret) { $secret.Dispose() }
    $env:OPENROUTER_API_KEY = $savedKey
    $env:ML_LIVE_TEST = $savedLive
    $env:ML_LIVE_TYPE = $savedType
    $env:ML_LIVE_COUNT = $savedCount
    $env:ML_LIVE_DIFFICULTY = $savedDifficulty
    $env:OPENROUTER_REASONING_ENABLED = $savedReasoning
    $env:OPENROUTER_MODEL = $savedModel
    $env:JAVA_HOME = $savedJava
    $env:JAVA_TOOL_OPTIONS = $savedOptions
    $env:GRADLE_USER_HOME = $savedGradle
}
Read-Host 'Press Enter to close' | Out-Null
