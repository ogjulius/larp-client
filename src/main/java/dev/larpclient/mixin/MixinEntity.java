package dev.larpclient.mixin;

import dev.larpclient.modules.utility.Freelook;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public abstract class MixinEntity {
    /** While freelook is active, mouse movement turns the camera instead of the player. */
    @Inject(method = "setAngles", at = @At("HEAD"), cancellable = true)
    private void larp$freelook(float yaw, float pitch, CallbackInfo ci) {
        if (Freelook.isActive() && (Object) this == Minecraft.getMinecraft().thePlayer) {
            Freelook.rotate(yaw, pitch);
            ci.cancel();
        }
    }
}
