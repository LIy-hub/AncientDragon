execute if score @s sb_mode matches 3 run function snowbrush:projectile/register_valid
execute unless score @s sb_mode matches 3 if score @s sb_shape matches 3 run function snowbrush:projectile/register_valid
execute unless score @s sb_mode matches 3 unless score @s sb_shape matches 3 if items entity @s weapon.offhand * run function snowbrush:projectile/register_valid
execute unless score @s sb_mode matches 3 unless score @s sb_shape matches 3 unless items entity @s weapon.offhand * run tellraw @s [{"text":"[雪球笔刷] ","color":"red"},{"text":"副手没有方块；只有自然化形状可以空手使用。","color":"white"}]
