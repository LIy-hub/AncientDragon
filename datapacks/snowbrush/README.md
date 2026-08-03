# Snowbrush 雪球地形笔刷

适用于 Minecraft Java 版 26.1.2（数据包格式 101.1）。

## 使用方法

1. 替换/生成的球形和随机形状需要副手方块；清空和自然化可以空手使用。
2. 主手投掷普通雪球。
3. 雪球落地时，以落点为中心执行当前操作和形状。

玩家指令（不需要 OP）：

```mcfunction
/trigger snow_mode set 1
/trigger snow_mode set 2
/trigger snow_mode set 3
/trigger snow_size set 5
/trigger snow_shape set 1
/trigger snow_shape set 2
/trigger snow_shape set 3
```

- `snow_mode set 1`：替换模式，只替换自然地形方块。
- `snow_mode set 2`：生成模式，只填充空气，不破坏已有方块。
- `snow_mode set 3`：清空模式，把当前形状覆盖范围变为空气。
- `snow_size set 1..64`：设置笔刷半径，默认值为 3。25～64 格可能一次修改数十万至上百万方块，请勿连续快速投掷。
- `snow_shape set 1`：精确球形。
- `snow_shape set 2`：随机有机形，每次投掷随机轮廓并上下浮动一格。
- `snow_shape set 3`：自然化平滑。它会读取落点附近现有地表，补掉被三侧高地包围的小凹坑，并削掉被三侧低地包围的突刺。空手时沿用原地表方块；副手有方块时，该方块参与补洞和少量表层混合。连续投掷可逐步柔化更大的落差。

自然化形状在替换模式和生成模式下执行同一套平滑规则；清空模式下仍使用随机有机轮廓清除方块。

管理员可以执行 `/function snowbrush:help` 再次显示帮助；卸载前执行
`/function snowbrush:uninstall` 可以清理记分板和残留追踪实体。

设置和副手材料在雪球投出的瞬间锁定。副手必须是可放置的完整方块；物品、
桶和部分依赖支撑的特殊方块不适合作为笔刷材料。自然化不是矿脉生成，也不会
强制把整片地形换成副手材质。清空模式具有破坏性，使用前
请确认形状和半径。半径较大时请避免连续快速投掷。
