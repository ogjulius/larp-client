package dev.larpclient.core.dev;

import dev.larpclient.LarpClient;
import net.minecraft.client.Minecraft;
import net.minecraft.util.ScreenShotHelper;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.opengl.Display;

/**
 * Developer tool: {@code -Dlarp.fullscreenProbe=true} toggles fullscreen on and off by itself,
 * logging window/framebuffer sizes and saving screenshots, then quits. No input is simulated.
 */
public final class FullscreenProbe {
    private final long start = System.currentTimeMillis();
    private int step;

    public static void installIfRequested() {
        if (Boolean.getBoolean("larp.fullscreenProbe")) {
            MinecraftForge.EVENT_BUS.register(new FullscreenProbe());
        }
    }

    private void log(String what) {
        Minecraft mc = Minecraft.getMinecraft();
        LarpClient.LOGGER.info("[probe] {}: display={}x{} fullscreen={} mc={}x{} pos={},{} active={}",
                what, Display.getWidth(), Display.getHeight(), Display.isFullscreen(),
                mc.displayWidth, mc.displayHeight, Display.getX(), Display.getY(), Display.isActive());
    }

    @SubscribeEvent
    public void onRender(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        long t = System.currentTimeMillis() - start;
        int[] at = {6000, 10000, 10500, 14500, 15000, 19000, 22000};
        if (step >= at.length || t < at[step]) {
            return;
        }
        switch (step) {
            case 0: log("windowed"); mc.toggleFullscreen(); break;
            case 1: log("fullscreen"); shot(mc, "probe-fullscreen.png"); break;
            case 2: mc.toggleFullscreen(); break;
            case 3: log("windowed-again"); shot(mc, "probe-windowed.png"); break;
            case 4: mc.toggleFullscreen(); break;
            case 5: log("fullscreen-again"); shot(mc, "probe-fullscreen2.png"); mc.toggleFullscreen(); break;
            default: log("final"); mc.shutdown(); break;
        }
        step++;
    }

    private static void shot(Minecraft mc, String name) {
        ScreenShotHelper.saveScreenshot(mc.mcDataDir, name, mc.displayWidth, mc.displayHeight, mc.getFramebuffer());
    }
}
