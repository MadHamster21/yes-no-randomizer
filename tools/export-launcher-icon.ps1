$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing

# Reuse the approved raster artwork, preserving its die and symbols. Only the
# connected pale-blue backdrop is removed for Android's adaptive foreground.
Add-Type -ReferencedAssemblies System.Drawing -TypeDefinition @'
using System;
using System.Drawing;
using System.Drawing.Drawing2D;
using System.Drawing.Imaging;

public static class DecisionLauncherIcon
{
    public static Bitmap Foreground(string sourcePath)
    {
        using (var source = new Bitmap(sourcePath))
        {
            int width = source.Width, height = source.Height;
            var pixels = new Color[width * height];
            var outside = new bool[pixels.Length];
            var queue = new int[pixels.Length];
            int head = 0, tail = 0;
            for (int y = 0; y < height; y++)
                for (int x = 0; x < width; x++)
                    pixels[y * width + x] = source.GetPixel(x, y);
            Action<int> visit = index => {
                if (outside[index]) return;
                Color c = pixels[index];
                int min = Math.Min(c.R, Math.Min(c.G, c.B));
                int max = Math.Max(c.R, Math.Max(c.G, c.B));
                if (min < 120 || max - min >= 80 || c.B - c.R < 6 || c.G < c.R) return;
                outside[index] = true;
                queue[tail++] = index;
            };
            for (int x = 0; x < width; x++) { visit(x); visit((height - 1) * width + x); }
            for (int y = 0; y < height; y++) { visit(y * width); visit(y * width + width - 1); }
            while (head < tail)
            {
                int i = queue[head++], x = i % width, y = i / width;
                if (x > 0) visit(i - 1);
                if (x + 1 < width) visit(i + 1);
                if (y > 0) visit(i - width);
                if (y + 1 < height) visit(i + width);
            }
            var result = new Bitmap(width, height, PixelFormat.Format32bppArgb);
            for (int y = 0; y < height; y++)
                for (int x = 0; x < width; x++)
                    if (!outside[y * width + x]) result.SetPixel(x, y, pixels[y * width + x]);
            return result;
        }
    }

    public static void Export(Image source, string destination, int size, bool round, bool adaptive)
    {
        using (var bitmap = new Bitmap(size, size, PixelFormat.Format32bppArgb))
        using (var graphics = Graphics.FromImage(bitmap))
        using (var attributes = new ImageAttributes())
        {
            graphics.Clear(Color.Transparent);
            graphics.SmoothingMode = SmoothingMode.AntiAlias;
            graphics.CompositingQuality = CompositingQuality.HighQuality;
            graphics.InterpolationMode = InterpolationMode.HighQualityBicubic;
            graphics.PixelOffsetMode = PixelOffsetMode.HighQuality;
            attributes.SetWrapMode(WrapMode.TileFlipXY);
            if (!adaptive)
            {
                using (var path = new GraphicsPath())
                {
                    if (round) path.AddEllipse(0, 0, size, size);
                    else
                    {
                        float diameter = size * 0.44f;
                        path.AddArc(0, 0, diameter, diameter, 180, 90);
                        path.AddArc(size - diameter, 0, diameter, diameter, 270, 90);
                        path.AddArc(size - diameter, size - diameter, diameter, diameter, 0, 90);
                        path.AddArc(0, size - diameter, diameter, diameter, 90, 90);
                        path.CloseFigure();
                    }
                    graphics.SetClip(path);
                }
            }
            if (!adaptive)
                using (var background = new SolidBrush(Color.FromArgb(239, 246, 255)))
                    graphics.FillRectangle(background, 0, 0, size, size);
            // The die occupies ~55 dp of the 108 dp adaptive canvas. Its full
            // silhouette stays inside the guaranteed central 66 dp circle.
            float extent = adaptive ? size * 74f / 108f : size * 0.94f;
            float inset = (size - extent) / 2f;
            graphics.DrawImage(source, new[] {
                new PointF(inset, inset), new PointF(inset + extent, inset),
                new PointF(inset, inset + extent)
            }, new RectangleF(0, 0, source.Width, source.Height), GraphicsUnit.Pixel, attributes);
            if (adaptive)
            {
                for (int y = 0; y < size; y++)
                    for (int x = 0; x < size; x++)
                    {
                        if (bitmap.GetPixel(x, y).A < 16) continue;
                        double dx = (x + 0.5 - size / 2.0) * 108 / size;
                        double dy = (y + 0.5 - size / 2.0) * 108 / size;
                        if (dx * dx + dy * dy > 33 * 33)
                            throw new InvalidOperationException("Foreground exceeds adaptive icon safe circle.");
                    }
            }
            bitmap.Save(destination, ImageFormat.Png);
        }
    }
}
'@

$repoRoot = Split-Path $PSScriptRoot -Parent
$sourcePath = Join-Path $repoRoot 'store-assets/2026-10/app-icon-options/01-decision-die.png'
$resourceRoot = Join-Path $repoRoot 'app/src/main/res'
$foreground = [DecisionLauncherIcon]::Foreground($sourcePath)
try {
    foreach ($entry in @(
        @{ Density = 'mdpi'; Legacy = 48; Adaptive = 108 },
        @{ Density = 'hdpi'; Legacy = 72; Adaptive = 162 },
        @{ Density = 'xhdpi'; Legacy = 96; Adaptive = 216 },
        @{ Density = 'xxhdpi'; Legacy = 144; Adaptive = 324 },
        @{ Density = 'xxxhdpi'; Legacy = 192; Adaptive = 432 }
    )) {
        $folder = Join-Path $resourceRoot ('mipmap-' + $entry.Density)
        [DecisionLauncherIcon]::Export($foreground, (Join-Path $folder 'logo.png'), $entry.Legacy, $false, $false)
        [DecisionLauncherIcon]::Export($foreground, (Join-Path $folder 'logo_round.png'), $entry.Legacy, $true, $false)
        [DecisionLauncherIcon]::Export($foreground, (Join-Path $folder 'logo_foreground.png'), $entry.Adaptive, $false, $true)
    }
} finally {
    $foreground.Dispose()
}
Write-Output 'Exported approved decision-die artwork at all five launcher densities.'
