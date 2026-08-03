scoreboard players set @s sb_init 1
scoreboard players set @s sb_mode 1
scoreboard players set @s sb_size 3
scoreboard players set @s sb_shape 1
tellraw @s [{"text":"[雪球笔刷] ","color":"aqua"},{"text":"已启用：替换模式、球形、半径 3。副手拿方块后投雪球。","color":"white"}]
tellraw @s [{"text":"操作：","color":"gray"},{"text":" /trigger snow_mode set 1","color":"green"},{"text":"（替换）","color":"gray"},{"text":" 2","color":"gold"},{"text":"（生成）","color":"gray"},{"text":" 3","color":"red"},{"text":"（清空）","color":"gray"}]
tellraw @s [{"text":"大小：","color":"gray"},{"text":" /trigger snow_size set 1..64","color":"yellow"}]
tellraw @s [{"text":"形状：","color":"gray"},{"text":" /trigger snow_shape set 1","color":"aqua"},{"text":"（球形）","color":"gray"},{"text":" 2","color":"light_purple"},{"text":"（随机）","color":"gray"},{"text":" 3","color":"green"},{"text":"（自然）","color":"gray"}]
