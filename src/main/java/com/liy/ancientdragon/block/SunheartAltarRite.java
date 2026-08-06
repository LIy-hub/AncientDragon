package com.liy.ancientdragon.block;

import com.liy.ancientdragon.AncientDragonMod;
import java.util.Optional;
import net.minecraft.resources.Identifier;

/**
 * Data-only contract for the permanent Sunheart Altar upgrades. Keeping the material economy
 * here makes the one-player corpse share explicit: twelve scales form one full armour set, and
 * ten bones form one tool or weapon.
 */
public enum SunheartAltarRite {
    SKY_WING(minecraft("elytra"), Ingredient.WING_MEMBRANE, 8, ancientDragon("sky_wing")),
    MOUNTAINFORGED_HELMET(
            minecraft("netherite_helmet"),
            Ingredient.SCALE,
            3,
            ancientDragon("mountainforged_netherite_helmet")),
    MOUNTAINFORGED_CHESTPLATE(
            minecraft("netherite_chestplate"),
            Ingredient.SCALE,
            3,
            ancientDragon("mountainforged_netherite_chestplate")),
    MOUNTAINFORGED_SKY_WING(
            ancientDragon("mountainforged_netherite_chestplate"),
            Ingredient.SKY_WING,
            1,
            ancientDragon("mountainforged_sky_wing")),
    MOUNTAINFORGED_LEGGINGS(
            minecraft("netherite_leggings"),
            Ingredient.SCALE,
            3,
            ancientDragon("mountainforged_netherite_leggings")),
    MOUNTAINFORGED_BOOTS(
            minecraft("netherite_boots"),
            Ingredient.SCALE,
            3,
            ancientDragon("mountainforged_netherite_boots")),
    DRAGONBONE_SWORD(
            minecraft("netherite_sword"),
            Ingredient.BONE,
            10,
            ancientDragon("dragonbone_netherite_sword")),
    DRAGONBONE_AXE(
            minecraft("netherite_axe"),
            Ingredient.BONE,
            10,
            ancientDragon("dragonbone_netherite_axe")),
    DRAGONBONE_PICKAXE(
            minecraft("netherite_pickaxe"),
            Ingredient.BONE,
            10,
            ancientDragon("dragonbone_netherite_pickaxe")),
    DRAGONBONE_SHOVEL(
            minecraft("netherite_shovel"),
            Ingredient.BONE,
            10,
            ancientDragon("dragonbone_netherite_shovel")),
    DRAGONBONE_HOE(
            minecraft("netherite_hoe"),
            Ingredient.BONE,
            10,
            ancientDragon("dragonbone_netherite_hoe")),
    DRAGONBONE_SPEAR(
            minecraft("netherite_spear"),
            Ingredient.BONE,
            10,
            ancientDragon("dragonbone_netherite_spear")),
    STORM_HORN(
            ancientDragon("ancient_horn"),
            Ingredient.NONE,
            0,
            ancientDragon("storm_horn"));

    private final Identifier inputId;
    private final Ingredient ingredient;
    private final int ingredientCost;
    private final Identifier outputId;

    SunheartAltarRite(
            Identifier inputId, Ingredient ingredient, int ingredientCost, Identifier outputId) {
        this.inputId = inputId;
        this.ingredient = ingredient;
        this.ingredientCost = ingredientCost;
        this.outputId = outputId;
    }

    public static Optional<SunheartAltarRite> forInputKey(String inputKey) {
        for (SunheartAltarRite rite : values()) {
            if (rite.inputId.getPath().equals(inputKey)) {
                return Optional.of(rite);
            }
        }
        return Optional.empty();
    }

    public Identifier inputId() {
        return inputId;
    }

    public Ingredient ingredient() {
        return ingredient;
    }

    public int ingredientCost() {
        return ingredientCost;
    }

    public String outputKey() {
        return outputId.getPath();
    }

    public Identifier outputId() {
        return outputId;
    }

    private static Identifier minecraft(String path) {
        return Identifier.withDefaultNamespace(path);
    }

    private static Identifier ancientDragon(String path) {
        return Identifier.fromNamespaceAndPath(AncientDragonMod.MOD_ID, path);
    }

    public enum Ingredient {
        WING_MEMBRANE(ancientDragon("ancient_wing_membrane")),
        SCALE(ancientDragon("ancient_scale")),
        BONE(ancientDragon("ancient_bone")),
        SKY_WING(ancientDragon("sky_wing")),
        NONE(minecraft("air"));

        private final Identifier itemId;

        Ingredient(Identifier itemId) {
            this.itemId = itemId;
        }

        public Identifier itemId() {
            return itemId;
        }
    }
}
