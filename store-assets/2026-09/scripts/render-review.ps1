$ErrorActionPreference='Stop'
$assetRoot=Split-Path $PSScriptRoot -Parent
$chrome='C:/Program Files/Google/Chrome/Application/chrome.exe'
$profile=Join-Path $assetRoot 'review/chrome-profile'
$common=@('--headless=new','--disable-gpu','--hide-scrollbars','--no-first-run','--no-default-browser-check','--allow-file-access-from-files','--force-device-scale-factor=1','--virtual-time-budget=3000',"--user-data-dir=$profile")
$contact=([uri](Join-Path $assetRoot 'review/contact-sheet.html')).AbsoluteUri
$out=Join-Path $assetRoot 'review/comparison.png'
Start-Process -FilePath $chrome -ArgumentList ($common+@('--window-size=1600,1680',"--screenshot=$out",$contact)) -WindowStyle Hidden -Wait
$gallery=([uri](Join-Path $assetRoot 'index.html')).AbsoluteUri
$out=Join-Path $assetRoot 'review/gallery.png'
Start-Process -FilePath $chrome -ArgumentList ($common+@('--window-size=1440,1500',"--screenshot=$out",$gallery)) -WindowStyle Hidden -Wait
$qa=Join-Path $assetRoot 'review/browser-qa.html'
Start-Process -FilePath $chrome -ArgumentList ($common+@('--dump-dom',($gallery+'?qa=1'))) -WindowStyle Hidden -Wait -RedirectStandardOutput $qa -RedirectStandardError (Join-Path $assetRoot 'review/browser-qa.log')
if (!(Select-String -LiteralPath $qa -Pattern 'data-qa="passed"' -Quiet)) {throw 'Gallery image/button checks failed'}
Write-Output 'Gallery images and controls passed browser checks; review previews exported.'
