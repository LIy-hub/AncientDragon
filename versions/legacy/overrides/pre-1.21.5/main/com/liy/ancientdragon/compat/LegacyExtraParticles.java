package com.liy.ancientdragon.compat;

import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;

/** Firefly ambience on versions predating the native firefly particle. */
public final class LegacyExtraParticles {
    public static final SimpleParticleType FIREFLY = Registry.register(BuiltInRegistries.PARTICLE_TYPE,
            ResourceLocation.fromNamespaceAndPath("ancient_dragon", "firefly"), FabricParticleTypes.simple());
    private LegacyExtraParticles() { }
    public static void initialize() { }
}
