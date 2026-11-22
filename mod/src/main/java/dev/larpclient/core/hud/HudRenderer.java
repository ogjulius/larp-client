package dev.larpclient.core.hud;

import dev.larpclient.core.event.EventBus;
import dev.larpclient.core.event.events.Render2DEvent;
import dev.larpclient.core.module.Module;
import dev.larpclient.core.module.ModuleManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;

import java.util.ArrayList;
import java.util.List;

/** Draws every enabled {@link HudModule} each frame. */
public final class HudRenderer {
    private final ModuleManager modules;
    private final List<HudModule> hudModules = new ArrayList<>();

    public HudRenderer(ModuleManager modules, EventBus bus) {
        this.modules = modules;
        bus.subscribe(Render2DEvent.class, this::render, EventBus.Priority.LOW);
    }

    public List<HudModule> getHudModules() {
        if (hudModules.isEmpty()) {
            for (Module module : modules.getModules()) {
                if (module instanceof HudModule) {
                    hudModules.add((HudModule) module);
                }
            }
        }
        return hudModules;
    }

    private void render(Render2DEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.gameSettings.showDebugInfo || mc.currentScreen instanceof dev.larpclient.core.gui.HudEditorScreen) {
            return;
        }
        for (HudModule hud : getHudModules()) {
            if (hud.isEnabled() && hud.shouldRender()) {
                draw(hud, event.scaledWidth, event.scaledHeight, event.partialTicks, false);
            }
        }
    }

    public static void draw(HudModule hud, int screenWidth, int screenHeight, float partialTicks, boolean editing) {
        GlStateManager.pushMatrix();
        GlStateManager.translate(hud.getScreenX(screenWidth), hud.getScreenY(screenHeight), 0);
        float scale = hud.getScale();
        if (scale != 1f) {
            GlStateManager.scale(scale, scale, 1f);
        }
        hud.render(partialTicks, editing);
        GlStateManager.popMatrix();
        GlStateManager.color(1f, 1f, 1f, 1f);
        GlStateManager.enableBlend();
    }
}
