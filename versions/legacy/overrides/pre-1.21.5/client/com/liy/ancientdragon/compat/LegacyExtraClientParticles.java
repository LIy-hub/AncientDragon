package com.liy.ancientdragon.compat;

import net.fabricmc.fabric.api.client.particle.v1.ParticleFactoryRegistry;
import net.minecraft.client.particle.GlowParticle;

public final class LegacyExtraClientParticles {
    private LegacyExtraClientParticles() { }
    public static void initialize() {
        ParticleFactoryRegistry.getInstance().register(LegacyExtraParticles.FIREFLY, sprites -> {
            var provider = new GlowParticle.GlowSquidProvider(sprites);
            return (type, level, x, y, z, dx, dy, dz) -> {
                var particle = provider.createParticle(type, level, x, y, z, dx, dy, dz);
                if (particle != null) particle.setColor(0.70F, 1.0F, 0.28F);
                return particle;
            };
        });
    }
}
