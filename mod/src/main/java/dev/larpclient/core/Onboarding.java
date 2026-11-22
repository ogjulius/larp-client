package dev.larpclient.core;

import dev.larpclient.core.event.EventBus;
import dev.larpclient.core.event.events.Render2DEvent;
import dev.larpclient.core.event.events.WorldLoadEvent;
import dev.larpclient.core.render.Shapes;
import dev.larpclient.core.render.Ui;
import net.minecraft.client.settings.KeyBinding;
import org.lwjgl.input.Keyboard;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** One-time hint on the first world join explaining how to reach LARP's features. */
public final class Onboarding {
    private static final long DURATION_MS = 12_000;
    private final Path marker;
    private final KeyBinding menuKey;
    private final KeyBinding zoomKey;
    private boolean pending;
    private long visibleMs;
    private long lastFrame;

    public Onboarding(Path dataDir, EventBus bus, KeyBinding menuKey, KeyBinding zoomKey) {
        this.marker = dataDir.resolve(".onboarded");
        this.menuKey = menuKey;
        this.zoomKey = zoomKey;
        if (Files.exists(marker)) {
            return;
        }
        bus.subscribe(WorldLoadEvent.class, e -> {
            if (!pending && visibleMs == 0) {
                pending = true;
                visibleMs = -1500; // let the world appear before showing it
                try {
                    Files.createDirectories(marker.getParent());
                    Files.createFile(marker);
                } catch (IOException ignored) {
                    // worst case the hint shows again next time
                }
            }
        });
        bus.subscribe(Render2DEvent.class, this::render);
    }

    private static String key(KeyBinding binding) {
        int code = binding.getKeyCode();
        return code <= 0 ? "unbound" : Keyboard.getKeyName(code).replace("RSHIFT", "Right Shift").replace("LMENU", "Left Alt");
    }

    private void render(Render2DEvent event) {
        long now = System.currentTimeMillis();
        long delta = lastFrame == 0 ? 0 : Math.min(100, now - lastFrame);
        lastFrame = now;
        if (!pending) {
            return;
        }
        // Only show (and only count down) while playing, not behind menus.
        if (net.minecraft.client.Minecraft.getMinecraft().currentScreen != null) {
            return;
        }
        visibleMs += delta;
        long t = visibleMs;
        if (t < 0) {
            return;
        }
        if (t > DURATION_MS) {
            pending = false;
            return;
        }
        float in = Math.min(1, t / 300f) * Math.min(1, (DURATION_MS - t) / 400f);
        String title = "Welcome to LARP Client";
        String line = "Press " + key(menuKey) + " for mods & HUD   ·   Hold " + key(zoomKey) + " to zoom";
        float w = Math.max(Ui.BODY_STRONG.width(title), Ui.SMALL.width(line)) + 24;
        float x = (event.scaledWidth - w) / 2f;
        float y = 18 - (1 - in) * 8;
        int a = (int) (in * 255);
        Shapes.roundedRect(x, y, w, 30, 6, (int) (in * 0xE0) << 24 | 0x101014);
        Shapes.roundedOutline(x, y, w, 30, 6, 0.5f, (int) (in * 0x50) << 24 | 0x8B5CF6);
        Ui.BODY_STRONG.drawCentered(title, x + w / 2f, y + 5, a << 24 | 0xFFFFFF);
        Ui.SMALL.drawCentered(line, x + w / 2f, y + 17, a << 24 | 0xC0C0CC);
    }
}
