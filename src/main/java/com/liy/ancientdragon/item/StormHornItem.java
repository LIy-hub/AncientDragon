package com.liy.ancientdragon.item;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** A resource-neutral emergency wind dash forged from a single Ancient Dragon horn. */
public final class StormHornItem extends Item {
    public static final int DASH_COOLDOWN_TICKS = 20 * 30;

    public StormHornItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.getCooldowns().isOnCooldown(stack)) {
            return InteractionResult.FAIL;
        }
        if (!(level instanceof ServerLevel serverLevel)) {
            return InteractionResult.SUCCESS;
        }

        Vec3 windDash = player.getLookAngle().scale(1.35D).add(0.0D, 0.45D, 0.0D);
        player.addDeltaMovement(windDash);
        player.hurtMarked = true;
        player.getCooldowns().addCooldown(stack, DASH_COOLDOWN_TICKS);
        serverLevel.playSound(
                null,
                player.getX(),
                player.getY(),
                player.getZ(),
                SoundEvents.ENDER_DRAGON_FLAP,
                SoundSource.PLAYERS,
                1.1F,
                1.35F);
        serverLevel.sendParticles(
                ParticleTypes.CLOUD,
                player.getX(),
                player.getY() + 0.7D,
                player.getZ(),
                18,
                0.35D,
                0.25D,
                0.35D,
                0.06D);
        return InteractionResult.SUCCESS_SERVER;
    }
}
