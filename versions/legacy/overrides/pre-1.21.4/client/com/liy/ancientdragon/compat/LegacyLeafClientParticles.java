package com.liy.ancientdragon.compat;

import net.fabricmc.fabric.api.client.particle.v1.ParticleFactoryRegistry;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.CherryParticle;
import net.minecraft.client.particle.SpriteSet;

/** Native falling-leaf motion with an original pale leaf mask for games without pale oak. */
public final class LegacyLeafClientParticles {
    private LegacyLeafClientParticles() { }
    public static void initialize() {
        ParticleFactoryRegistry.getInstance().register(LegacyLeafParticles.PALE_OAK_LEAVES,
                sprites -> (type, level, x, y, z, dx, dy, dz) -> new PaleLeaf(level, x, y, z, sprites));
    }
    private static final class PaleLeaf extends CherryParticle {
        private PaleLeaf(ClientLevel level, double x, double y, double z, SpriteSet sprites) {
            super(level, x, y, z, sprites);
            setColor(0.90F, 0.92F, 0.88F);
        }
    }
}
