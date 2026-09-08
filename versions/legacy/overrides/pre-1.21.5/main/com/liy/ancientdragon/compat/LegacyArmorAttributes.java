package com.liy.ancientdragon.compat;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.equipment.ArmorType;

/** Reads the native netherite baseline; the original relic bonuses are added by the caller. */
public final class LegacyArmorAttributes {
    private LegacyArmorAttributes() { }
    public static ItemAttributeModifiers netherite(ArmorType type) {
        var item = switch (type) {
            case HELMET -> Items.NETHERITE_HELMET;
            case CHESTPLATE -> Items.NETHERITE_CHESTPLATE;
            case LEGGINGS -> Items.NETHERITE_LEGGINGS;
            case BOOTS -> Items.NETHERITE_BOOTS;
            default -> throw new IllegalArgumentException("Unsupported relic armor type: " + type);
        };
        return item.getDefaultInstance().getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
    }
}
