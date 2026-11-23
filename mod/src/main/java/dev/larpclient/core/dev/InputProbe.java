package dev.larpclient.core.dev;

import dev.larpclient.LarpClient;
import net.minecraft.client.Minecraft;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.Display;

import java.io.File;

/**
 * Developer tool for input latency: {@code -Dlarp.inputProbe=true} logs frame rate, worst frame time and
 * how far the cursor / camera moved, once per second. {@code -Dlarp.inputProbe=world} also opens the
 * first singleplayer world a few seconds after start, so grabbed-mouse (in-game) input can be measured
 * without clicking through menus. Pair with a virtual high-rate mouse to reproduce the XWayland freeze.
 */
public final class InputProbe {
    private final boolean loadWorld;
    private final long start = System.nanoTime();
    private long windowStart = start;
    private long lastFrame = start;
    private long worstFrame;
    private int frames;
    private boolean worldRequested;
    private float lastYaw = Float.NaN;
    private float lastPitch;
    private double look;
    private int lastX = -1;
    private int lastY;
    private long cursor;

    private InputProbe(boolean loadWorld) {
        this.loadWorld = loadWorld;
    }

    public static void installIfRequested() {
        String mode = System.getProperty("larp.inputProbe");
        if (mode != null && !mode.equals("false")) {
            MinecraftForge.EVENT_BUS.register(new InputProbe(mode.equals("world")));
        }
    }

    @SubscribeEvent
    public void onRender(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        long now = System.nanoTime();
        worstFrame = Math.max(worstFrame, now - lastFrame);
        lastFrame = now;
        frames++;

        if (mc.thePlayer != null) {
            if (!Float.isNaN(lastYaw)) {
                look += Math.abs(mc.thePlayer.rotationYaw - lastYaw) + Math.abs(mc.thePlayer.rotationPitch - lastPitch);
            }
            lastYaw = mc.thePlayer.rotationYaw;
            lastPitch = mc.thePlayer.rotationPitch;
        } else {
            lastYaw = Float.NaN;
        }
        int x = Mouse.getX();
        int y = Mouse.getY();
        if (lastX >= 0) {
            cursor += Math.abs(x - lastX) + Math.abs(y - lastY);
        }
        lastX = x;
        lastY = y;

        if (loadWorld && !worldRequested && now - start > 5_000_000_000L && mc.theWorld == null) {
            worldRequested = true;
            File[] saves = new File(mc.mcDataDir, "saves").listFiles(File::isDirectory);
            if (saves != null && saves.length > 0) {
                LarpClient.LOGGER.info("[input] loading world {}", saves[0].getName());
                mc.launchIntegratedServer(saves[0].getName(), saves[0].getName(), null);
            }
        }

        if (now - windowStart >= 1_000_000_000L) {
            LarpClient.LOGGER.info("[input] fps={} worstFrameMs={} active={} grabbed={} cursor={} look={} screen={}",
                    frames, worstFrame / 1_000_000, Display.isActive(), Mouse.isGrabbed(), cursor,
                    Math.round(look), mc.currentScreen == null ? "none" : mc.currentScreen.getClass().getSimpleName());
            windowStart = now;
            frames = 0;
            worstFrame = 0;
            cursor = 0;
            look = 0;
        }
    }
}
