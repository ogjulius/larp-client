package dev.larpclient.mixin;

import dev.larpclient.core.render.Shapes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiSlot;
import net.minecraft.client.renderer.Tessellator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Scrolling lists (worlds, servers, resource packs) become translucent panels over the sky. */
@Mixin(GuiSlot.class)
public abstract class MixinGuiSlot {
    @Shadow
    public int width;
    @Shadow
    public int top;
    @Shadow
    public int bottom;
    @Shadow
    public int left;
    @Shadow
    public int right;

    @Inject(method = "drawContainerBackground", at = @At("HEAD"), cancellable = true, remap = false)
    private void larp$listBackground(Tessellator tessellator, CallbackInfo ci) {
        if (Minecraft.getMinecraft().theWorld == null) {
            Shapes.rect(left, top, right - left, bottom - top, 0x70000000);
            ci.cancel();
        }
    }

    @Inject(method = "overlayBackground", at = @At("HEAD"), cancellable = true)
    private void larp$listOverlay(int startY, int endY, int startAlpha, int endAlpha, CallbackInfo ci) {
        if (Minecraft.getMinecraft().theWorld == null) {
            // The header/footer bands: leave the sky visible, just add a hairline edge.
            Shapes.rect(0, startY == 0 ? endY - 1 : startY, width, 1, 0x20FFFFFF);
            ci.cancel();
        }
    }
}
