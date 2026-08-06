package com.liy.ancientdragon.block;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import net.minecraft.SharedConstants;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

final class SunheartAltarMenuLogicTest {
    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        bindComponents(Items.STONE, 64, 0);
        bindComponents(Items.DIRT, 64, 0);
        bindComponents(Items.NETHERITE_SWORD, 1, 2031);
        bindComponents(Items.DIAMOND_SWORD, 1, 1561);
    }

    private static void bindComponents(Item item, int maxStackSize, int maxDamage) {
        var holder = BuiltInRegistries.ITEM.get(BuiltInRegistries.ITEM.getKey(item)).orElseThrow();
        if (holder.areComponentsBound()) {
            return;
        }
        DataComponentMap.Builder components = DataComponentMap.builder()
                .set(DataComponents.MAX_STACK_SIZE, maxStackSize);
        if (maxDamage > 0) {
            components.set(DataComponents.MAX_DAMAGE, maxDamage);
        }
        holder.bindComponents(components.build());
    }

    @ParameterizedTest
    @EnumSource(SunheartAltarRite.class)
    void everyRiteBecomesReadyAtItsExactMaterialCost(SunheartAltarRite rite) {
        Optional<SunheartAltarRite.Ingredient> material = rite.ingredient()
                        == SunheartAltarRite.Ingredient.NONE
                ? Optional.empty()
                : Optional.of(rite.ingredient());

        assertEquals(
                SunheartAltarMenu.RecipeError.NONE,
                SunheartAltarMenu.evaluate(Optional.of(rite), material, rite.ingredientCost()));
    }

    @Test
    void evaluationDistinguishesEmptyWrongInsufficientAndReadyInputs() {
        Optional<SunheartAltarRite> noRite = Optional.empty();
        Optional<SunheartAltarRite> sword = Optional.of(SunheartAltarRite.DRAGONBONE_SWORD);

        assertEquals(
                SunheartAltarMenu.RecipeError.NONE,
                SunheartAltarMenu.evaluate(noRite, Optional.empty(), 0));
        assertEquals(
                SunheartAltarMenu.RecipeError.MISSING_TARGET,
                SunheartAltarMenu.evaluate(noRite, Optional.empty(), 1));
        assertEquals(
                SunheartAltarMenu.RecipeError.INSUFFICIENT_MATERIAL,
                SunheartAltarMenu.evaluate(sword, Optional.empty(), 0));
        assertEquals(
                SunheartAltarMenu.RecipeError.WRONG_MATERIAL,
                SunheartAltarMenu.evaluate(
                        sword, Optional.of(SunheartAltarRite.Ingredient.SCALE), 10));
        assertEquals(
                SunheartAltarMenu.RecipeError.INSUFFICIENT_MATERIAL,
                SunheartAltarMenu.evaluate(
                        sword, Optional.of(SunheartAltarRite.Ingredient.BONE), 9));
        assertEquals(
                SunheartAltarMenu.RecipeError.NONE,
                SunheartAltarMenu.evaluate(
                        sword, Optional.of(SunheartAltarRite.Ingredient.BONE), 10));
        assertEquals(
                SunheartAltarMenu.RecipeError.NONE,
                SunheartAltarMenu.evaluate(
                        sword, Optional.of(SunheartAltarRite.Ingredient.BONE), 64));
    }

    @Test
    void zeroCostRiteRequiresAnEmptyMaterialSlot() {
        Optional<SunheartAltarRite> horn = Optional.of(SunheartAltarRite.STORM_HORN);

        assertEquals(
                SunheartAltarMenu.RecipeError.NONE,
                SunheartAltarMenu.evaluate(horn, Optional.empty(), 0));
        assertEquals(
                SunheartAltarMenu.RecipeError.UNNEEDED_MATERIAL,
                SunheartAltarMenu.evaluate(
                        horn, Optional.of(SunheartAltarRite.Ingredient.BONE), 1));
    }

    @Test
    void survivalConsumesOneTargetAndTheExactMaterialCost() {
        ItemStack target = new ItemStack(Items.STONE, 2);
        ItemStack material = new ItemStack(Items.DIRT, 18);

        SunheartAltarMenu.consumeInputs(target, material, 10, false);

        assertEquals(1, target.getCount());
        assertEquals(8, material.getCount());
    }

    @Test
    void creativeConsumesTheTargetButPreservesRequiredMaterial() {
        ItemStack target = new ItemStack(Items.STONE, 2);
        ItemStack material = new ItemStack(Items.DIRT, 18);

        SunheartAltarMenu.consumeInputs(target, material, 10, true);

        assertEquals(1, target.getCount());
        assertEquals(18, material.getCount());
    }

    @Test
    void zeroCostRiteConsumesOnlyOneTarget() {
        ItemStack target = new ItemStack(Items.STONE, 3);
        ItemStack material = ItemStack.EMPTY;

        SunheartAltarMenu.consumeInputs(target, material, 0, false);

        assertEquals(2, target.getCount());
        assertTrue(material.isEmpty());
    }

    @Test
    void resultIsOneItemAndKeepsTheTargetsComponentPatch() {
        ItemStack target = new ItemStack(Items.NETHERITE_SWORD, 4);
        target.set(DataComponents.CUSTOM_NAME, Component.literal("Sunheart Test"));
        target.set(DataComponents.DAMAGE, 137);
        target.set(DataComponents.REPAIR_COST, 7);
        Enchantment enchantment = Enchantment.enchantment(Enchantment.definition(
                        HolderSet.direct(BuiltInRegistries.ITEM.wrapAsHolder(Items.NETHERITE_SWORD)),
                        1,
                        1,
                        new Enchantment.Cost(1, 0),
                        new Enchantment.Cost(1, 0),
                        1,
                        EquipmentSlotGroup.MAINHAND))
                .build(Identifier.withDefaultNamespace("sunheart_test"));
        ItemEnchantments.Mutable enchantments = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
        enchantments.set(Holder.direct(enchantment), 1);
        target.set(DataComponents.ENCHANTMENTS, enchantments.toImmutable());

        ItemStack result = SunheartAltarMenu.transmuteResult(target, Items.DIAMOND_SWORD);

        assertTrue(result.is(Items.DIAMOND_SWORD));
        assertEquals(1, result.getCount());
        assertEquals(4, target.getCount());
        assertEquals(target.getComponentsPatch(), result.getComponentsPatch());
        assertEquals(Component.literal("Sunheart Test"), result.get(DataComponents.CUSTOM_NAME));
        assertEquals(137, result.getOrDefault(DataComponents.DAMAGE, 0));
        assertEquals(7, result.getOrDefault(DataComponents.REPAIR_COST, 0));
        assertEquals(target.get(DataComponents.ENCHANTMENTS), result.get(DataComponents.ENCHANTMENTS));
    }
}
