package dev.larpclient.mixin;

import dev.larpclient.modules.performance.EntityCulling;
import net.minecraft.client.renderer.culling.ICamera;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(RenderManager.class)
public abstract class MixinRenderManager {
    @Shadow
    public Entity livingPlayer;

    @Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true)
    private void larp$cullDistant(Entity entity, ICamera camera, double camX, double camY, double camZ,
                                  CallbackInfoReturnable<Boolean> cir) {
        if (EntityCulling.shouldCull(entity, livingPlayer)) {
            cir.setReturnValue(false);
        }
    }
}
