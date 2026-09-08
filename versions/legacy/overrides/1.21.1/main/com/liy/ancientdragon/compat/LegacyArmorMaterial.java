package com.liy.ancientdragon.compat;

import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ArmorMaterials;

/** Native armor material rendering and repair behavior with the authored mountainforged textures. */
public final class LegacyArmorMaterial {
    public static final Holder<ArmorMaterial> MOUNTAINFORGED = register();
    private LegacyArmorMaterial() { }
    private static Holder<ArmorMaterial> register() {
        var vanilla = ArmorMaterials.NETHERITE.value();
        return Registry.registerForHolder(BuiltInRegistries.ARMOR_MATERIAL,
                ResourceLocation.fromNamespaceAndPath("ancient_dragon", "mountainforged"),
                new ArmorMaterial(vanilla.defense(), vanilla.enchantmentValue(), vanilla.equipSound(),
                        vanilla.repairIngredient(), List.of(new ArmorMaterial.Layer(ResourceLocation.fromNamespaceAndPath(
                        "ancient_dragon", "mountainforged"))), vanilla.toughness(), vanilla.knockbackResistance()));
    }
}
