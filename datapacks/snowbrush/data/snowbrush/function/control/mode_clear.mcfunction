scoreboard players set @s sb_mode 3
scoreboard players reset @s snow_mode
tellraw @s [{"text":"[雪球笔刷] ","color":"aqua"},{"text":"已切换为清空模式：落点范围将被清除，请谨慎使用。","color":"red"}]
