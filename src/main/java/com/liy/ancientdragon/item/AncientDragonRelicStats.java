package com.liy.ancientdragon.item;

/** Final player-approved combat values for the Ancient Dragon material inheritances. */
public final class AncientDragonRelicStats {
    public static final double DURABILITY_MULTIPLIER = 2.25D;
    public static final double MOUNTAINFORGED_ARMOUR_TOUGHNESS_BONUS = 2.0D;
    public static final double MOUNTAINFORGED_KNOCKBACK_RESISTANCE_BONUS = 0.25D;
    public static final double DRAGONBONE_WEAPON_DAMAGE_BONUS = 5.0D;

    private AncientDragonRelicStats() {
    }

    public static int upgradedDurability(int vanillaDurability) {
        return Math.round((float) (vanillaDurability * DURABILITY_MULTIPLIER));
    }
}
