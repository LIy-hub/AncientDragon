package com.liy.ancientdragon.mixin;

import com.liy.ancientdragon.entity.AncientDragonEntity;
import com.liy.ancientdragon.entity.AncientDragonPartEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Older canBeCollidedWith has no source entity; preserve the upstream player-only proxy rule here. */
@Mixin(Entity.class)
public abstract class LegacyCollisionFilterMixin {
    @Inject(method = "canCollideWith", at = @At("HEAD"), cancellable = true)
    private void ancientDragon$playerOnlyProxyCollision(Entity target, CallbackInfoReturnable<Boolean> callback) {
        if (!((Object) this instanceof Player)
                && (target instanceof AncientDragonPartEntity || target instanceof AncientDragonEntity)) {
            callback.setReturnValue(false);
        }
    }
}
