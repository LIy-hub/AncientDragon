scoreboard players operation @s sb_size = @s snow_size
scoreboard players reset @s snow_size
tellraw @s [{"text":"[雪球笔刷] ","color":"aqua"},{"text":"球形笔刷半径已设为 ","color":"white"},{"score":{"name":"@s","objective":"sb_size"},"color":"yellow"},{"text":"。","color":"white"}]
execute if score @s sb_size matches 25..64 run tellraw @s [{"text":"[雪球笔刷] ","color":"gold"},{"text":"大范围警告：该半径单次可能修改数十万至上百万方块，请勿连续快速投掷。","color":"yellow"}]
