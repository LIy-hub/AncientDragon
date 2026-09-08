package com.liy.ancientdragon.compat;

import net.fabricmc.fabric.api.client.particle.v1.ParticleFactoryRegistry;
import net.minecraft.client.particle.FlameParticle;

public final class LegacyClientParticles {
    private LegacyClientParticles() { }
    public static void initialize() {
        ParticleFactoryRegistry.getInstance().register(LegacyParticles.COPPER_FIRE_FLAME, sprites -> {
            var provider = new FlameParticle.Provider(sprites);
            return (type, level, x, y, z, dx, dy, dz) -> {
                var flame = provider.createParticle(type, level, x, y, z, dx, dy, dz);
                if (flame != null) flame.setColor(0.20F, 1.0F, 0.66F);
                return flame;
            };
        });
    }
}
