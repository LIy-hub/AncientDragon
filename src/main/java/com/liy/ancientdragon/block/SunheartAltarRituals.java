package com.liy.ancientdragon.block;

import java.util.Arrays;
import java.util.Optional;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Shared lookup for the inputs, materials and outputs accepted by the Sunheart Altar menu. */
public final class SunheartAltarRituals {
    private SunheartAltarRituals() {
    }

    public static Optional<SunheartAltarRite> riteFor(ItemStack stack) {
        return riteFor(BuiltInRegistries.ITEM.getKey(stack.getItem()));
    }

    static Optional<SunheartAltarRite> riteFor(Identifier itemId) {
        return Arrays.stream(SunheartAltarRite.values())
                .filter(rite -> rite.inputId().equals(itemId))
                .findFirst();
    }

    public static boolean isRitualTarget(ItemStack stack) {
        return riteFor(stack).isPresent();
    }

    public static boolean isRitualMaterial(ItemStack stack) {
        return materialKindFor(stack).isPresent();
    }

    public static Optional<SunheartAltarRite.Ingredient> materialKindFor(ItemStack stack) {
        return materialKindFor(BuiltInRegistries.ITEM.getKey(stack.getItem()));
    }

    static Optional<SunheartAltarRite.Ingredient> materialKindFor(Identifier itemId) {
        return Arrays.stream(SunheartAltarRite.Ingredient.values())
                .filter(ingredient -> ingredient != SunheartAltarRite.Ingredient.NONE)
                .filter(ingredient -> ingredient.itemId().equals(itemId))
                .findFirst();
    }

    public static Item outputFor(SunheartAltarRite rite) {
        return BuiltInRegistries.ITEM
                .getOptional(rite.outputId())
                .orElseThrow(() -> new IllegalStateException(
                        "Missing Sunheart Altar output item " + rite.outputId()));
    }

}
