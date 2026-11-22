package dev.larpclient.core.dev;

import dev.larpclient.LarpClient;
import dev.larpclient.core.gui.HudEditorScreen;
import dev.larpclient.core.gui.ModMenuScreen;
import dev.larpclient.core.gui.menu.LarpMainMenu;
import dev.larpclient.core.gui.menu.MusicScreen;
import dev.larpclient.modules.hud.KeystrokesHud;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.util.ScreenShotHelper;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.opengl.GL11;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Developer tool. {@code -Dlarp.screenshotTour=true} renders every LARP screen offscreen at a fixed
 * resolution ({@code larp.tourWidth}×{@code larp.tourHeight}, GUI scale {@code larp.tourScale}),
 * saves each to {@code screenshots/larp-tour-N.png}, then quits. Independent of the window size the
 * window manager gives the game, so results are reproducible.
 */
public final class ScreenshotTour {
    private static final long STEP_MS = 1600;

    private final List<Supplier<GuiScreen>> steps = new ArrayList<>();
    private final int width = Integer.getInteger("larp.tourWidth", 1280);
    private final int height = Integer.getInteger("larp.tourHeight", 720);
    private final int[] mouse = parseMouse(System.getProperty("larp.tourMouse", ""));
    private int index = -1;
    private long stepStarted;
    private GuiScreen screen;
    private Framebuffer target;

    public static void installIfRequested() {
        if (Boolean.getBoolean("larp.screenshotTour")) {
            MinecraftForge.EVENT_BUS.register(new ScreenshotTour());
        }
    }

    private ScreenshotTour() {
        steps.add(LarpMainMenu::new);
        steps.add(ModMenuScreen::new);
        steps.add(() -> new ModMenuScreen(LarpClient.get().modules().get(KeystrokesHud.class)));
        steps.add(() -> new MusicScreen(new LarpMainMenu()));
        steps.add(HudEditorScreen::new);
        stepStarted = System.currentTimeMillis() + 5000; // let the game settle first
    }

    private static int[] parseMouse(String spec) {
        String[] parts = spec.split(",");
        try {
            return new int[]{Integer.parseInt(parts[0].trim()), Integer.parseInt(parts[1].trim())};
        } catch (Exception e) {
            return new int[]{-100, -100};
        }
    }

    @SubscribeEvent
    public void onRender(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now < stepStarted) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (index < 0 || now - stepStarted > STEP_MS) {
            if (index >= 0) {
                renderOffscreen(mc, true);
            }
            index++;
            if (index >= steps.size()) {
                mc.shutdown();
                return;
            }
            mc.gameSettings.guiScale = Integer.getInteger("larp.tourScale", 2);
            screen = steps.get(index).get();
            stepStarted = now;
            withTarget(mc, () -> {
                ScaledResolution res = new ScaledResolution(mc);
                screen.setWorldAndResolution(mc, res.getScaledWidth(), res.getScaledHeight());
            });
        }
        renderOffscreen(mc, false); // keep animations running
    }

    private void renderOffscreen(Minecraft mc, boolean capture) {
        withTarget(mc, () -> {
            ScaledResolution res = new ScaledResolution(mc);
            GlStateManager.clearColor(0, 0, 0, 1);
            GlStateManager.clear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
            mc.entityRenderer.setupOverlayRendering();
            int mx = mouse[0] / res.getScaleFactor() * 2 / 2;
            int my = mouse[1] / res.getScaleFactor();
            screen.drawScreen(mx, my, 0f);
            if (capture) {
                ScreenShotHelper.saveScreenshot(mc.mcDataDir, "larp-tour-" + index + ".png", width, height, target);
            }
        });
    }

    /** Run {@code action} with the game believing its window is {@code width×height} and bound to our buffer. */
    private void withTarget(Minecraft mc, Runnable action) {
        if (target == null) {
            target = new Framebuffer(width, height, true);
        }
        int oldW = mc.displayWidth;
        int oldH = mc.displayHeight;
        Framebuffer old = mc.getFramebuffer();
        try {
            setFramebuffer(mc, target);
            mc.displayWidth = width;
            mc.displayHeight = height;
            target.bindFramebuffer(true);
            action.run();
        } finally {
            mc.displayWidth = oldW;
            mc.displayHeight = oldH;
            setFramebuffer(mc, old);
            old.bindFramebuffer(true);
        }
    }

    private static void setFramebuffer(Minecraft mc, Framebuffer fb) {
        for (String name : new String[]{"framebufferMc", "field_147124_at"}) {
            try {
                Field f = Minecraft.class.getDeclaredField(name);
                f.setAccessible(true);
                f.set(mc, fb);
                return;
            } catch (ReflectiveOperationException ignored) {
                // try the next name
            }
        }
    }
}
