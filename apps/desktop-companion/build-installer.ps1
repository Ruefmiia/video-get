[CmdletBinding()]
param(
    [switch]$SkipBuild
)

$ErrorActionPreference = "Stop"

if (-not $SkipBuild) {
    & (Join-Path $PSScriptRoot "build.ps1")
}

$Iscc = Get-Command "iscc.exe" -ErrorAction SilentlyContinue
if (-not $Iscc) {
    $CandidatePaths = @(
        (Join-Path $env:LOCALAPPDATA "Programs\Inno Setup 6\ISCC.exe"),
        "C:\Program Files (x86)\Inno Setup 6\ISCC.exe",
        "C:\Program Files\Inno Setup 6\ISCC.exe"
    )
    foreach ($CandidatePath in $CandidatePaths) {
        if (Test-Path -LiteralPath $CandidatePath) {
            $Iscc = Get-Item -LiteralPath $CandidatePath
            break
        }
    }
    if (-not $Iscc) {
        throw "未找到 Inno Setup 6。请确认 ISCC.exe 已安装，或将其目录加入 PATH。"
    }
}

$IsccPath = if ($Iscc.Source) { $Iscc.Source } else { $Iscc.FullName }
& $IsccPath (Join-Path $PSScriptRoot "installer\video-get.iss")
if ($LASTEXITCODE -ne 0) {
    throw "Inno Setup 构建失败，退出码：$LASTEXITCODE"
}
