package com.liy.ancientdragon.worldgen;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.liy.ancientdragon.atmosphere.SacredMountainNetherZone;
import org.junit.jupiter.api.Test;

final class SacredMountainParticlePaletteTest {
    @Test
    void paletteCoversADeepAmbientVanillaSelectionWithoutCombatLies() {
        assertTrue(SacredMountainParticlePalette.allParticleKinds().size() >= 60);
        assertTrue(SacredMountainParticlePalette.divineParticles()
                .contains(SacredMountainParticlePalette.ParticleKind.END_ROD));
        assertTrue(SacredMountainParticlePalette.divineParticles()
                .contains(SacredMountainParticlePalette.ParticleKind.REVERSE_PORTAL));
        assertTrue(SacredMountainParticlePalette.rareManifestations()
                .contains(SacredMountainParticlePalette.ParticleKind.TOTEM_OF_UNDYING));
        assertTrue(SacredMountainParticlePalette.allParticleKinds().stream().noneMatch(kind ->
                kind.name().contains("DAMAGE")
                        || kind.name().equals("HEART")
                        || kind.name().equals("NOTE")
                        || kind.name().contains("EXPLOSION")));
    }

    @Test
    void eachNetherMemoryHasItsOwnFullPoolAndAllLanesResolve() {
        for (SacredMountainNetherZone zone : SacredMountainNetherZone.values()) {
            assertTrue(SacredMountainParticlePalette.particlesForZone(zone).size() >= 10, zone::name);
            for (int lane = 0; lane < 100; lane++) {
                assertNotNull(SacredMountainParticlePalette.pick(zone, lane, lane * 37));
            }
        }
    }
}
