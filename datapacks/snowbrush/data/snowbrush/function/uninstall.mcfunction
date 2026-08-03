kill @e[type=minecraft:item_display,tag=snowbrush.tracker]
scoreboard objectives remove snow_mode
scoreboard objectives remove snow_size
scoreboard objectives remove sb_init
scoreboard objectives remove sb_mode
scoreboard objectives remove sb_size
scoreboard objectives remove sb_brush
scoreboard objectives remove sb_shape
scoreboard objectives remove sb_variant
scoreboard objectives remove sb_yoff
scoreboard objectives remove sb_cursor
scoreboard objectives remove sb_roll
scoreboard objectives remove snow_shape
tellraw @a [{"text":"[雪球笔刷] 数据已清理。现在可以移除数据包并执行 /reload。","color":"yellow"}]
