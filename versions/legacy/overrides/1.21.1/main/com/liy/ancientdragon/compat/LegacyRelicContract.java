package com.liy.ancientdragon.compat;


import com.liy.ancientdragon.item.AncientDragonItems;
import com.liy.ancientdragon.item.AncientDragonRelicStats;
import net.fabricmc.fabric.api.entity.event.v1.FabricElytraItem;
import net.minecraft.SharedConstants;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Equipable;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.component.ItemAttributeModifiers;



public final class LegacyRelicContract {
    private LegacyRelicContract() { }
    public static void verify() {
        armorRetainsNativeDefenseRelicBonusesDurabilityAndEquipSlots();
        bothWingsKeepOfficialGliderHookAndUnbreakableChestEquipment();
        spearRetainsDamageRangeDurabilityAndNativeEnchantability();
        System.getLogger("Ancient Dragon").log(System.Logger.Level.INFO, "Legacy relic contract verified: armor, gliders, spear");
    }
    private static void armorRetainsNativeDefenseRelicBonusesDurabilityAndEquipSlots() {
        Item[] items = {AncientDragonItems.MOUNTAINFORGED_NETHERITE_HELMET, AncientDragonItems.MOUNTAINFORGED_NETHERITE_CHESTPLATE,
                AncientDragonItems.MOUNTAINFORGED_NETHERITE_LEGGINGS, AncientDragonItems.MOUNTAINFORGED_NETHERITE_BOOTS};
        ArmorItem.Type[] types = {ArmorItem.Type.HELMET, ArmorItem.Type.CHESTPLATE, ArmorItem.Type.LEGGINGS, ArmorItem.Type.BOOTS};
        double[] defense = {3, 8, 6, 3};
        for (int i = 0; i < items.length; i++) {
            assertInstanceOf(ArmorItem.class, items[i]);
            assertEquals(types[i].getSlot(), Equipable.get(items[i].getDefaultInstance()).getEquipmentSlot());
            assertEquals(AncientDragonRelicStats.upgradedDurability(types[i].getDurability(37)), items[i].getDefaultInstance().getMaxDamage());
            assertEquals(defense[i], amount(items[i], Attributes.ARMOR));
            assertEquals(5.0, amount(items[i], Attributes.ARMOR_TOUGHNESS));
            assertEquals(0.35, amount(items[i], Attributes.KNOCKBACK_RESISTANCE), 1.0e-7);
        }
    }
    private static void bothWingsKeepOfficialGliderHookAndUnbreakableChestEquipment() {
        for (var item : new Item[] {AncientDragonItems.SKY_WING, AncientDragonItems.MOUNTAINFORGED_SKY_WING}) {
            assertInstanceOf(FabricElytraItem.class, item);
            assertEquals(EquipmentSlot.CHEST, Equipable.get(item.getDefaultInstance()).getEquipmentSlot());
            assertNotNull(item.getDefaultInstance().get(DataComponents.UNBREAKABLE));
        }
        assertEquals(8, amount(AncientDragonItems.MOUNTAINFORGED_SKY_WING, Attributes.ARMOR));
    }
    private static void spearRetainsDamageRangeDurabilityAndNativeEnchantability() {
        var spear = AncientDragonItems.DRAGONBONE_NETHERITE_SPEAR;
        assertEquals(9.0, amount(spear, Attributes.ATTACK_DAMAGE));
        assertEquals(1.5, amount(spear, Attributes.ENTITY_INTERACTION_RANGE));
        assertEquals(15, spear.getEnchantmentValue());
        assertEquals(4570, spear.getDefaultInstance().getMaxDamage());
    }
    private static void assertEquals(double expected, double actual) { assertEquals(expected, actual, 1.0e-7); }
    private static void assertEquals(double expected, double actual, double tolerance) {
        if (Math.abs(expected - actual) > tolerance) throw new IllegalStateException("Legacy relic attribute mismatch: " + expected + " != " + actual);
    }
    private static void assertEquals(Object expected, Object actual) {
        if (!java.util.Objects.equals(expected, actual)) throw new IllegalStateException("Legacy relic equipment mismatch: " + expected + " != " + actual);
    }
    private static void assertInstanceOf(Class<?> expected, Object actual) {
        if (!expected.isInstance(actual)) throw new IllegalStateException("Missing legacy equipment behavior " + expected);
    }
    private static void assertNotNull(Object actual) { if (actual == null) throw new IllegalStateException("Missing legacy equipment component"); }
    private static double amount(Item item, Holder<Attribute> attribute) {
        return item.getDefaultInstance().getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY)
                .modifiers().stream().filter(entry -> entry.attribute().equals(attribute)).mapToDouble(entry -> entry.modifier().amount()).sum();
    }
}
