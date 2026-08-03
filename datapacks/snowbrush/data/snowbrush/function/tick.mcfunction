# 初始化玩家并处理 trigger 控制
execute as @a unless score @s sb_init matches 1 run function snowbrush:player/init
execute as @a unless score @s sb_shape matches 1..3 run scoreboard players set @s sb_shape 1
scoreboard players enable @a snow_mode
scoreboard players enable @a snow_size
scoreboard players enable @a snow_shape
execute as @a[scores={snow_mode=1}] run function snowbrush:control/mode_replace
execute as @a[scores={snow_mode=2}] run function snowbrush:control/mode_generate
execute as @a[scores={snow_mode=3}] run function snowbrush:control/mode_clear
execute as @a[scores={snow_mode=..-1}] run function snowbrush:control/invalid_mode
execute as @a[scores={snow_mode=4..}] run function snowbrush:control/invalid_mode
execute as @a[scores={snow_size=1..64}] run function snowbrush:control/set_size
execute as @a[scores={snow_size=..-1}] run function snowbrush:control/invalid_size
execute as @a[scores={snow_size=65..}] run function snowbrush:control/invalid_size
execute as @a[scores={snow_shape=1}] run function snowbrush:control/shape_sphere
execute as @a[scores={snow_shape=2}] run function snowbrush:control/shape_random
execute as @a[scores={snow_shape=3}] run function snowbrush:control/shape_natural
execute as @a[scores={snow_shape=..-1}] run function snowbrush:control/invalid_shape
execute as @a[scores={snow_shape=4..}] run function snowbrush:control/invalid_shape

# 为新雪球绑定一个不会随雪球一起消失的 item_display 乘客
execute as @e[type=minecraft:snowball,tag=!snowbrush.tracked] at @s run function snowbrush:projectile/register

# 仍然骑在雪球上的追踪实体会得到 mounted 标签；失去载具即视为命中
tag @e[type=minecraft:item_display,tag=snowbrush.tracker] remove snowbrush.mounted
execute as @e[type=minecraft:snowball,tag=snowbrush.tracked] on passengers if entity @s[type=minecraft:item_display,tag=snowbrush.tracker] run tag @s add snowbrush.mounted
execute as @e[type=minecraft:item_display,tag=snowbrush.tracker,tag=!snowbrush.mounted] at @s run function snowbrush:projectile/impact
