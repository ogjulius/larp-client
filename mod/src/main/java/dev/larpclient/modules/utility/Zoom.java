package dev.larpclient.modules.utility;

import dev.larpclient.LarpClient;
import dev.larpclient.core.event.events.FovEvent;
import dev.larpclient.core.event.events.MouseScrollEvent;
import dev.larpclient.core.module.Category;
import dev.larpclient.core.module.Module;
import dev.larpclient.core.setting.BooleanSetting;
import dev.larpclient.core.setting.NumberSetting;
import dev.larpclient.core.util.Animation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import org.lwjgl.input.Keyboard;

/** Hold-to-zoom with smooth animation and scroll adjustment. */
public final class Zoom extends Module {
    public static final KeyBinding KEY = new KeyBinding("Zoom", Keyboard.KEY_C, LarpClient.KEY_CATEGORY);

    private final NumberSetting factor = setting(new NumberSetting("Zoom Factor", "How far to zoom in", 4, 2, 15, 0.5));
    private final BooleanSetting smooth = setting(new BooleanSetting("Smooth Zoom", "Animate the transition", true));
    private final BooleanSetting scroll = setting(new BooleanSetting("Scroll To Adjust", "Mouse wheel changes zoom while held", true));
    private final BooleanSetting cinematic = setting(new BooleanSetting("Cinematic Camera", "Smooth mouse movement while zoomed", false));

    private final Animation animation = new Animation(1);
    private double scrollFactor = 1;
    private boolean zooming;
    private boolean savedSmoothCamera;

    public Zoom() {
        super("Zoom", "Hold a key to zoom in (default: C)", Category.UTILITY);
        on(FovEvent.class, this::onFov);
        on(MouseScrollEvent.class, this::onScroll);
    }

    @Override
    public boolean enabledByDefault() {
        return true;
    }

    private void onFov(FovEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        boolean held = KEY.isKeyDown() && mc.currentScreen == null;
        if (held != zooming) {
            zooming = held;
            if (held) {
                scrollFactor = 1;
                if (cinematic.isOn()) {
                    savedSmoothCamera = mc.gameSettings.smoothCamera;
                    mc.gameSettings.smoothCamera = true;
                }
            } else if (cinematic.isOn()) {
                mc.gameSettings.smoothCamera = savedSmoothCamera;
            }
        }
        double target = zooming ? factor.get() * scrollFactor : 1;
        animation.setTarget(target);
        double current = smooth.isOn() ? animation.update(System.currentTimeMillis(), 0.25) : target;
        if (!smooth.isOn()) {
            animation.snap(target);
        }
        if (current != 1) {
            event.fov /= (float) current;
        }
    }

    private void onScroll(MouseScrollEvent event) {
        if (zooming && scroll.isOn()) {
            scrollFactor *= event.delta > 0 ? 1.25 : 0.8;
            scrollFactor = Math.max(1 / factor.get(), Math.min(50 / factor.get(), scrollFactor));
            event.cancel();
        }
    }

    @Override
    protected void onDisable() {
        if (zooming && cinematic.isOn()) {
            Minecraft.getMinecraft().gameSettings.smoothCamera = savedSmoothCamera;
        }
        zooming = false;
        animation.snap(1);
    }
}
