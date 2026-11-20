# Windows one-shot setup for the BalaSpeak Android project.
#   .\scripts\windows-setup.bat                 -> project code + sherpa-onnx AAR (~48 MB)
#   .\scripts\windows-setup.bat -WithVoices     -> also pack every neural voice into the APK (~400 MB)
[CmdletBinding()]
param(
    [switch]$WithVoices,
    [string]$Branch = "arena/05c3abe6-apk-revarcing",
    [string]$SherpaVersion = "1.13.8"
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
Set-Location $root

try { [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12 } catch {}

function Step($text) { Write-Host ""; Write-Host "==> $text" -ForegroundColor Cyan }

# 1. The default branch of this repository is only a stub; the real sources live on
#    the work branch. Fetch it when settings.gradle.kts is missing.
if (-not (Test-Path (Join-Path $root "settings.gradle.kts"))) {
    Step "Fetching branch $Branch (the main branch only holds a README)"
    git fetch origin $Branch
    if ($LASTEXITCODE -ne 0) { throw "git fetch failed - check your network or run: git remote -v" }
    git checkout -B v34 FETCH_HEAD
    if ($LASTEXITCODE -ne 0) { throw "git checkout failed" }
}
else {
    Write-Host "sources present (settings.gradle.kts found)" -ForegroundColor DarkGray
}

# 2. sherpa-onnx AAR - not on Maven Central, the Gradle build refuses to run without it.
$libs = Join-Path $root "app\libs"
$aars = Join-Path $libs "sherpa-onnx-$SherpaVersion.aar"
if (Test-Path $aars) {
    Write-Host "sherpa-onnx AAR already present" -ForegroundColor DarkGray
}
else {
    Step "Downloading sherpa-onnx $SherpaVersion AAR (about 48 MB)"
    New-Item -ItemType Directory -Force -Path $libs | Out-Null
    $url = "https://github.com/k2-fsa/sherpa-onnx/releases/download/v$SherpaVersion/sherpa-onnx-$SherpaVersion.aar"
    Invoke-WebRequest -Uri $url -OutFile $aars -UseBasicParsing
    Write-Host ("saved {0:N0} bytes" -f (Get-Item $aars).Length) -ForegroundColor Green
}

# 3. Optional: unpack every voice model into jniLibs so the APK ships them.
if ($WithVoices) {
    Step "Packing neural voices into the APK (needs Git Bash, about 400 MB download)"
    $candidates = @(
        "C:\Program Files\Git\bin\bash.exe",
        "C:\Program Files (x86)\Git\bin\bash.exe",
        "$env:LOCALAPPDATA\Programs\Git\bin\bash.exe"
    )
    $bash = $candidates | Where-Object { Test-Path $_ } | Select-Object -First 1
    if (-not $bash) {
        $cmd = Get-Command bash.exe -ErrorAction SilentlyContinue
        if ($cmd) { $bash = $cmd.Source }
    }
    if (-not $bash) {
        throw "Git Bash not found. Install 'Git for Windows' (it ships bash) or run scripts/prepare_builtin_assets.sh inside WSL."
    }
    Write-Host "using $bash" -ForegroundColor DarkGray
    & $bash "scripts/prepare_builtin_assets.sh"
    if ($LASTEXITCODE -ne 0) { throw "prepare_builtin_assets.sh failed" }
}

Step "Done. Next: open this folder in Android Studio and press Run."
Write-Host "  build debug APK   : gradlew.bat :app:assembleDebug" -ForegroundColor Yellow
Write-Host "  build release APK : gradlew.bat :app:assembleRelease -PnoMinify" -ForegroundColor Yellow
Write-Host "  install to phone  : adb install -r app\build\outputs\apk\debug\app-debug.apk" -ForegroundColor Yellow
