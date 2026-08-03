tag @s add snowbrush.registering
tag @s add snowbrush.tracked
execute on origin if entity @s[type=minecraft:player] run function snowbrush:projectile/register_owner
tag @s remove snowbrush.registering
