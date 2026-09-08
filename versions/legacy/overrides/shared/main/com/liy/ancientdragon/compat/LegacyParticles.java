package com.liy.ancientdragon.compat;

import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;

/** Copper-flame ambience for game versions predating the vanilla particle. */
public final class LegacyParticles {
    public static final SimpleParticleType COPPER_FIRE_FLAME = Registry.register(BuiltInRegistries.PARTICLE_TYPE,
            ResourceLocation.fromNamespaceAndPath("ancient_dragon", "copper_fire_flame"), FabricParticleTypes.simple());
    private LegacyParticles() { }
    public static void initialize() { }
}
