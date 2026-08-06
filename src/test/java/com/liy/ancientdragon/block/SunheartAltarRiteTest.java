package com.liy.ancientdragon.block;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.stream.Stream;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

final class SunheartAltarRiteTest {
    @ParameterizedTest
    @MethodSource("riteContracts")
    void everyRiteKeepsItsInputMaterialCostAndOutputContract(
            SunheartAltarRite expected,
            String inputId,
            SunheartAltarRite.Ingredient ingredient,
            int ingredientCost,
            String outputId) {
        Identifier parsedInputId = Identifier.parse(inputId);
        SunheartAltarRite actual = SunheartAltarRituals.riteFor(parsedInputId).orElseThrow();

        assertEquals(expected, actual);
        assertEquals(parsedInputId, actual.inputId());
        assertEquals(ingredient, actual.ingredient());
        assertEquals(ingredientCost, actual.ingredientCost());
        assertEquals(Identifier.parse(outputId), actual.outputId());
        if (ingredient == SunheartAltarRite.Ingredient.NONE) {
            assertTrue(SunheartAltarRituals.materialKindFor(ingredient.itemId()).isEmpty());
        } else {
            assertEquals(
                    ingredient,
                    SunheartAltarRituals.materialKindFor(ingredient.itemId()).orElseThrow());
        }
    }

    @Test
    void onePlayersScaleShareForgesExactlyOneCompleteArmourSet() {
        int scaleCost = 0;
        for (SunheartAltarRite rite : SunheartAltarRite.values()) {
            if (rite.ingredient() == SunheartAltarRite.Ingredient.SCALE) {
                scaleCost += rite.ingredientCost();
            }
        }
        assertEquals(12, scaleCost);
    }

    @Test
    void everySupportedNetheriteToolConsumesOnePlayersBoneShare() {
        assertEquals(10, SunheartAltarRite.DRAGONBONE_SWORD.ingredientCost());
        assertEquals(10, SunheartAltarRite.DRAGONBONE_AXE.ingredientCost());
        assertEquals(10, SunheartAltarRite.DRAGONBONE_PICKAXE.ingredientCost());
        assertEquals(10, SunheartAltarRite.DRAGONBONE_SHOVEL.ingredientCost());
        assertEquals(10, SunheartAltarRite.DRAGONBONE_HOE.ingredientCost());
        assertEquals(10, SunheartAltarRite.DRAGONBONE_SPEAR.ingredientCost());
        assertEquals(SunheartAltarRite.Ingredient.BONE, SunheartAltarRite.DRAGONBONE_SPEAR.ingredient());
    }

    @Test
    void altarRecognizesAllThirteenDeliberateRitesAndRejectsUnknownInputs() {
        assertEquals(13, SunheartAltarRite.values().length);
        assertEquals(
                SunheartAltarRite.SKY_WING,
                SunheartAltarRite.forInputKey("elytra").orElseThrow());
        assertEquals(
                SunheartAltarRite.STORM_HORN,
                SunheartAltarRite.forInputKey("ancient_horn").orElseThrow());
        assertEquals(8, SunheartAltarRite.SKY_WING.ingredientCost());
        assertEquals(0, SunheartAltarRite.STORM_HORN.ingredientCost());
        assertEquals(SunheartAltarRite.Ingredient.SKY_WING, SunheartAltarRite.MOUNTAINFORGED_SKY_WING.ingredient());
        assertEquals(1, SunheartAltarRite.MOUNTAINFORGED_SKY_WING.ingredientCost());
        assertTrue(SunheartAltarRite.forInputKey("netherite_spear").isPresent());
        assertTrue(SunheartAltarRite.forInputKey("netherite_pickaxe").isPresent());
        assertTrue(SunheartAltarRite.forInputKey("netherite_shovel").isPresent());
        assertTrue(SunheartAltarRite.forInputKey("netherite_hoe").isPresent());
        assertFalse(SunheartAltarRite.forInputKey("diamond_sword").isPresent());
    }

    private static Stream<Object[]> riteContracts() {
        return Stream.of(
                contract(
                        SunheartAltarRite.SKY_WING,
                        "minecraft:elytra",
                        SunheartAltarRite.Ingredient.WING_MEMBRANE,
                        8,
                        "ancient_dragon:sky_wing"),
                contract(
                        SunheartAltarRite.MOUNTAINFORGED_HELMET,
                        "minecraft:netherite_helmet",
                        SunheartAltarRite.Ingredient.SCALE,
                        3,
                        "ancient_dragon:mountainforged_netherite_helmet"),
                contract(
                        SunheartAltarRite.MOUNTAINFORGED_CHESTPLATE,
                        "minecraft:netherite_chestplate",
                        SunheartAltarRite.Ingredient.SCALE,
                        3,
                        "ancient_dragon:mountainforged_netherite_chestplate"),
                contract(
                        SunheartAltarRite.MOUNTAINFORGED_SKY_WING,
                        "ancient_dragon:mountainforged_netherite_chestplate",
                        SunheartAltarRite.Ingredient.SKY_WING,
                        1,
                        "ancient_dragon:mountainforged_sky_wing"),
                contract(
                        SunheartAltarRite.MOUNTAINFORGED_LEGGINGS,
                        "minecraft:netherite_leggings",
                        SunheartAltarRite.Ingredient.SCALE,
                        3,
                        "ancient_dragon:mountainforged_netherite_leggings"),
                contract(
                        SunheartAltarRite.MOUNTAINFORGED_BOOTS,
                        "minecraft:netherite_boots",
                        SunheartAltarRite.Ingredient.SCALE,
                        3,
                        "ancient_dragon:mountainforged_netherite_boots"),
                contract(
                        SunheartAltarRite.DRAGONBONE_SWORD,
                        "minecraft:netherite_sword",
                        SunheartAltarRite.Ingredient.BONE,
                        10,
                        "ancient_dragon:dragonbone_netherite_sword"),
                contract(
                        SunheartAltarRite.DRAGONBONE_AXE,
                        "minecraft:netherite_axe",
                        SunheartAltarRite.Ingredient.BONE,
                        10,
                        "ancient_dragon:dragonbone_netherite_axe"),
                contract(
                        SunheartAltarRite.DRAGONBONE_PICKAXE,
                        "minecraft:netherite_pickaxe",
                        SunheartAltarRite.Ingredient.BONE,
                        10,
                        "ancient_dragon:dragonbone_netherite_pickaxe"),
                contract(
                        SunheartAltarRite.DRAGONBONE_SHOVEL,
                        "minecraft:netherite_shovel",
                        SunheartAltarRite.Ingredient.BONE,
                        10,
                        "ancient_dragon:dragonbone_netherite_shovel"),
                contract(
                        SunheartAltarRite.DRAGONBONE_HOE,
                        "minecraft:netherite_hoe",
                        SunheartAltarRite.Ingredient.BONE,
                        10,
                        "ancient_dragon:dragonbone_netherite_hoe"),
                contract(
                        SunheartAltarRite.DRAGONBONE_SPEAR,
                        "minecraft:netherite_spear",
                        SunheartAltarRite.Ingredient.BONE,
                        10,
                        "ancient_dragon:dragonbone_netherite_spear"),
                contract(
                        SunheartAltarRite.STORM_HORN,
                        "ancient_dragon:ancient_horn",
                        SunheartAltarRite.Ingredient.NONE,
                        0,
                        "ancient_dragon:storm_horn"));
    }

    private static Object[] contract(
            SunheartAltarRite rite,
            String inputKey,
            SunheartAltarRite.Ingredient ingredient,
            int ingredientCost,
            String outputKey) {
        return new Object[] {rite, inputKey, ingredient, ingredientCost, outputKey};
    }
}
