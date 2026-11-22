package dev.larpclient.mixin;

import dev.larpclient.core.gui.menu.Backdrop;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Vanilla menus outside a world (world list, options, multiplayer…) get LARP's starry sky instead of dirt. */
@Mixin(GuiScreen.class)
public abstract class MixinGuiScreen {
    @Shadow
    public int width;
    @Shadow
    public int height;

    @Inject(method = "drawBackground", at = @At("HEAD"), cancellable = true)
    private void larp$skyBackground(int tint, CallbackInfo ci) {
        if (Minecraft.getMinecraft().theWorld == null) {
            Backdrop.drawDimmed(width, height);
            ci.cancel();
        }
    }
}
