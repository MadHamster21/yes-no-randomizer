param([string]$Chrome = 'C:/Program Files/Google/Chrome/Application/chrome.exe')
$ErrorActionPreference = 'Stop'
$assetRoot = Split-Path $PSScriptRoot -Parent
$outputRoot = Join-Path $assetRoot 'phone/real'
$profileRoot = Join-Path $assetRoot 'review/chrome-profile'
New-Item -ItemType Directory -Force $outputRoot | Out-Null
$htmlPath = Join-Path $PSScriptRoot 'artwork.html'
foreach ($card in @('01','02','03','04')) {
    $outputPath = Join-Path $outputRoot "$card.png"
    $pageUrl = ([uri]$htmlPath).AbsoluteUri + '?card=' + $card
    $arguments = @('--headless=new','--disable-gpu','--hide-scrollbars','--no-first-run','--no-default-browser-check','--allow-file-access-from-files','--force-device-scale-factor=1','--window-size=1080,1920','--virtual-time-budget=2500',"--user-data-dir=$profileRoot", "--screenshot=$outputPath",$pageUrl)
    $process = Start-Process -FilePath $Chrome -ArgumentList $arguments -WindowStyle Hidden -PassThru -Wait
    if ($process.ExitCode -ne 0 -or !(Test-Path -LiteralPath $outputPath)) { throw "Chrome export failed: $card" }
    Write-Output $outputPath
}
