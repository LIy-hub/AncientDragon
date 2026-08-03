param(
    [string]$PackRoot = (Join-Path $PSScriptRoot '..\datapacks\snowbrush'),
    [ValidateRange(1, 64)]
    [int]$MaxRadius = 64
)

$resolvedPackRoot = [System.IO.Path]::GetFullPath($PackRoot)
$functionRoot = Join-Path $resolvedPackRoot 'data\snowbrush\function\brush'
$replaceRoot = Join-Path $functionRoot 'replace'
$generateRoot = Join-Path $functionRoot 'generate'
$clearRoot = Join-Path $functionRoot 'clear'

[System.IO.Directory]::CreateDirectory($replaceRoot) | Out-Null
[System.IO.Directory]::CreateDirectory($generateRoot) | Out-Null
[System.IO.Directory]::CreateDirectory($clearRoot) | Out-Null
$utf8NoBom = [System.Text.UTF8Encoding]::new($false)

function Format-RelativeCoordinate([int]$Value) {
    if ($Value -eq 0) {
        return '~'
    }
    return "~$Value"
}

for ($radius = 1; $radius -le $MaxRadius; $radius++) {
    $replaceLines = [System.Collections.Generic.List[string]]::new()
    $generateLines = [System.Collections.Generic.List[string]]::new()
    $clearLines = [System.Collections.Generic.List[string]]::new()
    $replaceLines.Add("# 球形替换笔刷，半径 $radius")
    $generateLines.Add("# 球形生成笔刷，半径 $radius")
    $clearLines.Add("# 球形清空笔刷，半径 $radius")

    $quantizeStep = if ($radius -le 16) { 1 } else { [Math]::Max(1, [int][Math]::Floor($radius / 12.0)) }
    for ($y = -$radius; $y -le $radius; $y++) {
        $remainingAfterY = ($radius * $radius) - ($y * $y)
        $zLimit = [int][Math]::Floor([Math]::Sqrt($remainingAfterY))
        $groupStartZ = $null
        $groupXLimit = $null
        for ($z = -$zLimit; $z -le $zLimit; $z++) {
            $remaining = $remainingAfterY - ($z * $z)
            $rawXLimit = [int][Math]::Floor([Math]::Sqrt($remaining))
            $xLimit = if ($quantizeStep -eq 1) { $rawXLimit } else { [int][Math]::Floor($rawXLimit / $quantizeStep) * $quantizeStep }
            if ($null -eq $groupStartZ) {
                $groupStartZ = $z
                $groupXLimit = $xLimit
            }
            $isLast = $z -eq $zLimit
            if ($xLimit -ne $groupXLimit -or $isLast) {
                $groupEndZ = if ($xLimit -ne $groupXLimit) { $z - 1 } else { $z }
                $xMin = Format-RelativeCoordinate (-$groupXLimit)
                $xMax = Format-RelativeCoordinate $groupXLimit
                $relativeY = Format-RelativeCoordinate $y
                $relativeZMin = Format-RelativeCoordinate $groupStartZ
                $relativeZMax = Format-RelativeCoordinate $groupEndZ

                $replaceLines.Add(('$fill {0} {1} {2} {3} {1} {4} $(id) replace #snowbrush:replaceable' -f $xMin, $relativeY, $relativeZMin, $xMax, $relativeZMax))
                $generateLines.Add(('$fill {0} {1} {2} {3} {1} {4} $(id) replace minecraft:air' -f $xMin, $relativeY, $relativeZMin, $xMax, $relativeZMax))
                $generateLines.Add(('$fill {0} {1} {2} {3} {1} {4} $(id) replace minecraft:cave_air' -f $xMin, $relativeY, $relativeZMin, $xMax, $relativeZMax))
                $clearLines.Add(('fill {0} {1} {2} {3} {1} {4} minecraft:air' -f $xMin, $relativeY, $relativeZMin, $xMax, $relativeZMax))

                if ($xLimit -ne $groupXLimit) {
                    $groupStartZ = $z
                    $groupXLimit = $xLimit
                    if ($isLast) {
                        $xMin = Format-RelativeCoordinate (-$groupXLimit)
                        $xMax = Format-RelativeCoordinate $groupXLimit
                        $relativeZ = Format-RelativeCoordinate $z
                        $replaceLines.Add(('$fill {0} {1} {2} {3} {1} {2} $(id) replace #snowbrush:replaceable' -f $xMin, $relativeY, $relativeZ, $xMax))
                        $generateLines.Add(('$fill {0} {1} {2} {3} {1} {2} $(id) replace minecraft:air' -f $xMin, $relativeY, $relativeZ, $xMax))
                        $generateLines.Add(('$fill {0} {1} {2} {3} {1} {2} $(id) replace minecraft:cave_air' -f $xMin, $relativeY, $relativeZ, $xMax))
                        $clearLines.Add(('fill {0} {1} {2} {3} {1} {2} minecraft:air' -f $xMin, $relativeY, $relativeZ, $xMax))
                    }
                }
            }
        }
    }

    [System.IO.File]::WriteAllLines((Join-Path $replaceRoot "r$radius.mcfunction"), $replaceLines, $utf8NoBom)
    [System.IO.File]::WriteAllLines((Join-Path $generateRoot "r$radius.mcfunction"), $generateLines, $utf8NoBom)
    [System.IO.File]::WriteAllLines((Join-Path $clearRoot "r$radius.mcfunction"), $clearLines, $utf8NoBom)
}

Write-Host "Generated spherical Snowbrush functions for radii 1-$MaxRadius in $functionRoot"
