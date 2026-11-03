param(
    [ValidateSet('single_choice','multiple_choice','true_false','remaining','all')][string]$Type = 'single_choice',
    [ValidateSet(5,10)][int]$Count = 5,
    [ValidateSet('easy','medium','hard')][string]$Difficulty = 'medium',
    [switch]$Reasoning,
    [ValidatePattern('^[A-Za-z0-9._/-]+:free$')][string]$Model = 'nvidia/nemotron-3.5-lightning:free'
)
$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path $PSScriptRoot -Parent
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
    Write-Host 'OpenRouter FREE model. Key input is hidden. Do not send the key in chat.'
    $secret = Read-Host 'Paste NEW OpenRouter API key and press Enter' -AsSecureString
    $pointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secret)
    $env:OPENROUTER_API_KEY = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer)
    [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer)
    $pointer = [IntPtr]::Zero
    if (-not $env:OPENROUTER_API_KEY.StartsWith('sk-or-')) { throw 'Invalid key format; nothing sent.' }
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
        & ./gradlew.bat --no-daemon '-Dorg.gradle.jvmargs=-Dfile.encoding=COMPAT' test --tests 'tech.genna.TY3.ml.OpenRouterLiveTest' --rerun-tasks --console=plain *> $logPath
        $resultCode = $LASTEXITCODE
    } finally { Pop-Location }
    @{state= $(if ($resultCode -eq 0) {'passed'} else {'failed'}); exitCode=$resultCode;
        type=$Type; count=$Count; difficulty=$Difficulty; model=$Model; reasoning=$Reasoning.IsPresent} | ConvertTo-Json | Set-Content $statusPath
    Write-Host "Test finished. Exit code: $resultCode. Output: $resultPath"
    Write-Host 'A passed test includes structural and model-assisted review; human content review is still needed.'
} catch {
    @{state='failed'; reason='Setup or test failed. No key is saved.'} | ConvertTo-Json | Set-Content $statusPath
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
