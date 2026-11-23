package com.liy.ancientdragon;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Set;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.SharedConstants;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementType;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Bootstraps registries and verifies the production client hook without starting a world. */
class Minecraft263BootstrapTest {
    @BeforeAll static void bootstrapProductionRegistries() {
        assertTrue(FabricLoader.getInstance().isModLoaded("ancient_dragon"));
        assertTrue(FabricLoader.getInstance().isModLoaded("blendlib"));
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        new AncientDragonMod().onInitialize();
    }

    @Test void cameraMixinAndWorldgenCodecsAreAvailable() throws Exception {
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

    @Test void victoryAdvancementDecodesWithoutChangingItsRewardsOrCriterion() throws Exception {
        var json = victoryAdvancement();
        var ops = RegistryOps.create(JsonOps.INSTANCE, RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY));
        var advancement = Advancement.CODEC.parse(ops, json).getOrThrow();
        assertTrue(advancement.isRoot());
        assertEquals(Set.of("victory"), advancement.criteria().keySet());
        assertSame(com.liy.ancientdragon.advancement.AncientDragonCriteria.VICTORY,
                advancement.criteria().get("victory").trigger());
        assertEquals(500, advancement.rewards().experience());
        assertEquals(Set.of("victory"), advancement.requirements().names());
        var display = advancement.display().orElseThrow();
        assertEquals(AdvancementType.CHALLENGE, display.type());
        assertTrue(display.showToast());
        assertTrue(display.announceToChat());
        assertFalse(display.hidden());
        var background = display.background().orElseThrow();
        assertEquals("minecraft:gui/advancements/backgrounds/end", background.id().toString());
        try (var texture = getClass().getResourceAsStream("/assets/minecraft/" + background.texturePath().getPath())) {
            assertNotNull(texture, "The declared background must exist in the official 26.3 client assets");
        }
        json.getAsJsonObject("display").remove("background");
        try (var original = Files.newBufferedReader(Path.of("src/main/resources/data/ancient_dragon/advancement/defeat_ancient_dragon.json"))) {
            assertEquals(JsonParser.parseReader(original), json, "Only the required background may change");
        }
    }

    @Test void officialCodecRejectsThePreviousMissingBackground() throws Exception {
        var json = victoryAdvancement();
        json.getAsJsonObject("display").remove("background");
        var ops = RegistryOps.create(JsonOps.INSTANCE, RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY));
        var error = Advancement.CODEC.parse(ops, json).error().orElseThrow();
        assertTrue(error.message().contains("Visible advancement roots must have background"), error.message());
    }

    private JsonObject victoryAdvancement() throws Exception {
        try (var input = getClass().getResourceAsStream("/data/ancient_dragon/advancement/defeat_ancient_dragon.json")) {
            assertNotNull(input);
            return JsonParser.parseReader(new InputStreamReader(input, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }
}
