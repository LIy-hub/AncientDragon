package com.liy.ancientdragon.compat;

import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;

public final class LegacyLeafParticles {
    public static final SimpleParticleType PALE_OAK_LEAVES = Registry.register(BuiltInRegistries.PARTICLE_TYPE,
            ResourceLocation.fromNamespaceAndPath("ancient_dragon", "pale_leaves"), FabricParticleTypes.simple());
    private LegacyLeafParticles() { }
    public static void initialize() { }
}
