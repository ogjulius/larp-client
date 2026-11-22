package dev.larpclient.mixin;

import dev.larpclient.modules.performance.ParticleLimiter;
import net.minecraft.client.particle.EffectRenderer;
import net.minecraft.client.particle.EntityFX;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EffectRenderer.class)
public abstract class MixinEffectRenderer {
    @Inject(method = "addEffect", at = @At("HEAD"), cancellable = true)
    private void larp$limitParticles(EntityFX effect, CallbackInfo ci) {
        if (ParticleLimiter.shouldDrop()) {
            ci.cancel();
        }
    }
}
