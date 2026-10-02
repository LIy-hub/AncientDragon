package com.liy.ancientdragon;

import java.util.Arrays;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Bootstraps registries and verifies the production client hook without starting a world. */
class Minecraft263BootstrapTest {
    @Test void cameraMixinAndWorldgenCodecsAreAvailable() throws Exception {
        assertTrue(FabricLoader.getInstance().isModLoaded("ancient_dragon"));
        assertTrue(FabricLoader.getInstance().isModLoaded("blendlib"));
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        new AncientDragonMod().onInitialize();
        try (var input = getClass().getResourceAsStream("/data/ancient_dragon/worldgen/structure_set/sacred_mountain.json")) {
            assertNotNull(input);
            var json = com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(input, java.nio.charset.StandardCharsets.UTF_8));
            var placement = net.minecraft.world.level.levelgen.structure.placement.StructurePlacement.CODEC
                    .parse(com.mojang.serialization.JsonOps.INSTANCE, json.getAsJsonObject().get("placement")).getOrThrow();
            assertInstanceOf(com.liy.ancientdragon.worldgen.SacredMountainPlacement.class, placement);
        }
        Class<?> camera = Class.forName("net.minecraft.client.Camera");
        assertTrue(Arrays.stream(camera.getDeclaredMethods())
                .anyMatch(method -> method.getName().contains("ancientDragon$")),
                "The production camera mixin must be applied");
        assertNotNull(com.liy.ancientdragon.worldgen.SacredMountainPlacement.CODEC);
    }
}
