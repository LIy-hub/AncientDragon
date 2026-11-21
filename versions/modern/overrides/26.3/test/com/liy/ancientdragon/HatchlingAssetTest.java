package com.liy.ancientdragon;

import static org.junit.jupiter.api.Assertions.*;

import com.liy.blendlib.api.BlendResourceId;
import com.liy.blendlib.core.asset.AssetBytes;
import com.liy.blendlib.core.loader.ModelAssetLoader;
import com.liy.blendlib.core.model.ModelAsset;
import java.util.List;
import java.io.IOException;
import java.io.UncheckedIOException;
import org.junit.jupiter.api.Test;

/** Verifies the exact resources fed to BlendLib's production reload parser. */
class HatchlingAssetTest {
    @Test
    void loadsBothEggStatesAndAllHatchlingAnimations() {
        var egg = load("block/dragon_egg");
        var cracked = load("block/dragon_egg_cracked");
        var hatchling = load("entity/hatchling");

        assertEquals(1.0, hatchling.unitsPerBlock());
        assertEquals(4, hatchling.clips().size());
        assertNotNull(hatchling.skeleton());
        assertEquals(1, egg.primitives().size());
        assertEquals(1, cracked.primitives().size());
        for (var model : List.of(egg, cracked, hatchling)) {
            for (var material : model.materials().values()) {
                assertTrue(read(material.baseColor()).size() > 0);
            }
        }
    }

    private static ModelAsset load(String path) {
        var id = BlendResourceId.parse("ancient_dragon:" + path);
        var descriptor = BlendResourceId.parse("ancient_dragon:blend_models/" + path + ".json");
        return new ModelAssetLoader().load(id, read(descriptor), HatchlingAssetTest::read);
    }

    private static AssetBytes read(BlendResourceId id) {
        try (var input = HatchlingAssetTest.class.getResourceAsStream("/assets/" + id.namespace() + "/" + id.path())) {
            assertNotNull(input, id.toString());
            return new AssetBytes(id, input.readAllBytes());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
