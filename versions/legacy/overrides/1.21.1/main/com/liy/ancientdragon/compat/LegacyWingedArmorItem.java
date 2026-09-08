package com.liy.ancientdragon.compat;

import net.fabricmc.fabric.api.entity.event.v1.FabricElytraItem;
import net.minecraft.world.item.ArmorItem;

/** Keeps native armor damage/equipping/rendering and adds the official Fabric glider lifecycle. */
public final class LegacyWingedArmorItem extends ArmorItem implements FabricElytraItem {
    public LegacyWingedArmorItem(Properties properties) { super(LegacyArmorMaterial.MOUNTAINFORGED, Type.CHESTPLATE, properties); }
}
