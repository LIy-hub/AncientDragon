param(
    [string]$PackRoot = (Join-Path $PSScriptRoot '..\datapacks\snowbrush'),
    [ValidateRange(1, 64)]
    [int]$MaxRadius = 64,
    [ValidateRange(1, 8)]
    [int]$VariantCount = 4
)

$resolvedPackRoot = [System.IO.Path]::GetFullPath($PackRoot)
$functionRoot = Join-Path $resolvedPackRoot 'data\snowbrush\function\brush'
$utf8NoBom = [System.Text.UTF8Encoding]::new($false)

& (Join-Path $PSScriptRoot 'Generate-SnowbrushSphereFunctions.ps1') `
    -PackRoot $resolvedPackRoot `
    -MaxRadius $MaxRadius

function Format-RelativeCoordinate([int]$Value) {
    if ($Value -eq 0) {
        return '~'
    }
    return "~$Value"
}

function Write-FunctionFile([string]$Path, [System.Collections.Generic.List[string]]$Lines) {
    [System.IO.Directory]::CreateDirectory([System.IO.Path]::GetDirectoryName($Path)) | Out-Null
    [System.IO.File]::WriteAllLines($Path, $Lines, $utf8NoBom)
}

function Clamp-Integer([int]$Value, [int]$Minimum, [int]$Maximum) {
    return [Math]::Max($Minimum, [Math]::Min($Maximum, $Value))
}

function Get-Noise3D([int]$X, [int]$Y, [int]$Z, [int]$Variant) {
    $wave = [Math]::Sin(($X + $Variant * 2.7) * 0.61) * 0.42
    $wave += [Math]::Cos(($Z - $Variant * 1.9) * 0.53) * 0.34
    $wave += [Math]::Sin(($Y + $Z + $Variant) * 0.47) * 0.24
    $hashRaw = [Math]::Sin($X * 12.9898 + $Y * 37.719 + $Z * 78.233 + $Variant * 19.19) * 43758.5453
    $hash = ($hashRaw - [Math]::Floor($hashRaw)) * 2.0 - 1.0
    return $wave + $hash * 0.28
}

function Get-Noise2D([int]$X, [int]$Z, [int]$Variant) {
    $wave = [Math]::Sin(($X + $Variant * 3.1) * 0.44) * 0.48
    $wave += [Math]::Cos(($Z - $Variant * 2.3) * 0.39) * 0.38
    $wave += [Math]::Sin(($X + $Z + $Variant * 5) * 0.21) * 0.22
    $hashRaw = [Math]::Sin($X * 17.13 + $Z * 41.73 + $Variant * 23.41) * 15731.743
    $hash = ($hashRaw - [Math]::Floor($hashRaw)) * 2.0 - 1.0
    return $wave + $hash * 0.22
}

function Add-RandomRunCommands(
    [System.Collections.Generic.List[string]]$ReplaceLines,
    [System.Collections.Generic.List[string]]$GenerateLines,
    [System.Collections.Generic.List[string]]$ClearLines,
    [int]$XMin,
    [int]$XMax,
    [int]$Y,
    [int]$ZMin,
    [int]$ZMax
) {
    $relativeXMin = Format-RelativeCoordinate $XMin
    $relativeXMax = Format-RelativeCoordinate $XMax
    $relativeY = Format-RelativeCoordinate $Y
    $relativeZMin = Format-RelativeCoordinate $ZMin
    $relativeZMax = Format-RelativeCoordinate $ZMax
    $replaceLines.Add(('$fill {0} {1} {2} {3} {1} {4} $(id) replace #snowbrush:replaceable' -f $relativeXMin, $relativeY, $relativeZMin, $relativeXMax, $relativeZMax))
    $generateLines.Add(('$fill {0} {1} {2} {3} {1} {4} $(id) replace minecraft:air' -f $relativeXMin, $relativeY, $relativeZMin, $relativeXMax, $relativeZMax))
    $generateLines.Add(('$fill {0} {1} {2} {3} {1} {4} $(id) replace minecraft:cave_air' -f $relativeXMin, $relativeY, $relativeZMin, $relativeXMax, $relativeZMax))
    $clearLines.Add(('fill {0} {1} {2} {3} {1} {4} minecraft:air' -f $relativeXMin, $relativeY, $relativeZMin, $relativeXMax, $relativeZMax))
}

# 四套连续、无孤块的低频噪声体素轮廓。
for ($radius = 1; $radius -le $MaxRadius; $radius++) {
    for ($variant = 1; $variant -le $VariantCount; $variant++) {
        $replaceLines = [System.Collections.Generic.List[string]]::new()
        $generateLines = [System.Collections.Generic.List[string]]::new()
        $clearLines = [System.Collections.Generic.List[string]]::new()
        $replaceLines.Add("# 随机有机替换，半径 $radius，轮廓 $variant")
        $generateLines.Add("# 随机有机生成，半径 $radius，轮廓 $variant")
        $clearLines.Add("# 随机有机清空，半径 $radius，轮廓 $variant")

        # 大范围使用连续、分层的低频轮廓，并把相邻同宽 Z 行合并成一个 fill。
        $xScale = [Math]::Max(1.0, $radius * 1.04)
        $yScale = [Math]::Max(1.0, $radius * 0.84)
        $zScale = [Math]::Max(1.0, $radius * 0.98)
        $quantizeStep = if ($radius -le 16) { 1 } else { [Math]::Max(1, [int][Math]::Floor($radius / 12.0)) }
        $yLimit = [int][Math]::Ceiling($yScale)
        for ($y = -$yLimit; $y -le $yLimit; $y++) {
            $remainingAfterY = 1.0 - ($y / $yScale) * ($y / $yScale)
            if ($remainingAfterY -lt 0.0) {
                continue
            }
            $layerNoise = Get-Noise3D 0 $y 0 $variant
            $layerXScale = $xScale * (1.0 + $layerNoise * 0.10)
            $layerZScale = $zScale * (1.0 + [Math]::Sin($y * 0.19 + $variant * 1.7) * 0.09)
            $centerX = [int][Math]::Round([Math]::Sin($y * 0.23 + $variant * 1.13) * [Math]::Min(5.0, $radius * 0.08))
            $centerZ = [int][Math]::Round([Math]::Cos($y * 0.17 + $variant * 0.91) * [Math]::Min(4.0, $radius * 0.06))
            $zLimit = [int][Math]::Floor($layerZScale * [Math]::Sqrt($remainingAfterY))
            $groupStartZ = $null
            $groupXMin = $null
            $groupXMax = $null
            for ($zOffset = -$zLimit; $zOffset -le $zLimit; $zOffset++) {
                $z = $centerZ + $zOffset
                $remaining = $remainingAfterY - ($zOffset / $layerZScale) * ($zOffset / $layerZScale)
                if ($remaining -lt 0.0) {
                    continue
                }
                $rawHalfWidth = [int][Math]::Floor($layerXScale * [Math]::Sqrt($remaining))
                $halfWidth = if ($quantizeStep -eq 1) { $rawHalfWidth } else { [int][Math]::Floor($rawHalfWidth / $quantizeStep) * $quantizeStep }
                $xMin = Clamp-Integer ($centerX - $halfWidth) (-$radius) $radius
                $xMax = Clamp-Integer ($centerX + $halfWidth) (-$radius) $radius
                if ($null -eq $groupStartZ) {
                    $groupStartZ = $z
                    $groupXMin = $xMin
                    $groupXMax = $xMax
                }
                $isLast = $zOffset -eq $zLimit
                if ($xMin -ne $groupXMin -or $xMax -ne $groupXMax -or $isLast) {
                    $groupEndZ = if ($xMin -ne $groupXMin -or $xMax -ne $groupXMax) { $z - 1 } else { $z }
                    Add-RandomRunCommands $replaceLines $generateLines $clearLines $groupXMin $groupXMax $y $groupStartZ $groupEndZ
                    if ($xMin -ne $groupXMin -or $xMax -ne $groupXMax) {
                        $groupStartZ = $z
                        $groupXMin = $xMin
                        $groupXMax = $xMax
                        if ($isLast) {
                            Add-RandomRunCommands $replaceLines $generateLines $clearLines $groupXMin $groupXMax $y $z $z
                        }
                    }
                }
            }
        }

        Write-FunctionFile (Join-Path $functionRoot "random\replace\r$radius\v$variant.mcfunction") $replaceLines
        Write-FunctionFile (Join-Path $functionRoot "random\generate\r$radius\v$variant.mcfunction") $generateLines
        Write-FunctionFile (Join-Path $functionRoot "random\clear\r$radius\v$variant.mcfunction") $clearLines
    }
}

# 旧版逐列贴地算法保留为迁移参考，但不再生成。
if ($false) {
for ($radius = 1; $radius -le $MaxRadius; $radius++) {
    $scanRadius = $radius + 3
    $maxGenerateAmount = [Math]::Min(8, $radius + 1)
    $maxReplaceAmount = [Math]::Min(5, $radius)
    $maxClearAmount = [Math]::Min(7, $radius + 1)

    for ($amount = 1; $amount -le $maxGenerateAmount; $amount++) {
        $applyLines = [System.Collections.Generic.List[string]]::new()
        $applyLines.Add(('$fill ~ ~1 ~ ~ ~{0} ~ $(id) replace minecraft:air' -f $amount))
        $applyLines.Add(('$fill ~ ~1 ~ ~ ~{0} ~ $(id) replace minecraft:cave_air' -f $amount))
        Write-FunctionFile (Join-Path $functionRoot "natural\apply\generate\a$amount.mcfunction") $applyLines

        $scanLines = [System.Collections.Generic.List[string]]::new()
        $scanLines.Add("# 从落点附近向下寻找暴露表面，生成高度 $amount")
        for ($scanY = $scanRadius; $scanY -ge -$scanRadius; $scanY--) {
            $relativeY = Format-RelativeCoordinate $scanY
            $scanLines.Add(('execute positioned ~ {0} ~ if block ~ ~ ~ #snowbrush:replaceable unless block ~ ~1 ~ #snowbrush:replaceable run return run function snowbrush:brush/natural/apply/generate/a{1} with entity @s item' -f $relativeY, $amount))
        }
        Write-FunctionFile (Join-Path $functionRoot "natural\generate\column\r$radius\a$amount.mcfunction") $scanLines
    }

    for ($amount = 1; $amount -le $maxReplaceAmount; $amount++) {
        $depth = Format-RelativeCoordinate (-($amount - 1))
        $applyLines = [System.Collections.Generic.List[string]]::new()
        $applyLines.Add(('$fill ~ {0} ~ ~ ~ ~ $(id) replace #snowbrush:replaceable' -f $depth))
        Write-FunctionFile (Join-Path $functionRoot "natural\apply\replace\a$amount.mcfunction") $applyLines

        $scanLines = [System.Collections.Generic.List[string]]::new()
        $scanLines.Add("# 从落点附近向下寻找暴露表面，替换深度 $amount")
        for ($scanY = $scanRadius; $scanY -ge -$scanRadius; $scanY--) {
            $relativeY = Format-RelativeCoordinate $scanY
            $scanLines.Add(('execute positioned ~ {0} ~ if block ~ ~ ~ #snowbrush:replaceable unless block ~ ~1 ~ #snowbrush:replaceable run return run function snowbrush:brush/natural/apply/replace/a{1} with entity @s item' -f $relativeY, $amount))
        }
        Write-FunctionFile (Join-Path $functionRoot "natural\replace\column\r$radius\a$amount.mcfunction") $scanLines
    }

    for ($amount = 1; $amount -le $maxClearAmount; $amount++) {
        $depth = Format-RelativeCoordinate (-($amount - 1))
        $applyLines = [System.Collections.Generic.List[string]]::new()
        $applyLines.Add(('fill ~ {0} ~ ~ ~ ~ minecraft:air' -f $depth))
        Write-FunctionFile (Join-Path $functionRoot "natural\apply\clear\a$amount.mcfunction") $applyLines

        $scanLines = [System.Collections.Generic.List[string]]::new()
        $scanLines.Add("# 从落点附近向下寻找暴露表面，侵蚀深度 $amount")
        for ($scanY = $scanRadius; $scanY -ge -$scanRadius; $scanY--) {
            $relativeY = Format-RelativeCoordinate $scanY
            $scanLines.Add(('execute positioned ~ {0} ~ if block ~ ~ ~ #snowbrush:replaceable unless block ~ ~1 ~ #snowbrush:replaceable run return run function snowbrush:brush/natural/apply/clear/a{1}' -f $relativeY, $amount))
        }
        Write-FunctionFile (Join-Path $functionRoot "natural\clear\column\r$radius\a$amount.mcfunction") $scanLines
    }

    for ($variant = 1; $variant -le $VariantCount; $variant++) {
        $generateLines = [System.Collections.Generic.List[string]]::new()
        $replaceLines = [System.Collections.Generic.List[string]]::new()
        $clearLines = [System.Collections.Generic.List[string]]::new()
        $generateLines.Add("# 自然地形生成，半径 $radius，轮廓 $variant")
        $replaceLines.Add("# 自然地形替换，半径 $radius，轮廓 $variant")
        $clearLines.Add("# 自然地形清空，半径 $radius，轮廓 $variant")
        $columns = [System.Collections.Generic.List[object]]::new()

        for ($z = -$radius; $z -le $radius; $z++) {
            for ($x = -$radius; $x -le $radius; $x++) {
                $xScale = [Math]::Max(1.0, $radius * 1.08)
                $zScale = [Math]::Max(1.0, $radius * 0.96)
                $distance = [Math]::Sqrt(($x / $xScale) * ($x / $xScale) + ($z / $zScale) * ($z / $zScale))
                $noise = Get-Noise2D $x $z $variant
                $threshold = 1.0 + $noise * 0.13
                if ($distance -gt $threshold -and -not ($x -eq 0 -and $z -eq 0)) {
                    continue
                }

                $profile = [Math]::Max(0.0, 1.0 - ($distance / [Math]::Max(0.6, $threshold)))
                $generateAmount = Clamp-Integer ([int][Math]::Round(1.0 + $profile * $radius * 0.55 + $noise * 0.75)) 1 $maxGenerateAmount
                $replaceAmount = Clamp-Integer ([int][Math]::Round(1.0 + $profile * [Math]::Min(4.0, $radius * 0.34) + $noise * 0.35)) 1 $maxReplaceAmount
                $clearAmount = Clamp-Integer ([int][Math]::Round(1.0 + $profile * [Math]::Min(6.0, $radius * 0.46) + $noise * 0.55)) 1 $maxClearAmount
                $orderRaw = [Math]::Sin($x * 31.17 + $z * 47.73 + $variant * 11.91) * 10000.0
                $order = $orderRaw - [Math]::Floor($orderRaw)
                $columns.Add([pscustomobject]@{
                    X = $x
                    Z = $z
                    Generate = $generateAmount
                    Replace = $replaceAmount
                    Clear = $clearAmount
                    Order = $order
                })
            }
        }

        foreach ($column in ($columns | Sort-Object Order)) {
            $relativeX = Format-RelativeCoordinate $column.X
            $relativeZ = Format-RelativeCoordinate $column.Z
            $generateLines.Add(('execute positioned {0} ~ {1} run function snowbrush:brush/natural/generate/column/r{2}/a{3}' -f $relativeX, $relativeZ, $radius, $column.Generate))
            $replaceLines.Add(('execute positioned {0} ~ {1} run function snowbrush:brush/natural/replace/column/r{2}/a{3}' -f $relativeX, $relativeZ, $radius, $column.Replace))
            $clearLines.Add(('execute positioned {0} ~ {1} run function snowbrush:brush/natural/clear/column/r{2}/a{3}' -f $relativeX, $relativeZ, $radius, $column.Clear))
        }

        Write-FunctionFile (Join-Path $functionRoot "natural\generate\r$radius\v$variant.mcfunction") $generateLines
        Write-FunctionFile (Join-Path $functionRoot "natural\replace\r$radius\v$variant.mcfunction") $replaceLines
        Write-FunctionFile (Join-Path $functionRoot "natural\clear\r$radius\v$variant.mcfunction") $clearLines
    }
}
}

# 自然化不是材料矿脉，而是对现有地表做一次局部高度松弛：
# 三侧较高时补一格凹坑，三侧较低时削一格突刺。空手复制原地表材质，
# 有副手方块时用该方块补洞，并在少量稳定采样点混入表层。
$highNeighborTriples = @(
    'if block ~1 ~1 ~ #snowbrush:replaceable if block ~-1 ~1 ~ #snowbrush:replaceable if block ~ ~1 ~1 #snowbrush:replaceable',
    'if block ~1 ~1 ~ #snowbrush:replaceable if block ~-1 ~1 ~ #snowbrush:replaceable if block ~ ~1 ~-1 #snowbrush:replaceable',
    'if block ~1 ~1 ~ #snowbrush:replaceable if block ~ ~1 ~1 #snowbrush:replaceable if block ~ ~1 ~-1 #snowbrush:replaceable',
    'if block ~-1 ~1 ~ #snowbrush:replaceable if block ~ ~1 ~1 #snowbrush:replaceable if block ~ ~1 ~-1 #snowbrush:replaceable'
)
$lowNeighborTriples = @(
    'if block ~1 ~ ~ #snowbrush:air if block ~-1 ~ ~ #snowbrush:air if block ~ ~ ~1 #snowbrush:air',
    'if block ~1 ~ ~ #snowbrush:air if block ~-1 ~ ~ #snowbrush:air if block ~ ~ ~-1 #snowbrush:air',
    'if block ~1 ~ ~ #snowbrush:air if block ~ ~ ~1 #snowbrush:air if block ~ ~ ~-1 #snowbrush:air',
    'if block ~-1 ~ ~ #snowbrush:air if block ~ ~ ~1 #snowbrush:air if block ~ ~ ~-1 #snowbrush:air'
)

$applyExisting = [System.Collections.Generic.List[string]]::new()
$applyMaterialPlain = [System.Collections.Generic.List[string]]::new()
foreach ($condition in $highNeighborTriples) {
    $applyExisting.Add("execute if block ~ ~1 ~ #snowbrush:air $condition run clone ~ ~ ~ ~ ~ ~ ~ ~1 ~ replace force")
    $applyMaterialPlain.Add(('$execute if block ~ ~1 ~ #snowbrush:air {0} run setblock ~ ~1 ~ $(id)' -f $condition))
}
foreach ($condition in $lowNeighborTriples) {
    $line = "execute if block ~ ~1 ~ #snowbrush:air $condition run setblock ~ ~ ~ minecraft:air"
    $applyExisting.Add($line)
    $applyMaterialPlain.Add($line)
}
$applyMaterialBlend = [System.Collections.Generic.List[string]]::new()
foreach ($line in $applyMaterialPlain) {
    $applyMaterialBlend.Add($line)
}
$applyMaterialBlend.Add('$execute if block ~ ~ ~ #snowbrush:replaceable if block ~ ~1 ~ #snowbrush:air run setblock ~ ~ ~ $(id)')
Write-FunctionFile (Join-Path $functionRoot 'natural\smooth\apply\existing.mcfunction') $applyExisting
Write-FunctionFile (Join-Path $functionRoot 'natural\smooth\apply\material_plain.mcfunction') $applyMaterialPlain
Write-FunctionFile (Join-Path $functionRoot 'natural\smooth\apply\material_blend.mcfunction') $applyMaterialBlend

for ($radius = 1; $radius -le $MaxRadius; $radius++) {
    # 雪球命中点本身就在地表附近；限制垂直搜索窗可避免 64 格笔刷为每列扫描 129 层。
    $scanRadius = [Math]::Min($radius + 2, 16)
    $scanExisting = [System.Collections.Generic.List[string]]::new()
    $scanMaterialPlain = [System.Collections.Generic.List[string]]::new()
    $scanMaterialBlend = [System.Collections.Generic.List[string]]::new()
    $scanExisting.Add("# 搜索落点附近最高的可自然化地表，半径 $radius")
    $scanMaterialPlain.Add("# 搜索落点附近最高的可自然化地表，半径 $radius，副手仅参与补洞")
    $scanMaterialBlend.Add("# 搜索落点附近最高的可自然化地表，半径 $radius，副手参与补洞和表层混合")
    for ($scanY = $scanRadius; $scanY -ge -$scanRadius; $scanY--) {
        $relativeY = Format-RelativeCoordinate $scanY
        $surfaceTest = "execute positioned ~ $relativeY ~ if block ~ ~ ~ #snowbrush:replaceable if block ~ ~1 ~ #snowbrush:air run return run function"
        $scanExisting.Add("$surfaceTest snowbrush:brush/natural/smooth/apply/existing")
        $scanMaterialPlain.Add("$surfaceTest snowbrush:brush/natural/smooth/apply/material_plain with entity @s item")
        $scanMaterialBlend.Add("$surfaceTest snowbrush:brush/natural/smooth/apply/material_blend with entity @s item")
    }
    Write-FunctionFile (Join-Path $functionRoot "natural\smooth\scan\existing\r$radius.mcfunction") $scanExisting
    Write-FunctionFile (Join-Path $functionRoot "natural\smooth\scan\material_plain\r$radius.mcfunction") $scanMaterialPlain
    Write-FunctionFile (Join-Path $functionRoot "natural\smooth\scan\material_blend\r$radius.mcfunction") $scanMaterialBlend

    $rowExisting = [System.Collections.Generic.List[string]]::new()
    $rowExisting.Add("function snowbrush:brush/natural/smooth/scan/existing/r$radius")
    $rowExisting.Add('scoreboard players remove @s sb_cursor 1')
    $rowExisting.Add("execute if score @s sb_cursor matches 1.. positioned ~1 ~ ~ run function snowbrush:brush/natural/smooth/row/existing/r$radius")
    Write-FunctionFile (Join-Path $functionRoot "natural\smooth\row\existing\r$radius.mcfunction") $rowExisting

    $rowMaterial = [System.Collections.Generic.List[string]]::new()
    $rowMaterial.Add('execute store result score @s sb_roll run random value 1..100')
    $rowMaterial.Add("execute if score @s sb_roll matches 1..22 run function snowbrush:brush/natural/smooth/scan/material_blend/r$radius")
    $rowMaterial.Add("execute if score @s sb_roll matches 23..100 run function snowbrush:brush/natural/smooth/scan/material_plain/r$radius")
    $rowMaterial.Add('scoreboard players remove @s sb_cursor 1')
    $rowMaterial.Add("execute if score @s sb_cursor matches 1.. positioned ~1 ~ ~ run function snowbrush:brush/natural/smooth/row/material/r$radius")
    Write-FunctionFile (Join-Path $functionRoot "natural\smooth\row\material\r$radius.mcfunction") $rowMaterial

    for ($variant = 1; $variant -le $VariantCount; $variant++) {
        $existingLines = [System.Collections.Generic.List[string]]::new()
        $materialLines = [System.Collections.Generic.List[string]]::new()
        $existingLines.Add("# 空手自然化，半径 $radius，轮廓 $variant")
        $materialLines.Add("# 副手材料参与自然化，半径 $radius，轮廓 $variant")

        for ($z = -$radius; $z -le $radius; $z++) {
            $rowXMin = $null
            $rowXMax = $null
            for ($x = -$radius; $x -le $radius; $x++) {
                $xScale = [Math]::Max(1.0, $radius * 1.06)
                $zScale = [Math]::Max(1.0, $radius * 0.98)
                $distance = [Math]::Sqrt(($x / $xScale) * ($x / $xScale) + ($z / $zScale) * ($z / $zScale))
                $noise = Get-Noise2D $x $z $variant
                $inside = $distance -le (1.0 + $noise * 0.11)
                if ($x -eq 0 -and $z -eq 0) {
                    $inside = $true
                }
                if (-not $inside) {
                    continue
                }
                if ($null -eq $rowXMin) {
                    $rowXMin = $x
                }
                $rowXMax = $x
            }
            if ($null -ne $rowXMin) {
                $relativeX = Format-RelativeCoordinate $rowXMin
                $relativeZ = Format-RelativeCoordinate $z
                $rowLength = $rowXMax - $rowXMin + 1
                $existingLines.Add("scoreboard players set @s sb_cursor $rowLength")
                $existingLines.Add("execute positioned $relativeX ~ $relativeZ run function snowbrush:brush/natural/smooth/row/existing/r$radius")
                $materialLines.Add("scoreboard players set @s sb_cursor $rowLength")
                $materialLines.Add("execute positioned $relativeX ~ $relativeZ run function snowbrush:brush/natural/smooth/row/material/r$radius")
            }
        }

        Write-FunctionFile (Join-Path $functionRoot "natural\smooth\existing\r$radius\v$variant.mcfunction") $existingLines
        Write-FunctionFile (Join-Path $functionRoot "natural\smooth\material\r$radius\v$variant.mcfunction") $materialLines

        $entryLines = [System.Collections.Generic.List[string]]::new()
        $entryLines.Add("execute if entity @s[tag=snowbrush.has_material] run function snowbrush:brush/natural/smooth/material/r$radius/v$variant")
        $entryLines.Add("execute unless entity @s[tag=snowbrush.has_material] run function snowbrush:brush/natural/smooth/existing/r$radius/v$variant")
        Write-FunctionFile (Join-Path $functionRoot "natural\replace\r$radius\v$variant.mcfunction") $entryLines
        Write-FunctionFile (Join-Path $functionRoot "natural\generate\r$radius\v$variant.mcfunction") $entryLines

        $clearLines = [System.Collections.Generic.List[string]]::new()
        $clearLines.Add("function snowbrush:brush/random/clear/r$radius/v$variant")
        Write-FunctionFile (Join-Path $functionRoot "natural\clear\r$radius\v$variant.mcfunction") $clearLines
    }
}

$sphereReplaceDispatch = [System.Collections.Generic.List[string]]::new()
$sphereGenerateDispatch = [System.Collections.Generic.List[string]]::new()
$sphereClearDispatch = [System.Collections.Generic.List[string]]::new()
$randomDispatches = @{
    replace = [System.Collections.Generic.List[string]]::new()
    generate = [System.Collections.Generic.List[string]]::new()
    clear = [System.Collections.Generic.List[string]]::new()
}
$naturalDispatches = @{
    replace = [System.Collections.Generic.List[string]]::new()
    generate = [System.Collections.Generic.List[string]]::new()
    clear = [System.Collections.Generic.List[string]]::new()
}

for ($radius = 1; $radius -le $MaxRadius; $radius++) {
    $sphereReplaceDispatch.Add("execute if score @s sb_brush matches $radius run function snowbrush:brush/replace/r$radius with entity @s item")
    $sphereGenerateDispatch.Add("execute if score @s sb_brush matches $radius run function snowbrush:brush/generate/r$radius with entity @s item")
    $sphereClearDispatch.Add("execute if score @s sb_brush matches $radius run function snowbrush:brush/clear/r$radius")
    for ($variant = 1; $variant -le $VariantCount; $variant++) {
        $randomDispatches.replace.Add("execute if score @s sb_brush matches $radius if score @s sb_variant matches $variant run function snowbrush:brush/random/replace/r$radius/v$variant with entity @s item")
        $randomDispatches.generate.Add("execute if score @s sb_brush matches $radius if score @s sb_variant matches $variant run function snowbrush:brush/random/generate/r$radius/v$variant with entity @s item")
        $randomDispatches.clear.Add("execute if score @s sb_brush matches $radius if score @s sb_variant matches $variant run function snowbrush:brush/random/clear/r$radius/v$variant")
        $naturalDispatches.replace.Add("execute if score @s sb_brush matches $radius if score @s sb_variant matches $variant run function snowbrush:brush/natural/replace/r$radius/v$variant")
        $naturalDispatches.generate.Add("execute if score @s sb_brush matches $radius if score @s sb_variant matches $variant run function snowbrush:brush/natural/generate/r$radius/v$variant")
        $naturalDispatches.clear.Add("execute if score @s sb_brush matches $radius if score @s sb_variant matches $variant run function snowbrush:brush/natural/clear/r$radius/v$variant")
    }
}

Write-FunctionFile (Join-Path $functionRoot 'dispatch_replace.mcfunction') $sphereReplaceDispatch
Write-FunctionFile (Join-Path $functionRoot 'dispatch_generate.mcfunction') $sphereGenerateDispatch
Write-FunctionFile (Join-Path $functionRoot 'dispatch_clear.mcfunction') $sphereClearDispatch
foreach ($operation in @('replace', 'generate', 'clear')) {
    Write-FunctionFile (Join-Path $functionRoot "dispatch_random_$operation.mcfunction") $randomDispatches[$operation]
    Write-FunctionFile (Join-Path $functionRoot "dispatch_natural_$operation.mcfunction") $naturalDispatches[$operation]
}

Write-Host "Generated sphere, random organic, and surface-smoothing Snowbrush functions in $functionRoot"
