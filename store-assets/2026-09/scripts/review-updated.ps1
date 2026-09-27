$ErrorActionPreference='Stop'
$assetRoot=Join-Path (Split-Path $PSScriptRoot -Parent) 'updated'
$chrome='C:/Program Files/Google/Chrome/Application/chrome.exe'
$profile=Join-Path $assetRoot 'review/chrome-profile'
$page=([uri](Join-Path $assetRoot 'index.html')).AbsoluteUri
$common=@('--headless=new','--disable-gpu','--hide-scrollbars','--no-first-run','--no-default-browser-check','--allow-file-access-from-files','--force-device-scale-factor=1','--virtual-time-budget=4000',"--user-data-dir=$profile")
$qa=Join-Path $assetRoot 'review/browser-qa.html'
Start-Process -FilePath $chrome -ArgumentList ($common+@('--dump-dom',($page+'?qa=1'))) -WindowStyle Hidden -Wait -RedirectStandardOutput $qa -RedirectStandardError (Join-Path $assetRoot 'review/browser-qa.log')
if (!(Select-String -LiteralPath $qa -Pattern 'data-qa="passed"' -Quiet)) {throw 'Gallery image/toggle checks failed'}
$preview=Join-Path $assetRoot 'review/gallery.png'
Start-Process -FilePath $chrome -ArgumentList ($common+@('--window-size=1440,1400',"--screenshot=$preview",$page)) -WindowStyle Hidden -Wait
Write-Output 'Updated gallery: all eight phone images and both view controls passed.'
