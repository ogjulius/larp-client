package dev.larpclient.mixin;

import dev.larpclient.modules.performance.BackgroundFpsLimit;
import net.minecraft.client.Minecraft;
import org.lwjgl.opengl.Display;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Minecraft.class)
public abstract class MixinMinecraft {
    @Inject(method = "getLimitFramerate", at = @At("HEAD"), cancellable = true)
    private void larp$backgroundFps(CallbackInfoReturnable<Integer> cir) {
        int limit = BackgroundFpsLimit.limit();
        if (limit > 0 && !Display.isActive()) {
            cir.setReturnValue(limit);
        }
    }
}
