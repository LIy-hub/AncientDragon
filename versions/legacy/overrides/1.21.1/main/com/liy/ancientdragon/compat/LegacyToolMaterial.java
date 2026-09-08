package com.liy.ancientdragon.compat;

import net.minecraft.tags.TagKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.Block;

public record LegacyToolMaterial(TagKey<Block> incorrectBlocksForDrops, int durability, float speed,
        float attackDamageBonus, int enchantmentValue, TagKey<Item> repairItems) implements Tier {
    public static final LegacyToolMaterial DIAMOND = from(Tiers.DIAMOND, "diamond_tool_materials");
    public static final LegacyToolMaterial NETHERITE = from(Tiers.NETHERITE, "netherite_tool_materials");
    private static LegacyToolMaterial from(Tier tier, String tag) {
        return new LegacyToolMaterial(tier.getIncorrectBlocksForDrops(), tier.getUses(), tier.getSpeed(),
                tier.getAttackDamageBonus(), tier.getEnchantmentValue(), TagKey.create(Registries.ITEM,
                ResourceLocation.fromNamespaceAndPath("ancient_dragon", tag)));
    }
    @Override public int getUses() { return durability; }
    @Override public float getSpeed() { return speed; }
    @Override public float getAttackDamageBonus() { return attackDamageBonus; }
    @Override public TagKey<Block> getIncorrectBlocksForDrops() { return incorrectBlocksForDrops; }
    @Override public int getEnchantmentValue() { return enchantmentValue; }
    @Override public Ingredient getRepairIngredient() { return Ingredient.of(repairItems); }
}
