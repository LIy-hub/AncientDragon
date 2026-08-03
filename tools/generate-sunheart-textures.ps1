param(
    [string]$ProjectRoot = (Split-Path -Parent $PSScriptRoot)
)

$ErrorActionPreference = 'Stop'

Add-Type -AssemblyName System.Drawing

$textureRoot = Join-Path $ProjectRoot 'src\main\resources\assets\ancient_dragon\textures'
$itemRoot = Join-Path $textureRoot 'item'
$wingRoot = Join-Path $textureRoot 'entity\equipment\wings'
$blockRoot = Join-Path $textureRoot 'block'
New-Item -ItemType Directory -Force -Path $itemRoot, $wingRoot, $blockRoot | Out-Null

$palette = @{
    Void = [System.Drawing.Color]::FromArgb(0, 0, 0, 0)
    Ink = [System.Drawing.Color]::FromArgb(255, 8, 13, 29)
    Navy = [System.Drawing.Color]::FromArgb(255, 17, 31, 67)
    Blue = [System.Drawing.Color]::FromArgb(255, 31, 75, 137)
    Azure = [System.Drawing.Color]::FromArgb(255, 50, 137, 198)
    Cyan = [System.Drawing.Color]::FromArgb(255, 77, 205, 231)
    Cloud = [System.Drawing.Color]::FromArgb(255, 202, 242, 255)
    GoldDark = [System.Drawing.Color]::FromArgb(255, 108, 65, 16)
    Gold = [System.Drawing.Color]::FromArgb(255, 205, 142, 35)
    GoldLight = [System.Drawing.Color]::FromArgb(255, 255, 220, 123)
    Obsidian = [System.Drawing.Color]::FromArgb(255, 16, 12, 28)
    ObsidianLight = [System.Drawing.Color]::FromArgb(255, 38, 30, 61)
    ObsidianMid = [System.Drawing.Color]::FromArgb(255, 59, 47, 88)
    RuneBlue = [System.Drawing.Color]::FromArgb(255, 30, 166, 219)
}

function New-Texture([int]$Width, [int]$Height) {
    return [System.Drawing.Bitmap]::new($Width, $Height, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
}

function Set-PixelSafe([System.Drawing.Bitmap]$Bitmap, [int]$X, [int]$Y, [System.Drawing.Color]$Color) {
    if ($X -ge 0 -and $X -lt $Bitmap.Width -and $Y -ge 0 -and $Y -lt $Bitmap.Height) {
        $Bitmap.SetPixel($X, $Y, $Color)
    }
}

function Save-Texture([System.Drawing.Bitmap]$Bitmap, [string]$Path) {
    $Bitmap.Save($Path, [System.Drawing.Imaging.ImageFormat]::Png)
    $Bitmap.Dispose()
}

function Set-ObsidianBase([System.Drawing.Bitmap]$Bitmap, [bool]$Brighter) {
    for ($y = 0; $y -lt $Bitmap.Height; $y++) {
        for ($x = 0; $x -lt $Bitmap.Width; $x++) {
            $noise = (($x * 13) + ($y * 7) + ($x * $y * 3)) % 11
            $color = if ($noise -lt 2) { $palette.Obsidian } elseif ($noise -lt 6) { $palette.ObsidianLight } else { $palette.ObsidianMid }
            if (-not $Brighter -and $noise -gt 6) { $color = $palette.ObsidianLight }
            Set-PixelSafe $Bitmap $x $y $color
        }
    }
}

# Inventory icon: folded left/right feathers around a narrow golden harness.
$icon = New-Texture 16 16
for ($y = 2; $y -le 12; $y++) {
    $span = if ($y -le 4) { 2 } elseif ($y -le 8) { 4 } elseif ($y -le 10) { 3 } else { 2 }
    $wingPixels = @()
    $wingPixels += ((7 - $span)..6)
    $wingPixels += (9..(8 + $span))
    foreach ($x in $wingPixels) {
        $edge = $x -eq (7 - $span) -or $x -eq (8 + $span) -or $y -eq 12
        $inner = $x -eq 6 -or $x -eq 9
        $color = if ($edge) { $palette.Ink } elseif ($inner) { $palette.GoldDark } elseif ((($x + $y) % 5) -eq 0) { $palette.Cloud } elseif ((($x + $y) % 3) -eq 0) { $palette.Cyan } elseif ($y -ge 9) { $palette.Azure } else { $palette.Blue }
        Set-PixelSafe $icon $x $y $color
    }
}
for ($y = 2; $y -le 12; $y++) {
    Set-PixelSafe $icon 7 $y $palette.GoldDark
    Set-PixelSafe $icon 8 $y $palette.Gold
}
Set-PixelSafe $icon 7 4 $palette.GoldLight
Set-PixelSafe $icon 8 4 $palette.GoldLight
Set-PixelSafe $icon 7 8 $palette.Cloud
Set-PixelSafe $icon 8 8 $palette.Cloud
Save-Texture $icon (Join-Path $itemRoot 'sky_wing.png')

# Equipment texture: retain the exact vanilla Elytra alpha silhouette (64x32) and repaint its opaque UV cells.
$wing = New-Texture 64 32
$wingRows = @{
    0 = @(31, 39); 1 = @(32, 33); 2 = @(34, 41); 3 = @(34, 42); 4 = @(35, 43); 5 = @(35, 43); 6 = @(35, 43)
    7 = @(35, 44); 8 = @(35, 44); 9 = @(35, 44); 10 = @(35, 44); 11 = @(36, 45); 12 = @(36, 45); 13 = @(36, 45)
    14 = @(36, 45); 15 = @(36, 45); 16 = @(37, 45); 17 = @(37, 45); 18 = @(37, 45); 19 = @(38, 45); 20 = @(38, 45); 21 = @(39, 45)
}
$mask = [System.Collections.Generic.HashSet[string]]::new()
foreach ($entry in $wingRows.GetEnumerator()) {
    for ($x = $entry.Value[0]; $x -le $entry.Value[1]; $x++) {
        [void]$mask.Add("$x,$($entry.Key)")
    }
}
for ($y = 11; $y -le 21; $y++) {
    [void]$mask.Add("22,$y")
}
foreach ($cell in $mask) {
    $parts = $cell -split ','
    $x = [int]$parts[0]
    $y = [int]$parts[1]
    $edge = $false
    foreach ($delta in @(@(-1, 0), @(1, 0), @(0, -1), @(0, 1))) {
        if (-not $mask.Contains("$($x + $delta[0]),$($y + $delta[1])")) { $edge = $true }
    }
    if ($x -eq 22) {
        $color = if (($y % 3) -eq 0) { $palette.GoldLight } else { $palette.Gold }
    } elseif ($edge) {
        $color = $palette.Ink
    } elseif ($y -le 1 -or $x -eq 35 -or (($x - 35) -eq [Math]::Floor(($y + 1) / 6))) {
        $color = if (($x + $y) % 4 -eq 0) { $palette.GoldLight } else { $palette.Gold }
    } else {
        $pattern = (($x * 5) + ($y * 3)) % 9
        $color = if ($pattern -eq 0) { $palette.Cloud } elseif ($pattern -le 2) { $palette.Cyan } elseif ($pattern -le 5) { $palette.Azure } else { $palette.Blue }
    }
    Set-PixelSafe $wing $x $y $color
}
Save-Texture $wing (Join-Path $wingRoot 'sky_wing.png')

# Sunheart Altar top: dark obsidian bed, a gold sun ring, and a cyan heart core.
$top = New-Texture 16 16
Set-ObsidianBase $top $true
for ($i = 0; $i -lt 16; $i++) {
    Set-PixelSafe $top $i 0 $palette.Ink; Set-PixelSafe $top $i 15 $palette.Ink
    Set-PixelSafe $top 0 $i $palette.Ink; Set-PixelSafe $top 15 $i $palette.Ink
}
for ($y = 1; $y -le 14; $y++) {
    for ($x = 1; $x -le 14; $x++) {
        $dx = [Math]::Abs($x - 7.5); $dy = [Math]::Abs($y - 7.5)
        $distance = $dx + $dy
        if ($distance -ge 5.5 -and $distance -le 6.5) {
            Set-PixelSafe $top $x $y $(if ((($x + $y) % 3) -eq 0) { $palette.GoldLight } else { $palette.GoldDark })
        }
    }
}
for ($i = 2; $i -le 13; $i++) {
    if ($i -notin @(5, 6, 9, 10)) {
        Set-PixelSafe $top 7 $i $palette.Gold
        Set-PixelSafe $top 8 $i $palette.GoldLight
        Set-PixelSafe $top $i 7 $palette.Gold
        Set-PixelSafe $top $i 8 $palette.GoldLight
    }
}
for ($y = 6; $y -le 9; $y++) {
    for ($x = 6; $x -le 9; $x++) {
        $core = if ($x -in @(7, 8) -and $y -in @(7, 8)) { $palette.Cloud } elseif ($x -in @(7, 8) -or $y -in @(7, 8)) { $palette.Cyan } else { $palette.RuneBlue }
        Set-PixelSafe $top $x $y $core
    }
}
Save-Texture $top (Join-Path $blockRoot 'sunheart_altar_top.png')

# Side: reinforced obsidian bands with a compact central sun and cyan fissures.
$side = New-Texture 16 16
Set-ObsidianBase $side $false
for ($x = 0; $x -lt 16; $x++) {
    Set-PixelSafe $side $x 0 $palette.Ink; Set-PixelSafe $side $x 15 $palette.Ink
    if (($x % 5) -ne 2) { Set-PixelSafe $side $x 2 $palette.GoldDark; Set-PixelSafe $side $x 13 $palette.GoldDark }
}
for ($x = 1; $x -le 14; $x++) {
    if (($x % 5) -ne 2) { Set-PixelSafe $side $x 1 $palette.Gold; Set-PixelSafe $side $x 14 $palette.GoldLight }
}
for ($y = 6; $y -le 9; $y++) {
    for ($x = 6; $x -le 9; $x++) {
        $sun = if ($x -in @(7, 8) -and $y -in @(7, 8)) { $palette.RuneBlue } elseif ($x -in @(7, 8) -or $y -in @(7, 8)) { $palette.GoldLight } else { $palette.Gold }
        Set-PixelSafe $side $x $y $sun
    }
}
foreach ($point in @(@(3, 4), @(4, 5), @(4, 6), @(11, 4), @(10, 5), @(10, 6), @(4, 10), @(3, 11), @(11, 10), @(12, 11))) {
    Set-PixelSafe $side $point[0] $point[1] $palette.RuneBlue
}
Save-Texture $side (Join-Path $blockRoot 'sunheart_altar_side.png')

# Bottom: nearly plain reinforced obsidian, with only sparse gold/cyan corner rivets.
$bottom = New-Texture 16 16
Set-ObsidianBase $bottom $false
for ($i = 0; $i -lt 16; $i++) {
    Set-PixelSafe $bottom $i 0 $palette.Ink; Set-PixelSafe $bottom $i 15 $palette.Ink
    Set-PixelSafe $bottom 0 $i $palette.Ink; Set-PixelSafe $bottom 15 $i $palette.Ink
}
foreach ($point in @(@(2, 2), @(13, 2), @(2, 13), @(13, 13))) {
    Set-PixelSafe $bottom $point[0] $point[1] $palette.GoldDark
}
foreach ($point in @(@(3, 2), @(12, 2), @(2, 3), @(13, 3))) {
    Set-PixelSafe $bottom $point[0] $point[1] $palette.RuneBlue
}
Save-Texture $bottom (Join-Path $blockRoot 'sunheart_altar_bottom.png')
