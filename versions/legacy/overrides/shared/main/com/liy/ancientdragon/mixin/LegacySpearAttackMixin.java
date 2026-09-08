package com.liy.ancientdragon.mixin;

import com.liy.ancientdragon.compat.LegacySpearItem;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Retains vanilla attack, enchantment, durability and statistics for each target of one charged jab. */
@Mixin(Player.class)
public abstract class LegacySpearAttackMixin extends LivingEntity {
    protected LegacySpearAttackMixin(EntityType<? extends LivingEntity> type, Level level) { super(type, level); }
    @Unique private boolean ancientDragon$insidePiercingJab;

    @Inject(method = "attack", at = @At("HEAD"), cancellable = true)
    private void ancientDragon$piercingJab(Entity requestedTarget, CallbackInfo callback) {
        Player player = (Player) (Object) this;
        if (ancientDragon$insidePiercingJab || !(player.getMainHandItem().getItem() instanceof LegacySpearItem)) return;
        callback.cancel();
        if (player.isSpectator() || player.getAttackStrengthScale(0.5F) < 1.0F || player.isUsingItem()) return;
        var targets = LegacySpearItem.targets(player);
        if (!targets.contains(requestedTarget)) return;
        int charge = attackStrengthTicker;
        ancientDragon$insidePiercingJab = true;
        try {
            for (Entity target : targets) {
                attackStrengthTicker = charge;
                player.attack(target);
            }
        } finally {
            ancientDragon$insidePiercingJab = false;
            player.resetAttackStrengthTicker();
        }
    }
}
