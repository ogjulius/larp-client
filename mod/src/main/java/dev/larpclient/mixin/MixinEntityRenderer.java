package dev.larpclient.mixin;

import dev.larpclient.modules.utility.Freelook;
import dev.larpclient.modules.utility.NoHurtCam;
import net.minecraft.client.renderer.EntityRenderer;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityRenderer.class)
public abstract class MixinEntityRenderer {
    @Inject(method = "hurtCameraEffect", at = @At("HEAD"), cancellable = true)
    private void larp$noHurtCam(float partialTicks, CallbackInfo ci) {
        if (NoHurtCam.active()) {
            ci.cancel();
        }
    }

    // --- Freelook: orient the camera with the freelook angles instead of the entity's ---

    @Redirect(method = "orientCamera", at = @At(value = "FIELD", target = "Lnet/minecraft/entity/Entity;rotationYaw:F"))
    private float larp$yaw(Entity entity) {
        return Freelook.isActive() ? Freelook.getCameraYaw() : entity.rotationYaw;
    }

    @Redirect(method = "orientCamera", at = @At(value = "FIELD", target = "Lnet/minecraft/entity/Entity;prevRotationYaw:F"))
    private float larp$prevYaw(Entity entity) {
        return Freelook.isActive() ? Freelook.getCameraYaw() : entity.prevRotationYaw;
    }

    @Redirect(method = "orientCamera", at = @At(value = "FIELD", target = "Lnet/minecraft/entity/Entity;rotationPitch:F"))
    private float larp$pitch(Entity entity) {
        return Freelook.isActive() ? Freelook.getCameraPitch() : entity.rotationPitch;
    }

    @Redirect(method = "orientCamera", at = @At(value = "FIELD", target = "Lnet/minecraft/entity/Entity;prevRotationPitch:F"))
    private float larp$prevPitch(Entity entity) {
        return Freelook.isActive() ? Freelook.getCameraPitch() : entity.prevRotationPitch;
    }
}
