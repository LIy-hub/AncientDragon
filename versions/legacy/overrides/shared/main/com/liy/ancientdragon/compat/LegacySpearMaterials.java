package com.liy.ancientdragon.compat;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.item.component.ItemAttributeModifiers;

/** Obtainable spear inputs only for Minecraft releases without vanilla spears. */
public final class LegacySpearMaterials {
    public static final Item DIAMOND_SPEAR = register("diamond_spear", ToolMaterial.DIAMOND, false);
    public static final Item NETHERITE_SPEAR = register("netherite_spear", ToolMaterial.NETHERITE, true);
    private LegacySpearMaterials() { }
    public static void initialize() { }
    public static void addToTab(CreativeModeTab.Output output) { output.accept(DIAMOND_SPEAR); output.accept(NETHERITE_SPEAR); }

    public static ItemAttributeModifiers withReach(ItemAttributeModifiers attributes) {
        return attributes.withModifierAdded(Attributes.ENTITY_INTERACTION_RANGE,
                new AttributeModifier(ResourceLocation.fromNamespaceAndPath("ancient_dragon", "spear_reach"),
                        1.5, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND);
    }
    private static Item register(String path, ToolMaterial material, boolean fireResistant) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath("ancient_dragon", path);
        ItemAttributeModifiers attributes = ItemAttributeModifiers.builder()
                .add(Attributes.ATTACK_DAMAGE, new AttributeModifier(Item.BASE_ATTACK_DAMAGE_ID,
                        material.attackDamageBonus(), AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND)
                .add(Attributes.ATTACK_SPEED, new AttributeModifier(Item.BASE_ATTACK_SPEED_ID,
                        1.0 / 1.15 - 4.0, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND).build();
        Item.Properties properties = new Item.Properties().setId(ResourceKey.create(BuiltInRegistries.ITEM.key(), id))
                .durability(material.durability()).repairable(material.repairItems())
                .enchantable(material.enchantmentValue()).attributes(withReach(attributes)).rarity(Rarity.COMMON);
        if (fireResistant) properties.fireResistant();
        return Registry.register(BuiltInRegistries.ITEM, id, new LegacySpearItem(properties));
    }
}
