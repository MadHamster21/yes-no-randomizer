$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing

$sourcePath = Join-Path $PSScriptRoot '01-decision-die.png'
$outputPath = Join-Path $PSScriptRoot 'play-store-icon-512.png'
$source = [System.Drawing.Image]::FromFile($sourcePath, $true)
$bitmap = [System.Drawing.Bitmap]::new(512, 512, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
$graphics = [System.Drawing.Graphics]::FromImage($bitmap)
$attributes = [System.Drawing.Imaging.ImageAttributes]::new()
try {
    $graphics.Clear([System.Drawing.Color]::FromArgb(255, 239, 246, 255))
    $graphics.CompositingMode = [System.Drawing.Drawing2D.CompositingMode]::SourceOver
    $graphics.CompositingQuality = [System.Drawing.Drawing2D.CompositingQuality]::HighQuality
    $graphics.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
    $graphics.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
    $attributes.SetWrapMode([System.Drawing.Drawing2D.WrapMode]::TileFlipXY)
    $graphics.DrawImage($source, [System.Drawing.Rectangle]::new(0, 0, 512, 512),
        0, 0, $source.Width, $source.Height, [System.Drawing.GraphicsUnit]::Pixel, $attributes)
    $bitmap.Save($outputPath, [System.Drawing.Imaging.ImageFormat]::Png)
} finally {
    $attributes.Dispose()
    $graphics.Dispose()
    $bitmap.Dispose()
    $source.Dispose()
}

& node (Join-Path $PSScriptRoot 'finalize-play-icon.mjs') $outputPath
if ($LASTEXITCODE -ne 0) { throw 'PNG finalization or validation failed.' }
