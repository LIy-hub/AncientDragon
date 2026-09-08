package com.liy.ancientdragon.compat;

import net.fabricmc.fabric.api.entity.event.v1.FabricElytraItem;
import net.minecraft.world.item.ElytraItem;

/** Fabric's native elytra hook retains gliding on the pre-GLIDER-component game. */
public final class LegacySkyWingItem extends ElytraItem implements FabricElytraItem {
    public LegacySkyWingItem(Properties properties) { super(properties); }
}
