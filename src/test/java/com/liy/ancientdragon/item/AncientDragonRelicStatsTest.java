package com.liy.ancientdragon.item;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

final class AncientDragonRelicStatsTest {
    @Test
    void userApprovedValuesIncreaseDurabilityByOneHundredAndTwentyFivePercent() {
        assertEquals(2.25D, AncientDragonRelicStats.DURABILITY_MULTIPLIER);
        assertEquals(225, AncientDragonRelicStats.upgradedDurability(100));
    }

    @Test
    void mountainAndDragonboneInheritanceUseTheApprovedCombatBonuses() {
        assertEquals(2.0D, AncientDragonRelicStats.MOUNTAINFORGED_ARMOUR_TOUGHNESS_BONUS);
        assertEquals(0.25D, AncientDragonRelicStats.MOUNTAINFORGED_KNOCKBACK_RESISTANCE_BONUS);
        assertEquals(5.0D, AncientDragonRelicStats.DRAGONBONE_WEAPON_DAMAGE_BONUS);
    }
}
