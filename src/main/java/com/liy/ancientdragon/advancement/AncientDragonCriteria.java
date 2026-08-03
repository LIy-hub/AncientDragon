package com.liy.ancientdragon.advancement;

import com.liy.ancientdragon.AncientDragonMod;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;

/** Advancement trigger registration. */
public final class AncientDragonCriteria {
    public static final AncientDragonVictoryTrigger VICTORY = Registry.register(
            BuiltInRegistries.TRIGGER_TYPES,
            Identifier.fromNamespaceAndPath(AncientDragonMod.MOD_ID, "victory"),
            new AncientDragonVictoryTrigger());

    private AncientDragonCriteria() {
    }

    public static void initialize() {
        // Class loading performs registration.
    }
}
