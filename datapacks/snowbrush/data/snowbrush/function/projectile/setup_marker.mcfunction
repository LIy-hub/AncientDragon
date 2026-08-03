tag @s add snowbrush.tracker
execute if items entity @a[tag=snowbrush.current_owner,sort=nearest,limit=1] weapon.offhand * run item replace entity @s contents from entity @a[tag=snowbrush.current_owner,sort=nearest,limit=1] weapon.offhand
execute if items entity @a[tag=snowbrush.current_owner,sort=nearest,limit=1] weapon.offhand * run tag @s add snowbrush.has_material
scoreboard players operation @s sb_brush = @a[tag=snowbrush.current_owner,sort=nearest,limit=1] sb_size
scoreboard players operation @s sb_shape = @a[tag=snowbrush.current_owner,sort=nearest,limit=1] sb_shape
execute store result score @s sb_variant run random value 1..4
execute store result score @s sb_yoff run random value -1..1
execute if score @a[tag=snowbrush.current_owner,sort=nearest,limit=1] sb_mode matches 2 run tag @s add snowbrush.generate
execute if score @a[tag=snowbrush.current_owner,sort=nearest,limit=1] sb_mode matches 3 run tag @s add snowbrush.clear
execute if score @a[tag=snowbrush.current_owner,sort=nearest,limit=1] sb_mode matches 1 run tag @s add snowbrush.replace
ride @s mount @e[type=minecraft:snowball,tag=snowbrush.registering,sort=nearest,limit=1,distance=..2]
