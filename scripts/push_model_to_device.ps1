$ErrorActionPreference = "Stop"

$modelPath = Join-Path $PSScriptRoot "..\models\gemma-4-E2B-it-gpu.litertlm"
$packageName = "com.edupulse.app"
$deviceModelDir = "/sdcard/Android/data/$packageName/files"

$adbCommand = Get-Command adb -ErrorAction SilentlyContinue
if ($null -eq $adbCommand) {
    throw "adb was not found on PATH. Install Android Platform Tools and add it to PATH."
}

if (-not (Test-Path -LiteralPath $modelPath -PathType Leaf)) {
    throw "Model not found: $modelPath"
}

$deviceLines = & $adbCommand.Source devices | Select-String "`tdevice$"
if ($deviceLines.Count -lt 1) {
    throw "No authorized Android device found. Enable USB debugging and authorize this computer."
}

& $adbCommand.Source shell "mkdir -p $deviceModelDir"
& $adbCommand.Source push $modelPath "$deviceModelDir/"
if ($LASTEXITCODE -ne 0) {
    throw "adb push failed."
}

Write-Output "Model pushed to $deviceModelDir"
