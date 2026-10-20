param(
  [string]$OutputDirectory = (Join-Path $PSScriptRoot '..\public\icons'),
  [string]$FaviconPath = (Join-Path $PSScriptRoot '..\public\favicon.ico')
)

Add-Type -AssemblyName System.Drawing

function New-RoundedRectanglePath {
  param([float]$X, [float]$Y, [float]$Width, [float]$Height, [float]$Radius)
  $path = New-Object System.Drawing.Drawing2D.GraphicsPath
  $diameter = $Radius * 2
  $path.AddArc($X, $Y, $diameter, $diameter, 180, 90)
  $path.AddArc($X + $Width - $diameter, $Y, $diameter, $diameter, 270, 90)
  $path.AddArc($X + $Width - $diameter, $Y + $Height - $diameter, $diameter, $diameter, 0, 90)
  $path.AddArc($X, $Y + $Height - $diameter, $diameter, $diameter, 90, 90)
  $path.CloseFigure()
  return $path
}

function Draw-AppIcon {
  param([int]$Size, [string]$Path, [bool]$Maskable)

  $bitmap = New-Object System.Drawing.Bitmap($Size, $Size, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
  $graphics = [System.Drawing.Graphics]::FromImage($bitmap)
  $graphics.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
  $graphics.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
  $graphics.Clear([System.Drawing.Color]::Transparent)

  $bounds = New-Object System.Drawing.Rectangle(0, 0, $Size, $Size)
  $gradient = New-Object System.Drawing.Drawing2D.LinearGradientBrush(
    $bounds,
    [System.Drawing.ColorTranslator]::FromHtml('#0f766e'),
    [System.Drawing.ColorTranslator]::FromHtml('#0f172a'),
    45.0
  )

  if ($Maskable) {
    $graphics.FillRectangle($gradient, $bounds)
  } else {
    $background = New-RoundedRectanglePath 0 0 $Size $Size ($Size * 0.22)
    $graphics.FillPath($gradient, $background)
    $background.Dispose()
  }

  $scale = if ($Maskable) { 0.54 } else { 0.68 }
  $origin = ($Size - ($Size * $scale)) / 2.0

  function X([double]$value) { return [float]($origin + ($Size * $scale * $value)) }
  function Y([double]$value) { return [float]($origin + ($Size * $scale * $value)) }

  $white = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::White)
  $accent = New-Object System.Drawing.SolidBrush([System.Drawing.ColorTranslator]::FromHtml('#0f766e'))

  $roof = @(
    [System.Drawing.PointF]::new((X 0.06), (Y 0.50)),
    [System.Drawing.PointF]::new((X 0.50), (Y 0.08)),
    [System.Drawing.PointF]::new((X 0.94), (Y 0.50))
  )
  $graphics.FillPolygon($white, $roof)

  $body = New-RoundedRectanglePath (X 0.16) (Y 0.44) ([float]($Size * $scale * 0.68)) ([float]($Size * $scale * 0.46)) ([float]($Size * $scale * 0.05))
  $graphics.FillPath($white, $body)
  $body.Dispose()

  $windowSize = [float]($Size * $scale * 0.11)
  $windowY = Y 0.55
  $leftWindow = New-RoundedRectanglePath (X 0.29) $windowY $windowSize $windowSize ([float]($windowSize * 0.18))
  $rightWindow = New-RoundedRectanglePath (X 0.60) $windowY $windowSize $windowSize ([float]($windowSize * 0.18))
  $graphics.FillPath($accent, $leftWindow)
  $graphics.FillPath($accent, $rightWindow)
  $leftWindow.Dispose()
  $rightWindow.Dispose()

  $doorWidth = [float]($Size * $scale * 0.15)
  $doorHeight = [float]($Size * $scale * 0.22)
  $door = New-RoundedRectanglePath (X 0.425) (Y 0.68) $doorWidth $doorHeight ([float]($doorWidth * 0.38))
  $graphics.FillPath($accent, $door)
  $door.Dispose()

  $bitmap.Save($Path, [System.Drawing.Imaging.ImageFormat]::Png)
  $accent.Dispose()
  $white.Dispose()
  $gradient.Dispose()
  $graphics.Dispose()
  $bitmap.Dispose()
}

function Write-PngIcon {
  param([string]$PngPath, [string]$IcoPath, [byte]$Size)

  $png = [System.IO.File]::ReadAllBytes($PngPath)
  $stream = [System.IO.File]::Create($IcoPath)
  $writer = New-Object System.IO.BinaryWriter($stream)
  try {
    $writer.Write([UInt16]0)
    $writer.Write([UInt16]1)
    $writer.Write([UInt16]1)
    $writer.Write($Size)
    $writer.Write($Size)
    $writer.Write([byte]0)
    $writer.Write([byte]0)
    $writer.Write([UInt16]1)
    $writer.Write([UInt16]32)
    $writer.Write([UInt32]$png.Length)
    $writer.Write([UInt32]22)
    $writer.Write($png)
  } finally {
    $writer.Dispose()
    $stream.Dispose()
  }
}

New-Item -ItemType Directory -Force -Path $OutputDirectory | Out-Null
New-Item -ItemType Directory -Force -Path (Split-Path -Parent $FaviconPath) | Out-Null

Draw-AppIcon -Size 192 -Path (Join-Path $OutputDirectory 'icon-192.png') -Maskable $false
Draw-AppIcon -Size 512 -Path (Join-Path $OutputDirectory 'icon-512.png') -Maskable $false
Draw-AppIcon -Size 512 -Path (Join-Path $OutputDirectory 'maskable-512.png') -Maskable $true

$temporaryFavicon = Join-Path $env:TEMP ('vecindad-favicon-' + [Guid]::NewGuid().ToString('N') + '.png')
try {
  Draw-AppIcon -Size 64 -Path $temporaryFavicon -Maskable $false
  Write-PngIcon -PngPath $temporaryFavicon -IcoPath $FaviconPath -Size 64
} finally {
  if (Test-Path $temporaryFavicon) { Remove-Item $temporaryFavicon -Force }
}

Write-Host "Iconos generados en $OutputDirectory y $FaviconPath"
