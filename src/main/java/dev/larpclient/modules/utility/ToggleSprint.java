package dev.larpclient.modules.utility;

import dev.larpclient.core.event.events.ClientTickEvent;
import dev.larpclient.core.event.events.KeyPressEvent;
import dev.larpclient.core.hud.TextHudModule;
import dev.larpclient.core.setting.BooleanSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import org.lwjgl.input.Keyboard;

/** Press the sprint key once to keep sprinting. Shown in the HUD category as it has a status text. */
public final class ToggleSprint extends TextHudModule {
    private final BooleanSetting showText = setting(new BooleanSetting("Show Status", "Show sprint status on the HUD", true));
    private boolean toggled;

    public ToggleSprint() {
        super("Toggle Sprint", "Tap your sprint key to toggle sprinting", 0.0, 0.97);
        on(KeyPressEvent.class, e -> {
            if (e.keyCode == sprintKey().getKeyCode()) {
                toggled = !toggled;
            }
        });
        on(ClientTickEvent.class, e -> {
            if (toggled) {
                KeyBinding.setKeyBindState(sprintKey().getKeyCode(), true);
            }
        });
    }

    private static KeyBinding sprintKey() {
        return Minecraft.getMinecraft().gameSettings.keyBindSprint;
    }

    @Override
    protected void onDisable() {
        toggled = false;
        int code = sprintKey().getKeyCode();
        if (code > 0) {
            KeyBinding.setKeyBindState(code, Keyboard.isKeyDown(code));
        }
    }

    @Override
    protected String getText() {
        if (!showText.isOn()) {
            return null;
        }
        if (toggled) {
            return "[Sprinting (Toggled)]";
        }
        return sprintKey().isKeyDown() ? "[Sprinting (Key Held)]" : null;
    }

    @Override
    protected String getPlaceholder() {
        return "[Sprinting (Toggled)]";
    }
}
