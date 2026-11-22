package dev.larpclient.modules.utility;

import dev.larpclient.LarpClient;
import dev.larpclient.core.ServerPolicy;
import dev.larpclient.core.event.events.ClientTickEvent;
import dev.larpclient.core.event.events.KeyPressEvent;
import dev.larpclient.core.module.Category;
import dev.larpclient.core.module.Module;
import dev.larpclient.core.setting.BooleanSetting;
import dev.larpclient.core.setting.ModeSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.util.MathHelper;
import org.lwjgl.input.Keyboard;

/**
 * Look around without changing the direction you move or face. Applied by
 * {@code MixinEntity} (mouse input) and {@code MixinEntityRenderer} (camera orientation).
 */
public final class Freelook extends Module {
    public static final KeyBinding KEY = new KeyBinding("Freelook", Keyboard.KEY_LMENU, LarpClient.KEY_CATEGORY);
    /** Servers that ask clients not to provide freelook. */
    private static final String[] DISALLOWED = {"hypixel.net"};

    private static Freelook instance;

    private final ModeSetting mode = setting(new ModeSetting("Mode", "Hold or toggle the key", "Hold", "Hold", "Toggle"));
    private final BooleanSetting thirdPerson = setting(new BooleanSetting("Third Person", "Switch to third person while looking", true));
    private final BooleanSetting invertPitch = setting(new BooleanSetting("Invert Pitch", "Invert vertical camera movement", false));

    private boolean active;
    private float cameraYaw;
    private float cameraPitch;
    private int savedPerspective;

    public Freelook() {
        super("Freelook", "Hold a key to look around freely (default: Left Alt)", Category.UTILITY);
        instance = this;
        on(ClientTickEvent.class, e -> tick());
        on(KeyPressEvent.class, e -> {
            if (mode.is("Toggle") && e.keyCode == KEY.getKeyCode()) {
                setActive(!active);
            }
        });
    }

    /** Non-null instance if freelook is currently steering the camera. */
    public static boolean isActive() {
        return instance != null && instance.active;
    }

    public static float getCameraYaw() {
        return instance.cameraYaw;
    }

    public static float getCameraPitch() {
        return instance.cameraPitch;
    }

    /** Apply raw mouse deltas (same scaling as {@code Entity#setAngles}). */
    public static void rotate(float yaw, float pitch) {
        Freelook f = instance;
        f.cameraYaw += yaw * 0.15f;
        f.cameraPitch += (f.invertPitch.isOn() ? pitch : -pitch) * 0.15f;
        f.cameraPitch = MathHelper.clamp_float(f.cameraPitch, -90f, 90f);
    }

    @Override
    public String blockedReason() {
        return ServerPolicy.isOn(DISALLOWED) ? "Disabled on this server at the server's request" : null;
    }

    private void tick() {
        if (mode.is("Hold")) {
            boolean held = KEY.isKeyDown() && Minecraft.getMinecraft().currentScreen == null;
            if (held != active) {
                setActive(held);
            }
        }
    }

    private void setActive(boolean value) {
        Minecraft mc = Minecraft.getMinecraft();
        if (value == active || mc.thePlayer == null) {
            return;
        }
        active = value;
        if (value) {
            cameraYaw = mc.thePlayer.rotationYaw;
            cameraPitch = mc.thePlayer.rotationPitch;
            savedPerspective = mc.gameSettings.thirdPersonView;
            if (thirdPerson.isOn() && savedPerspective == 0) {
                mc.gameSettings.thirdPersonView = 1;
            }
        } else {
            mc.gameSettings.thirdPersonView = savedPerspective;
        }
    }

    @Override
    protected void onDisable() {
        setActive(false);
    }
}
