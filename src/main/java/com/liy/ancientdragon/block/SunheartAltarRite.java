package com.liy.ancientdragon.block;

import java.util.Optional;

/**
 * Data-only contract for the permanent Sunheart Altar upgrades. Keeping the material economy
 * here makes the one-player corpse share explicit: twelve scales form one full armour set, and
 * ten bones form one tool or weapon.
 */
public enum SunheartAltarRite {
    SKY_WING("elytra", Ingredient.WING_MEMBRANE, 8, "sky_wing"),
    MOUNTAINFORGED_HELMET("netherite_helmet", Ingredient.SCALE, 3, "mountainforged_netherite_helmet"),
    MOUNTAINFORGED_CHESTPLATE("netherite_chestplate", Ingredient.SCALE, 3, "mountainforged_netherite_chestplate"),
    MOUNTAINFORGED_SKY_WING(
            "mountainforged_netherite_chestplate", Ingredient.SKY_WING, 1, "mountainforged_sky_wing"),
    MOUNTAINFORGED_LEGGINGS("netherite_leggings", Ingredient.SCALE, 3, "mountainforged_netherite_leggings"),
    MOUNTAINFORGED_BOOTS("netherite_boots", Ingredient.SCALE, 3, "mountainforged_netherite_boots"),
    DRAGONBONE_SWORD("netherite_sword", Ingredient.BONE, 10, "dragonbone_netherite_sword"),
    DRAGONBONE_AXE("netherite_axe", Ingredient.BONE, 10, "dragonbone_netherite_axe"),
    DRAGONBONE_PICKAXE("netherite_pickaxe", Ingredient.BONE, 10, "dragonbone_netherite_pickaxe"),
    DRAGONBONE_SHOVEL("netherite_shovel", Ingredient.BONE, 10, "dragonbone_netherite_shovel"),
    DRAGONBONE_HOE("netherite_hoe", Ingredient.BONE, 10, "dragonbone_netherite_hoe"),
    DRAGONBONE_SPEAR("netherite_spear", Ingredient.BONE, 10, "dragonbone_netherite_spear"),
    STORM_HORN("ancient_horn", Ingredient.NONE, 0, "storm_horn");

    private final String inputKey;
    private final Ingredient ingredient;
    private final int ingredientCost;
    private final String outputKey;

    SunheartAltarRite(String inputKey, Ingredient ingredient, int ingredientCost, String outputKey) {
        this.inputKey = inputKey;
        this.ingredient = ingredient;
        this.ingredientCost = ingredientCost;
        this.outputKey = outputKey;
    }

    public static Optional<SunheartAltarRite> forInputKey(String inputKey) {
        for (SunheartAltarRite rite : values()) {
            if (rite.inputKey.equals(inputKey)) {
                return Optional.of(rite);
            }
        }
        return Optional.empty();
    }

    public Ingredient ingredient() {
        return ingredient;
    }

    public int ingredientCost() {
        return ingredientCost;
    }

    public String outputKey() {
        return outputKey;
    }

    public enum Ingredient {
        WING_MEMBRANE,
        SCALE,
        BONE,
        SKY_WING,
        NONE
    }
}
