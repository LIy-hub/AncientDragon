execute if entity @s[tag=snowbrush.replace] run function snowbrush:brush/dispatch_mode_replace
execute if entity @s[tag=snowbrush.generate] run function snowbrush:brush/dispatch_mode_generate
execute if entity @s[tag=snowbrush.clear] run function snowbrush:brush/dispatch_mode_clear
particle minecraft:poof ~ ~ ~ 0.35 0.35 0.35 0.05 12 force
playsound minecraft:block.stone.place master @a[distance=..32] ~ ~ ~ 0.6 0.8
kill @s
