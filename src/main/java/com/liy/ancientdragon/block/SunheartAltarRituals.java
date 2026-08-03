package com.liy.ancientdragon.block;

import com.liy.ancientdragon.item.AncientDragonItems;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** Shared ritual lookup plus the sole server-side implementation of Sunheart forging. */
public final class SunheartAltarRituals {
    private SunheartAltarRituals() {
    }

    public static Optional<SunheartAltarRite> riteFor(ItemStack stack) {
        if (stack.is(Items.ELYTRA)) {
            return Optional.of(SunheartAltarRite.SKY_WING);
        }
        if (stack.is(Items.NETHERITE_HELMET)) {
            return Optional.of(SunheartAltarRite.MOUNTAINFORGED_HELMET);
        }
        if (stack.is(Items.NETHERITE_CHESTPLATE)) {
            return Optional.of(SunheartAltarRite.MOUNTAINFORGED_CHESTPLATE);
        }
        if (stack.is(AncientDragonItems.MOUNTAINFORGED_NETHERITE_CHESTPLATE)) {
            return Optional.of(SunheartAltarRite.MOUNTAINFORGED_SKY_WING);
        }
        if (stack.is(Items.NETHERITE_LEGGINGS)) {
            return Optional.of(SunheartAltarRite.MOUNTAINFORGED_LEGGINGS);
        }
        if (stack.is(Items.NETHERITE_BOOTS)) {
            return Optional.of(SunheartAltarRite.MOUNTAINFORGED_BOOTS);
        }
        if (stack.is(Items.NETHERITE_SWORD)) {
            return Optional.of(SunheartAltarRite.DRAGONBONE_SWORD);
        }
        if (stack.is(Items.NETHERITE_AXE)) {
            return Optional.of(SunheartAltarRite.DRAGONBONE_AXE);
        }
        if (stack.is(Items.NETHERITE_PICKAXE)) {
            return Optional.of(SunheartAltarRite.DRAGONBONE_PICKAXE);
        }
        if (stack.is(Items.NETHERITE_SHOVEL)) {
            return Optional.of(SunheartAltarRite.DRAGONBONE_SHOVEL);
        }
        if (stack.is(Items.NETHERITE_HOE)) {
            return Optional.of(SunheartAltarRite.DRAGONBONE_HOE);
        }
        if (stack.is(Items.NETHERITE_SPEAR)) {
            return Optional.of(SunheartAltarRite.DRAGONBONE_SPEAR);
        }
        if (stack.is(AncientDragonItems.ANCIENT_HORN)) {
            return Optional.of(SunheartAltarRite.STORM_HORN);
        }
        return Optional.empty();
    }

    public static Item ingredientFor(SunheartAltarRite.Ingredient ingredient) {
        return switch (ingredient) {
            case WING_MEMBRANE -> AncientDragonItems.ANCIENT_WING_MEMBRANE;
            case SCALE -> AncientDragonItems.ANCIENT_SCALE;
            case BONE -> AncientDragonItems.ANCIENT_BONE;
            case SKY_WING -> AncientDragonItems.SKY_WING;
            case NONE -> Items.AIR;
        };
    }

    public static Item outputFor(SunheartAltarRite rite) {
        return switch (rite) {
            case SKY_WING -> AncientDragonItems.SKY_WING;
            case MOUNTAINFORGED_HELMET -> AncientDragonItems.MOUNTAINFORGED_NETHERITE_HELMET;
            case MOUNTAINFORGED_CHESTPLATE -> AncientDragonItems.MOUNTAINFORGED_NETHERITE_CHESTPLATE;
            case MOUNTAINFORGED_SKY_WING -> AncientDragonItems.MOUNTAINFORGED_SKY_WING;
            case MOUNTAINFORGED_LEGGINGS -> AncientDragonItems.MOUNTAINFORGED_NETHERITE_LEGGINGS;
            case MOUNTAINFORGED_BOOTS -> AncientDragonItems.MOUNTAINFORGED_NETHERITE_BOOTS;
            case DRAGONBONE_SWORD -> AncientDragonItems.DRAGONBONE_NETHERITE_SWORD;
            case DRAGONBONE_AXE -> AncientDragonItems.DRAGONBONE_NETHERITE_AXE;
            case DRAGONBONE_PICKAXE -> AncientDragonItems.DRAGONBONE_NETHERITE_PICKAXE;
            case DRAGONBONE_SHOVEL -> AncientDragonItems.DRAGONBONE_NETHERITE_SHOVEL;
            case DRAGONBONE_HOE -> AncientDragonItems.DRAGONBONE_NETHERITE_HOE;
            case DRAGONBONE_SPEAR -> AncientDragonItems.DRAGONBONE_NETHERITE_SPEAR;
            case STORM_HORN -> AncientDragonItems.STORM_HORN;
        };
    }

    public static int countIngredient(Player player, Item ingredient) {
        return player.getInventory().getNonEquipmentItems().stream()
                .filter(stack -> stack.is(ingredient))
                .mapToInt(ItemStack::getCount)
                .sum();
    }

    public static boolean forge(ServerPlayer player, BlockPos altarPos, InteractionHand hand) {
        ServerLevel level = player.level();
        if (!SunheartAltarAccessRules.mayUse(
                level.getBlockState(altarPos).is(AncientDragonBlocks.SUNHEART_ALTAR),
                player.distanceToSqr(Vec3.atCenterOf(altarPos)))) {
            player.sendSystemMessage(Component.translatable("message.ancient_dragon.altar_too_far"));
            return false;
        }

        ItemStack target = player.getItemInHand(hand);
        Optional<SunheartAltarRite> riteOptional = riteFor(target);
        if (riteOptional.isEmpty()) {
            player.sendSystemMessage(Component.translatable("message.ancient_dragon.altar_no_rite"));
            return false;
        }

        SunheartAltarRite rite = riteOptional.get();
        Item ingredient = ingredientFor(rite.ingredient());
        if (!player.getAbilities().instabuild
                && rite.ingredientCost() > 0
                && countIngredient(player, ingredient) < rite.ingredientCost()) {
            player.sendSystemMessage(Component.translatable(needMessageKey(rite.ingredient()), rite.ingredientCost()));
            return false;
        }
        if (!player.getAbilities().instabuild && rite.ingredientCost() > 0) {
            consumeIngredient(player, ingredient, rite.ingredientCost());
        }

        Item output = outputFor(rite);
        player.setItemInHand(hand, target.transmuteCopy(output));
        level.playSound(null, altarPos, SoundEvents.BEACON_POWER_SELECT, SoundSource.BLOCKS, 1.0F, 1.2F);
        level.sendParticles(
                ParticleTypes.END_ROD,
                altarPos.getX() + 0.5D,
                altarPos.getY() + 1.0D,
                altarPos.getZ() + 0.5D,
                24,
                0.3D,
                0.4D,
                0.3D,
                0.04D);
        player.sendSystemMessage(Component.translatable(
                "message.ancient_dragon.altar_forged", output.getDefaultInstance().getHoverName()));
        return true;
    }

    public static String needMessageKey(SunheartAltarRite.Ingredient ingredient) {
        return switch (ingredient) {
            case WING_MEMBRANE -> "message.ancient_dragon.altar_needs_membranes";
            case SCALE -> "message.ancient_dragon.altar_needs_scales";
            case BONE -> "message.ancient_dragon.altar_needs_bones";
            case SKY_WING -> "message.ancient_dragon.altar_needs_sky_wing";
            case NONE -> throw new IllegalArgumentException("No material requirement has no failure message");
        };
    }

    private static void consumeIngredient(Player player, Item ingredient, int count) {
        int remaining = count;
        for (ItemStack stack : player.getInventory().getNonEquipmentItems()) {
            if (!stack.is(ingredient)) {
                continue;
            }
            int consumed = Math.min(stack.getCount(), remaining);
            stack.shrink(consumed);
            remaining -= consumed;
            if (remaining == 0) {
                player.getInventory().setChanged();
                return;
            }
        }
        throw new IllegalStateException("Sunheart Altar consumed materials without confirming the full cost");
    }
}
