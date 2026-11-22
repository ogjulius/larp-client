package dev.larpclient.core.gui;

import dev.larpclient.core.render.Shapes;
import dev.larpclient.core.render.Ui;
import dev.larpclient.core.setting.BooleanSetting;
import dev.larpclient.core.setting.ColorSetting;
import dev.larpclient.core.setting.KeySetting;
import dev.larpclient.core.setting.ModeSetting;
import dev.larpclient.core.setting.NumberSetting;
import dev.larpclient.core.setting.Setting;
import dev.larpclient.core.util.RenderUtil;
import org.lwjgl.input.Keyboard;

import java.awt.Color;

/** Interactive editor row for a single {@link Setting}. */
public abstract class SettingWidget<S extends Setting<?>> {
    protected static final int ROW = 24;
    protected final S setting;
    protected int x;
    protected int y;
    protected int width;

    protected SettingWidget(S setting) {
        this.setting = setting;
    }

    public static SettingWidget<?> create(Setting<?> setting) {
        if (setting instanceof BooleanSetting) {
            return new Toggle((BooleanSetting) setting);
        } else if (setting instanceof NumberSetting) {
            return new Slider((NumberSetting) setting);
        } else if (setting instanceof ModeSetting) {
            return new Mode((ModeSetting) setting);
        } else if (setting instanceof ColorSetting) {
            return new ColorPicker((ColorSetting) setting);
        } else if (setting instanceof KeySetting) {
            return new KeyBind((KeySetting) setting);
        }
        throw new IllegalArgumentException("No widget for " + setting.getClass());
    }

    public Setting<?> getSetting() {
        return setting;
    }

    public int getHeight() {
        return ROW;
    }

    public void layout(int x, int y, int width) {
        this.x = x;
        this.y = y;
        this.width = width;
    }

    public abstract void draw(int mouseX, int mouseY);

    public void mouseClicked(int mouseX, int mouseY, int button) {
    }

    public void mouseDragged(int mouseX, int mouseY) {
    }

    public void mouseReleased() {
    }

    /** @return true if the key was consumed */
    public boolean keyTyped(char c, int keyCode) {
        return false;
    }

    protected boolean hovered(int mouseX, int mouseY) {
        return RenderUtil.hovered(mouseX, mouseY, x, y, width, getHeight());
    }

    /** Row background + label; subclasses draw their control on the right. */
    protected void row(int mouseX, int mouseY) {
        if (hovered(mouseX, mouseY)) {
            Shapes.roundedRect(x, y, width, getHeight() - 2, 4, 0x0CFFFFFF);
        }
        Ui.text(setting.getName(), x + 8, y + 8, 0xE6FFFFFF);
    }

    /** Kept for callers outside the GUI package. */
    public static void drawSwitch(int sx, int sy, boolean on) {
        Widgets.toggle(sx, sy, on);
    }

    // ------------------------------------------------------------------ implementations

    static final class Toggle extends SettingWidget<BooleanSetting> {
        Toggle(BooleanSetting setting) {
            super(setting);
        }

        @Override
        public void draw(int mouseX, int mouseY) {
            row(mouseX, mouseY);
            Widgets.toggle(x + width - 26, y + 6, setting.isOn());
        }

        @Override
        public void mouseClicked(int mouseX, int mouseY, int button) {
            if (button == 0 && hovered(mouseX, mouseY)) {
                setting.toggle();
            }
        }
    }

    static final class Slider extends SettingWidget<NumberSetting> {
        private boolean dragging;

        Slider(NumberSetting setting) {
            super(setting);
        }

        private float trackX() {
            return x + width * 0.45f;
        }

        private float trackWidth() {
            return width * 0.55f - 44;
        }

        @Override
        public void draw(int mouseX, int mouseY) {
            row(mouseX, mouseY);
            Widgets.slider(trackX(), y + 11, trackWidth(), (float) setting.getPercent());
            Ui.BODY.drawRight(setting.format(), x + width - 8, y + 7, 0xB0FFFFFF);
        }

        @Override
        public void mouseClicked(int mouseX, int mouseY, int button) {
            if (button == 0 && RenderUtil.hovered(mouseX, mouseY, trackX() - 4, y, trackWidth() + 8, ROW)) {
                dragging = true;
                mouseDragged(mouseX, mouseY);
            }
        }

        @Override
        public void mouseDragged(int mouseX, int mouseY) {
            if (dragging) {
                setting.setPercent(Math.max(0, Math.min(1, (mouseX - trackX()) / trackWidth())));
            }
        }

        @Override
        public void mouseReleased() {
            dragging = false;
        }
    }

    static final class Mode extends SettingWidget<ModeSetting> {
        Mode(ModeSetting setting) {
            super(setting);
        }

        @Override
        public void draw(int mouseX, int mouseY) {
            row(mouseX, mouseY);
            String value = setting.get();
            float w = Ui.BODY_STRONG.width(value) + 26;
            float bx = x + width - 8 - w;
            Shapes.roundedRect(bx, y + 4, w, 14, 4, 0x14FFFFFF);
            Shapes.triangle(bx + 7, y + 11, bx + 10, y + 8, bx + 10, y + 14, 0x99FFFFFF);
            Shapes.triangle(bx + w - 7, y + 11, bx + w - 10, y + 14, bx + w - 10, y + 8, 0x99FFFFFF);
            Ui.BODY_STRONG.drawCentered(value, bx + w / 2f, y + 6.5f, 0xFFFFFFFF);
        }

        @Override
        public void mouseClicked(int mouseX, int mouseY, int button) {
            if (hovered(mouseX, mouseY) && (button == 0 || button == 1)) {
                setting.cycle(button == 0);
            }
        }
    }

    static final class KeyBind extends SettingWidget<KeySetting> {
        private boolean listening;

        KeyBind(KeySetting setting) {
            super(setting);
        }

        @Override
        public void draw(int mouseX, int mouseY) {
            row(mouseX, mouseY);
            String text = listening ? "Press a key…"
                    : setting.get() == KeySetting.NONE ? "None" : Keyboard.getKeyName(setting.get());
            float w = Math.max(34, Ui.BODY_STRONG.width(text) + 14);
            float bx = x + width - 8 - w;
            Shapes.roundedRect(bx, y + 4, w, 14, 4, listening ? 0x408B5CF6 : 0x14FFFFFF);
            if (listening) {
                Shapes.roundedOutline(bx, y + 4, w, 14, 4, 0.6f, Theme.ACCENT);
            }
            Ui.BODY_STRONG.drawCentered(text, bx + w / 2f, y + 6.5f, 0xFFFFFFFF);
        }

        @Override
        public void mouseClicked(int mouseX, int mouseY, int button) {
            if (hovered(mouseX, mouseY)) {
                if (button == 0) {
                    listening = !listening;
                } else if (button == 1) {
                    setting.set(KeySetting.NONE);
                    listening = false;
                }
            } else {
                listening = false;
            }
        }

        @Override
        public boolean keyTyped(char c, int keyCode) {
            if (!listening) {
                return false;
            }
            setting.set(keyCode == Keyboard.KEY_ESCAPE || keyCode == Keyboard.KEY_BACK ? KeySetting.NONE : keyCode);
            listening = false;
            return true;
        }
    }

    /** HSB + alpha sliders; right-click the swatch to toggle chroma (rainbow). */
    static final class ColorPicker extends SettingWidget<ColorSetting> {
        private static final String[] LABELS = {"H", "S", "B", "A"};
        private int dragging = -1;
        private boolean expanded;

        ColorPicker(ColorSetting setting) {
            super(setting);
        }

        @Override
        public int getHeight() {
            return expanded ? ROW + 22 : ROW;
        }

        private float sliderWidth() {
            return (width - 16 - 3 * 14) / 4f;
        }

        private float sliderX(int i) {
            return x + 8 + i * (sliderWidth() + 14);
        }

        private float sliderY() {
            return y + ROW + 9;
        }

        private float value(int i) {
            float[] hsb = setting.toHsb();
            return i < 3 ? hsb[i] : setting.getAlpha() / 255f;
        }

        @Override
        public void draw(int mouseX, int mouseY) {
            row(mouseX, mouseY);
            float sw = x + width - 26;
            Shapes.roundedRect(sw, y + 4, 18, 14, 4, setting.resolve() | 0xFF000000);
            Shapes.roundedOutline(sw, y + 4, 18, 14, 4, 0.6f, 0x40FFFFFF);
            String hex = setting.isChroma() ? "Chroma" : String.format("#%06X", setting.get() & 0xFFFFFF);
            Ui.SMALL.drawRight(hex, sw - 6, y + 8, setting.isChroma() ? Theme.ACCENT : 0x80FFFFFF);
            if (!expanded) {
                return;
            }
            float[] hsb = setting.toHsb();
            float w = sliderWidth();
            for (int i = 0; i < 4; i++) {
                float sx = sliderX(i);
                float sy = sliderY();
                int segments = 12;
                for (int s = 0; s < segments; s++) {
                    float t0 = s / (float) segments;
                    float t1 = (s + 1) / (float) segments;
                    Shapes.horizontalGradient(sx + w * t0, sy, w / segments + 0.1f, 4, sample(i, t0, hsb), sample(i, t1, hsb));
                }
                Shapes.circle(sx + value(i) * w, sy + 2, 3.5f, 0xFFFFFFFF);
                Ui.LABEL.draw(LABELS[i], sx, sy - 9, 0x80FFFFFF, 1f);
            }
        }

        private int sample(int channel, float t, float[] hsb) {
            switch (channel) {
                case 0: return Color.HSBtoRGB(t, 1, 1) | 0xFF000000;
                case 1: return Color.HSBtoRGB(hsb[0], t, hsb[2]) | 0xFF000000;
                case 2: return Color.HSBtoRGB(hsb[0], hsb[1], t) | 0xFF000000;
                default: return RenderUtil.blend(0xFF202028, setting.get() | 0xFF000000, t);
            }
        }

        @Override
        public void mouseClicked(int mouseX, int mouseY, int button) {
            float sw = x + width - 26;
            if (button == 1 && RenderUtil.hovered(mouseX, mouseY, sw, y + 4, 18, 14)) {
                setting.setChroma(!setting.isChroma());
                return;
            }
            if (button != 0) {
                return;
            }
            if (RenderUtil.hovered(mouseX, mouseY, x, y, width, ROW)) {
                expanded = !expanded;
                return;
            }
            if (!expanded) {
                return;
            }
            for (int i = 0; i < 4; i++) {
                if (RenderUtil.hovered(mouseX, mouseY, sliderX(i) - 4, sliderY() - 4, sliderWidth() + 8, 12)) {
                    dragging = i;
                    mouseDragged(mouseX, mouseY);
                    return;
                }
            }
        }

        @Override
        public void mouseDragged(int mouseX, int mouseY) {
            if (dragging < 0) {
                return;
            }
            float t = Math.max(0, Math.min(1, (mouseX - sliderX(dragging)) / sliderWidth()));
            if (dragging == 3) {
                setting.setAlpha(Math.round(t * 255));
                return;
            }
            float[] hsb = setting.toHsb();
            hsb[dragging] = Math.min(t, dragging == 0 ? 0.999f : 1f);
            setting.setHsb(hsb[0], hsb[1], hsb[2]);
        }

        @Override
        public void mouseReleased() {
            dragging = -1;
        }
    }
}
