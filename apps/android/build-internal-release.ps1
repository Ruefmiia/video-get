[CmdletBinding()]
param(
    [string]$KeystorePath = (Join-Path $env:USERPROFILE ".android\video-get-internal.jks"),
    [string]$KeyAlias = "video-get-internal",
    [string]$VersionName = "0.1.0"
)

$ErrorActionPreference = "Stop"
$ProjectRoot = $PSScriptRoot
$KeystorePath = [System.IO.Path]::GetFullPath($KeystorePath)

if (-not (Test-Path -LiteralPath $KeystorePath -PathType Leaf)) {
    throw "Keystore not found: $KeystorePath"
}

$storePasswordSecure = Read-Host "Keystore password" -AsSecureString
$keyPasswordSecure = Read-Host "Key password" -AsSecureString
$storePassword = [System.Net.NetworkCredential]::new("", $storePasswordSecure).Password
$keyPassword = [System.Net.NetworkCredential]::new("", $keyPasswordSecure).Password

$env:VIDEO_GET_KEYSTORE_PATH = $KeystorePath
$env:VIDEO_GET_KEYSTORE_PASSWORD = $storePassword
$env:VIDEO_GET_KEY_ALIAS = $KeyAlias
$env:VIDEO_GET_KEY_PASSWORD = $keyPassword

try {
    Push-Location $ProjectRoot
    try {
        & .\gradlew.bat clean testDebugUnitTest assembleRelease
        if ($LASTEXITCODE -ne 0) {
            throw "Release build failed with exit code $LASTEXITCODE"
        }
    } finally {
        Pop-Location
    }
} finally {
    Remove-Item Env:VIDEO_GET_KEYSTORE_PATH -ErrorAction SilentlyContinue
    Remove-Item Env:VIDEO_GET_KEYSTORE_PASSWORD -ErrorAction SilentlyContinue
    Remove-Item Env:VIDEO_GET_KEY_ALIAS -ErrorAction SilentlyContinue
    Remove-Item Env:VIDEO_GET_KEY_PASSWORD -ErrorAction SilentlyContinue
    $storePassword = $null
    $keyPassword = $null
}

$releaseDirectory = Join-Path $ProjectRoot "app\build\outputs\apk\release"
$apk = Get-ChildItem -LiteralPath $releaseDirectory -Filter "app-arm64-v8a-release.apk" -File |
    Select-Object -First 1
if (-not $apk) {
    throw "Signed arm64 release APK was not produced. Check the signing configuration."
}

$sdkCandidates = @(
    $env:ANDROID_HOME,
    $env:ANDROID_SDK_ROOT,
    (Join-Path $env:LOCALAPPDATA "Android\Sdk")
) | Where-Object { $_ -and (Test-Path -LiteralPath $_ -PathType Container) }
$sdkRoot = $sdkCandidates | Select-Object -First 1
if (-not $sdkRoot) {
    throw "Android SDK was not found. Set ANDROID_HOME or ANDROID_SDK_ROOT."
}

$buildTools = Get-ChildItem -LiteralPath (Join-Path $sdkRoot "build-tools") -Directory |
    Sort-Object { [version]$_.Name } -Descending |
    Select-Object -First 1
$apkSigner = Join-Path $buildTools.FullName "apksigner.bat"
if (-not (Test-Path -LiteralPath $apkSigner -PathType Leaf)) {
    throw "apksigner was not found: $apkSigner"
}

& $apkSigner verify --verbose --print-certs $apk.FullName
if ($LASTEXITCODE -ne 0) {
    throw "APK signature verification failed with exit code $LASTEXITCODE"
}

$outputDirectory = Join-Path $ProjectRoot "release-output\$VersionName"
New-Item -ItemType Directory -Path $outputDirectory -Force | Out-Null
$releaseApk = Join-Path $outputDirectory "video-get-$VersionName-arm64-v8a.apk"
Copy-Item -LiteralPath $apk.FullName -Destination $releaseApk -Force

$hash = Get-FileHash -LiteralPath $releaseApk -Algorithm SHA256
$checksumPath = Join-Path $outputDirectory "SHA256SUMS.txt"
"$($hash.Hash.ToLowerInvariant())  $([System.IO.Path]::GetFileName($releaseApk))" |
    Set-Content -LiteralPath $checksumPath -Encoding utf8
Copy-Item -LiteralPath (Join-Path $ProjectRoot "THIRD_PARTY_NOTICES.md") -Destination $outputDirectory -Force
$releaseNotes = Join-Path $ProjectRoot "release-notes\$VersionName.md"
if (Test-Path -LiteralPath $releaseNotes -PathType Leaf) {
    Copy-Item -LiteralPath $releaseNotes -Destination (Join-Path $outputDirectory "RELEASE_NOTES.md") -Force
}

Write-Output "Signed release APK: $releaseApk"
Write-Output "SHA-256: $($hash.Hash)"
