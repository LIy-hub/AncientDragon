package com.liy.ancientdragon.mixin.client;

import com.liy.ancientdragon.client.atmosphere.AncientDragonAtmosphereController;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Keeps battle impulses visual-only and first-person only. */
@Mixin(Camera.class)
abstract class CameraMixin {
    @Shadow private float xRot;
    @Shadow private float yRot;

    @Shadow protected abstract void setRotation(float yRot, float xRot);

    @Inject(method = "update", at = @At("TAIL"))
    private void ancientDragon$applyBattleImpulse(DeltaTracker deltaTracker, CallbackInfo callback) {
        Camera camera = (Camera) (Object) this;
        if (camera.isDetached()) {
            return;
        }
        float yaw = AncientDragonAtmosphereController.cameraYawOffset();
        float pitch = AncientDragonAtmosphereController.cameraPitchOffset();
        if (yaw != 0.0F || pitch != 0.0F) {
            setRotation(yRot + yaw, xRot + pitch);
        }
    }
}
