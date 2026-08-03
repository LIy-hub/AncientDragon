package com.liy.ancientdragon.item;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

final class AncientDragonEquipmentCompatibilityTagTest {
    @Test
    void strengthenedArmourExtendsTheFourVanillaArmourSlotsWithoutReplacingVanillaTags() throws IOException {
        assertTagContains("head_armor", "ancient_dragon:mountainforged_netherite_helmet");
        assertTagContains("chest_armor", "ancient_dragon:mountainforged_netherite_chestplate");
        assertTagContains("chest_armor", "ancient_dragon:mountainforged_sky_wing");
        assertTagContains("leg_armor", "ancient_dragon:mountainforged_netherite_leggings");
        assertTagContains("foot_armor", "ancient_dragon:mountainforged_netherite_boots");
    }

    @Test
    void dragonboneWeaponsJoinTheVanillaWeaponCategoriesThatDriveTheirEnchantments() throws IOException {
        assertTagContains("swords", "ancient_dragon:dragonbone_netherite_sword");
        assertTagContains("axes", "ancient_dragon:dragonbone_netherite_axe");
        assertTagContains("spears", "ancient_dragon:dragonbone_netherite_spear");
    }

    private static void assertTagContains(String tagName, String itemId) throws IOException {
        String resourcePath = "data/minecraft/tags/item/" + tagName + ".json";
        try (InputStream stream = AncientDragonEquipmentCompatibilityTagTest.class
                .getClassLoader()
                .getResourceAsStream(resourcePath)) {
            assertTrue(stream != null, () -> "Missing compatibility tag " + resourcePath);
            String content = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(content.contains("\"replace\": false"), () -> resourcePath + " must extend vanilla");
            assertTrue(content.contains("\"" + itemId + "\""), () -> resourcePath + " is missing " + itemId);
        }
    }
}
