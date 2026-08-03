package com.liy.ancientdragon.block;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class SunheartAltarRiteTest {
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
}
