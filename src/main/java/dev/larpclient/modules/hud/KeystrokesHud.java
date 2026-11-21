package dev.larpclient.modules.hud;

import dev.larpclient.LarpClient;
import dev.larpclient.core.hud.HudModule;
import dev.larpclient.core.setting.BooleanSetting;
import dev.larpclient.core.setting.ColorSetting;
import dev.larpclient.core.util.Animation;
import dev.larpclient.core.util.RenderUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.client.settings.KeyBinding;

/** WASD / mouse / space key overlay with smooth press fading. */
public final class KeystrokesHud extends HudModule {
    private static final int KEY = 22;
    private static final int GAP = 2;
    private static final int WIDTH = KEY * 3 + GAP * 2;

    private final BooleanSetting showMouse = setting(new BooleanSetting("Show Mouse", "Show LMB/RMB keys", true));
    private final BooleanSetting showCps = setting(new BooleanSetting("Show CPS", "Show CPS on the mouse keys", true)
            .visibleWhen(showMouse::isOn));
    private final BooleanSetting showSpace = setting(new BooleanSetting("Show Space", "Show the jump key", true));
    private final ColorSetting idleColor = setting(new ColorSetting("Idle Color", "Key background", 0x6F000000));
    private final ColorSetting pressedColor = setting(new ColorSetting("Pressed Color", "Pressed key background", 0xAFFFFFFF));
    private final ColorSetting textColor = setting(new ColorSetting("Text Color", "Key label colour", 0xFFFFFFFF));
    private final ColorSetting pressedTextColor = setting(new ColorSetting("Pressed Text Color", "Pressed label colour", 0xFF000000));

    private final Animation[] fades = new Animation[7];

    public KeystrokesHud() {
        super("Keystrokes", "Shows movement keys and mouse buttons", 0.0, 0.5);
        for (int i = 0; i < fades.length; i++) {
            fades[i] = new Animation(0);
        }
    }

    @Override
    public boolean enabledByDefault() {
        return true;
    }

    @Override
    public int getWidth() {
        return WIDTH;
    }

    @Override
    public int getHeight() {
        int h = KEY * 2 + GAP;
        if (showMouse.isOn()) {
            h += GAP + KEY;
        }
        if (showSpace.isOn()) {
            h += GAP + 12;
        }
        return h;
    }

    @Override
    public void render(float partialTicks, boolean editing) {
        GameSettings gs = Minecraft.getMinecraft().gameSettings;
        long now = System.currentTimeMillis();
        int col2 = KEY + GAP;
        int col3 = (KEY + GAP) * 2;

        key(0, gs.keyBindForward, col2, 0, KEY, KEY, null, now);
        key(1, gs.keyBindLeft, 0, KEY + GAP, KEY, KEY, null, now);
        key(2, gs.keyBindBack, col2, KEY + GAP, KEY, KEY, null, now);
        key(3, gs.keyBindRight, col3, KEY + GAP, KEY, KEY, null, now);

        int y = (KEY + GAP) * 2;
        if (showMouse.isOn()) {
            int half = (WIDTH - GAP) / 2;
            String lmb = showCps.isOn() ? LarpClient.get().leftCps().getCps(now) + " CPS" : "LMB";
            String rmb = showCps.isOn() ? LarpClient.get().rightCps().getCps(now) + " CPS" : "RMB";
            key(4, gs.keyBindAttack, 0, y, half, KEY, lmb, now);
            key(5, gs.keyBindUseItem, half + GAP, y, WIDTH - half - GAP, KEY, rmb, now);
            y += KEY + GAP;
        }
        if (showSpace.isOn()) {
            key(6, gs.keyBindJump, 0, y, WIDTH, 12, "———", now);
        }
    }

    private void key(int index, KeyBinding binding, int x, int y, int w, int h, String label, long now) {
        Animation fade = fades[index];
        fade.setTarget(binding.isKeyDown() ? 1 : 0);
        float t = (float) fade.update(now, 0.35);
        RenderUtil.rect(x, y, x + w, y + h, RenderUtil.blend(idleColor.resolve(), pressedColor.resolve(), t));
        String text = label != null ? label : keyName(binding);
        int color = RenderUtil.blend(textColor.resolve(), pressedTextColor.resolve(), t);
        RenderUtil.centeredText(text, x + w / 2f, y + (h - 8) / 2f, color, false);
    }

    private static String keyName(KeyBinding binding) {
        String name = GameSettings.getKeyDisplayString(binding.getKeyCode());
        return name.length() > 3 ? name.substring(0, 3) : name;
    }
}
