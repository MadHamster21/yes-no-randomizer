# Convert emulator captures to opaque RGB PNGs and size AI alternatives for comparison.
# No app pixels are painted or synthesized in the tablet screenshots.
param([string]$AssetRoot = (Split-Path $PSScriptRoot -Parent))
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing
$assetRoot = [System.IO.Path]::GetFullPath($AssetRoot)
New-Item -ItemType Directory -Force (Join-Path $assetRoot 'phone/generated') | Out-Null
function Export-RgbPng([string]$Source,[string]$Destination,[int]$Width=0,[int]$Height=0) {
    $inputImage = [System.Drawing.Image]::FromFile($Source)
    if ($Width -eq 0) { $Width=$inputImage.Width; $Height=$inputImage.Height }
    $bitmap = New-Object System.Drawing.Bitmap($Width,$Height,[System.Drawing.Imaging.PixelFormat]::Format24bppRgb)
    $graphics = [System.Drawing.Graphics]::FromImage($bitmap)
    try {
        $graphics.Clear([System.Drawing.Color]::White)
        $graphics.InterpolationMode=[System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
        $scale=[Math]::Min($Width / $inputImage.Width, $Height / $inputImage.Height)
        $drawWidth=[int][Math]::Round($inputImage.Width*$scale)
        $drawHeight=[int][Math]::Round($inputImage.Height*$scale)
        $graphics.DrawImage($inputImage,[int](($Width-$drawWidth)/2),[int](($Height-$drawHeight)/2),$drawWidth,$drawHeight)
        $bitmap.Save($Destination,[System.Drawing.Imaging.ImageFormat]::Png)
    } finally { $graphics.Dispose(); $bitmap.Dispose(); $inputImage.Dispose() }
}
foreach($card in @('01','02','03','04')) {
    Export-RgbPng (Join-Path $assetRoot "review/generated-originals/$card.png") (Join-Path $assetRoot "phone/generated/$card.png") 1080 1920
}
$tabletFiles=@('tablet-final-light','tablet-final-dark','tablet-final-language','tablet-final-spanish')
for($i=0;$i -lt $tabletFiles.Length;$i++) {
    $source=Join-Path $assetRoot ('raw/'+$tabletFiles[$i]+'.png')
    if(Test-Path -LiteralPath $source) {
        Export-RgbPng $source (Join-Path $assetRoot ('tablet/0'+($i+1)+'.png'))
    }
}
