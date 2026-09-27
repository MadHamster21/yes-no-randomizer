param(
    [string]$Chrome = 'C:/Program Files/Google/Chrome/Application/chrome.exe',
    [string]$AssetRoot = (Split-Path $PSScriptRoot -Parent),
    [int]$PipY = 1408
)
$ErrorActionPreference = 'Stop'
$assetRoot = [System.IO.Path]::GetFullPath($AssetRoot)
$outputRoot = Join-Path $assetRoot 'phone/real'
$profileRoot = Join-Path $assetRoot 'review/chrome-profile'
New-Item -ItemType Directory -Force $outputRoot | Out-Null
New-Item -ItemType Directory -Force $profileRoot | Out-Null
$htmlPath = Join-Path $PSScriptRoot 'artwork.html'
foreach ($card in @('01','02','03','04')) {
    $outputPath = Join-Path $outputRoot "$card.png"
    $captureUrl = ([uri](Join-Path $assetRoot 'raw')).AbsoluteUri
    $pageUrl = ([uri]$htmlPath).AbsoluteUri + '?card=' + $card + '&pipY=' + $PipY + '&captures=' + [uri]::EscapeDataString($captureUrl)
    $arguments = @('--headless=new','--disable-gpu','--hide-scrollbars','--no-first-run','--no-default-browser-check','--allow-file-access-from-files','--force-device-scale-factor=1','--window-size=1080,1920','--virtual-time-budget=2500',"--user-data-dir=$profileRoot", "--screenshot=$outputPath",$pageUrl)
    $process = Start-Process -FilePath $Chrome -ArgumentList $arguments -WindowStyle Hidden -PassThru -Wait
    if ($process.ExitCode -ne 0 -or !(Test-Path -LiteralPath $outputPath)) { throw "Chrome export failed: $card" }
    Write-Output $outputPath
}
