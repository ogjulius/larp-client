package dev.larpclient.mixin;

import dev.larpclient.modules.performance.BackgroundFpsLimit;
import net.minecraft.client.Minecraft;
import org.lwjgl.opengl.Display;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Minecraft.class)
public abstract class MixinMinecraft {
    @Inject(method = "getLimitFramerate", at = @At("HEAD"), cancellable = true)
    private void larp$backgroundFps(CallbackInfoReturnable<Integer> cir) {
        int limit = BackgroundFpsLimit.limit();
        if (limit > 0 && !Display.isActive()) {
            cir.setReturnValue(limit);
            return;
        }
        // Vanilla caps menus outside a world at 30 FPS, which makes LARP's animated menus stutter.
        Minecraft self = (Minecraft) (Object) this;
        if (self.theWorld == null && self.currentScreen != null) {
            cir.setReturnValue(dev.larpclient.modules.performance.FastRender.menuFramerate(self.gameSettings.limitFramerate));
        }
    }

    /** Route F11 / the Fullscreen option through the compositor on Linux (see CompositorFullscreen). */
    @Inject(method = "toggleFullscreen", at = @At("HEAD"), cancellable = true)
    private void larp$compositorFullscreen(CallbackInfo ci) {
        if (dev.larpclient.core.platform.CompositorFullscreen.get().toggle()) {
            ci.cancel();
        }
    }

    /** glGetError forces a CPU/GPU sync; vanilla calls it several times per frame. */
    @Inject(method = "checkGLError", at = @At("HEAD"), cancellable = true)
    private void larp$skipGlErrorCheck(String location, CallbackInfo ci) {
        if (!dev.larpclient.modules.performance.FastRender.glErrorChecks()) {
            ci.cancel();
        }
    }
}
