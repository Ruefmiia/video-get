$ErrorActionPreference = "Stop"
$ProjectRoot = (Resolve-Path (Join-Path $PSScriptRoot "../..")).Path
$OutputRoot = Join-Path $PSScriptRoot "dist"
$BasePython = python -c "import sys; print(sys.base_prefix)"
$env:TCL_LIBRARY = Join-Path $BasePython "tcl\tcl8.6"
$env:TK_LIBRARY = Join-Path $BasePython "tcl\tk8.6"

python -m PyInstaller `
    --noconfirm `
    --clean `
    --distpath $OutputRoot `
    --workpath (Join-Path $PSScriptRoot "build") `
    (Join-Path $PSScriptRoot "video-get-companion.spec")
if ($LASTEXITCODE -ne 0) {
    throw "PyInstaller 构建失败，退出码：$LASTEXITCODE"
}

$WarningFile = Join-Path $PSScriptRoot "build\video-get-companion\warn-video-get-companion.txt"
if (
    (Test-Path -LiteralPath $WarningFile) -and
    (Select-String -LiteralPath $WarningFile -Pattern "missing module named tkinter" -Quiet)
) {
    throw "PyInstaller 未能收集 tkinter。请在可访问 Python Tcl/Tk 目录的正常用户权限下重新构建。"
}

Copy-Item `
    -LiteralPath (Join-Path $OutputRoot "VideoGet.exe") `
    -Destination (Join-Path $OutputRoot "VideoGetNativeHost.exe") `
    -Force

$FfmpegCommand = Get-Command ffmpeg.exe -ErrorAction Stop
$FfprobeCommand = Get-Command ffprobe.exe -ErrorAction Stop
$FfmpegSource = (Resolve-Path -LiteralPath $FfmpegCommand.Source).Path
$FfprobeSource = (Resolve-Path -LiteralPath $FfprobeCommand.Source).Path
$FfmpegRoot = Split-Path -Parent (Split-Path -Parent $FfmpegSource)
$FfmpegLicense = Join-Path $FfmpegRoot "LICENSE"
$FfmpegReadme = Join-Path $FfmpegRoot "README.txt"

if (-not (Test-Path -LiteralPath $FfmpegLicense -PathType Leaf)) {
    throw "FFmpeg LICENSE was not found at $FfmpegLicense"
}
if (-not (Test-Path -LiteralPath $FfmpegReadme -PathType Leaf)) {
    throw "FFmpeg README.txt was not found at $FfmpegReadme"
}

$BundledFfmpegBin = Join-Path $OutputRoot "ffmpeg\bin"
New-Item -ItemType Directory -Path $BundledFfmpegBin -Force | Out-Null
Copy-Item -LiteralPath $FfmpegSource -Destination (Join-Path $BundledFfmpegBin "ffmpeg.exe") -Force
Copy-Item -LiteralPath $FfprobeSource -Destination (Join-Path $BundledFfmpegBin "ffprobe.exe") -Force
Copy-Item -LiteralPath $FfmpegLicense -Destination (Join-Path $OutputRoot "ffmpeg\LICENSE") -Force
Copy-Item -LiteralPath $FfmpegReadme -Destination (Join-Path $OutputRoot "ffmpeg\README.txt") -Force

Write-Output "Desktop companion built at $OutputRoot\VideoGet.exe"
Write-Output "Bundled FFmpeg from $FfmpegRoot"
