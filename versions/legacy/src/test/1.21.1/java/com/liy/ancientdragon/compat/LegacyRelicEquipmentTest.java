package com.liy.ancientdragon.compat;

import static org.junit.jupiter.api.Assertions.*;
import net.fabricmc.fabric.api.entity.event.v1.FabricElytraItem;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ArmorItem;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class LegacyRelicEquipmentTest {
    @BeforeAll static void bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }
    @Test void nativeArmorGetterRetainsDefenseBeforeRelicBonuses() {
        double[] expected = {3,8,6,3};
        ArmorItem.Type[] types = {ArmorItem.Type.HELMET,ArmorItem.Type.CHESTPLATE,ArmorItem.Type.LEGGINGS,ArmorItem.Type.BOOTS};
        for (int i=0;i<types.length;i++) assertEquals(expected[i], LegacyArmorAttributes.netherite(types[i]).modifiers().stream()
                .filter(entry -> entry.attribute().equals(Attributes.ARMOR)).mapToDouble(entry -> entry.modifier().amount()).sum());
    }
    @Test void wingClassesImplementOfficialGliderHookAndNativeEquipment() {
        assertTrue(FabricElytraItem.class.isAssignableFrom(LegacySkyWingItem.class));
        assertTrue(FabricElytraItem.class.isAssignableFrom(LegacyWingedArmorItem.class));
        assertTrue(ArmorItem.class.isAssignableFrom(LegacyWingedArmorItem.class));
    }
    @Test void nativeToolTierKeepsNetheriteAndDiamondContracts() {
        assertEquals(2031,LegacyToolMaterial.NETHERITE.getUses());
        assertEquals(1561,LegacyToolMaterial.DIAMOND.getUses());
        assertEquals(15,LegacyToolMaterial.NETHERITE.getEnchantmentValue());
        assertEquals(4.0F,LegacyToolMaterial.NETHERITE.getAttackDamageBonus());
    }
}
