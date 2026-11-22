package dev.larpclient.core.gui;

import dev.larpclient.core.setting.BooleanSetting;
import dev.larpclient.core.setting.ColorSetting;
import dev.larpclient.core.setting.KeySetting;
import dev.larpclient.core.setting.ModeSetting;
import dev.larpclient.core.setting.NumberSetting;
import dev.larpclient.core.setting.Setting;
import dev.larpclient.core.util.RenderUtil;
import org.lwjgl.input.Keyboard;

import java.awt.Color;

/** Interactive editor for a single {@link Setting}. */
public abstract class SettingWidget<S extends Setting<?>> {
    protected static final int ROW = 22;
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

    protected void label(int mouseX, int mouseY) {
        RenderUtil.text(setting.getName(), x + 6, y + 7, Theme.TEXT, false);
    }

    protected void background(int mouseX, int mouseY) {
        RenderUtil.rect(x, y, x + width, y + getHeight() - 2, hovered(mouseX, mouseY) ? Theme.CARD_HOVER : Theme.CARD);
    }

    // ------------------------------------------------------------------ implementations

    static final class Toggle extends SettingWidget<BooleanSetting> {
        Toggle(BooleanSetting setting) {
            super(setting);
        }

        @Override
        public void draw(int mouseX, int mouseY) {
            background(mouseX, mouseY);
            label(mouseX, mouseY);
            drawSwitch(x + width - 28, y + 5, setting.isOn());
        }

        @Override
        public void mouseClicked(int mouseX, int mouseY, int button) {
            if (button == 0 && hovered(mouseX, mouseY)) {
                setting.toggle();
            }
        }
    }

    public static void drawSwitch(int sx, int sy, boolean on) {
        RenderUtil.rect(sx, sy, sx + 22, sy + 10, on ? Theme.ACCENT : Theme.OFF);
        int knob = on ? sx + 13 : sx + 1;
        RenderUtil.rect(knob, sy + 1, knob + 8, sy + 9, Theme.TEXT);
    }

    static final class Slider extends SettingWidget<NumberSetting> {
        private boolean dragging;

        Slider(NumberSetting setting) {
            super(setting);
        }

        private int trackX() {
            return x + width / 2;
        }

        private int trackWidth() {
            return width / 2 - 34;
        }

        @Override
        public void draw(int mouseX, int mouseY) {
            background(mouseX, mouseY);
            label(mouseX, mouseY);
            int tx = trackX();
            int tw = trackWidth();
            int ty = y + 9;
            RenderUtil.rect(tx, ty, tx + tw, ty + 3, Theme.OFF);
            int filled = (int) (tw * setting.getPercent());
            RenderUtil.rect(tx, ty, tx + filled, ty + 3, Theme.ACCENT);
            RenderUtil.rect(tx + filled - 2, ty - 3, tx + filled + 2, ty + 6, Theme.TEXT);
            RenderUtil.text(setting.format(), tx + tw + 6, y + 7, Theme.TEXT_DIM, false);
        }

        @Override
        public void mouseClicked(int mouseX, int mouseY, int button) {
            if (button == 0 && RenderUtil.hovered(mouseX, mouseY, trackX() - 3, y, trackWidth() + 6, ROW)) {
                dragging = true;
                mouseDragged(mouseX, mouseY);
            }
        }

        @Override
        public void mouseDragged(int mouseX, int mouseY) {
            if (dragging) {
                setting.setPercent(Math.max(0, Math.min(1, (mouseX - trackX()) / (double) trackWidth())));
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
            background(mouseX, mouseY);
            label(mouseX, mouseY);
            String text = "◀ " + setting.get() + " ▶";
            int w = RenderUtil.font().getStringWidth(text);
            RenderUtil.text(text, x + width - w - 6, y + 7, Theme.ACCENT, false);
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
            background(mouseX, mouseY);
            label(mouseX, mouseY);
            String text = listening ? "> press a key <"
                    : setting.get() == KeySetting.NONE ? "None" : Keyboard.getKeyName(setting.get());
            int w = RenderUtil.font().getStringWidth(text);
            RenderUtil.text(text, x + width - w - 6, y + 7, listening ? Theme.ACCENT : Theme.TEXT_DIM, false);
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

        ColorPicker(ColorSetting setting) {
            super(setting);
        }

        @Override
        public int getHeight() {
            return ROW + 14;
        }

        private int sliderWidth() {
            return (width - 12 - 3 * 6) / 4;
        }

        private int sliderX(int i) {
            return x + 6 + i * (sliderWidth() + 6);
        }

        private int sliderY() {
            return y + ROW;
        }

        private float value(int i) {
            float[] hsb = setting.toHsb();
            return i < 3 ? hsb[i] : setting.getAlpha() / 255f;
        }

        @Override
        public void draw(int mouseX, int mouseY) {
            background(mouseX, mouseY);
            label(mouseX, mouseY);
            int sw = x + width - 22;
            RenderUtil.rect(sw - 1, y + 4, sw + 15, y + 18, Theme.BORDER);
            RenderUtil.rect(sw, y + 5, sw + 14, y + 17, setting.resolve() | 0xFF000000);
            if (setting.isChroma()) {
                RenderUtil.text("Chroma", sw - 40, y + 7, Theme.ACCENT, false);
            }
            float[] hsb = setting.toHsb();
            int w = sliderWidth();
            for (int i = 0; i < 4; i++) {
                int sx = sliderX(i);
                int sy = sliderY();
                // gradient preview, drawn in 2px strips
                for (int px = 0; px < w; px += 2) {
                    float t = px / (float) w;
                    int c;
                    switch (i) {
                        case 0: c = Color.HSBtoRGB(t, 1, 1); break;
                        case 1: c = Color.HSBtoRGB(hsb[0], t, hsb[2]); break;
                        case 2: c = Color.HSBtoRGB(hsb[0], hsb[1], t); break;
                        default: c = RenderUtil.blend(0xFF000000, setting.get() | 0xFF000000, t); break;
                    }
                    RenderUtil.rect(sx + px, sy, Math.min(sx + px + 2, sx + w), sy + 6, c | 0xFF000000);
                }
                int knob = sx + (int) (value(i) * w);
                RenderUtil.rect(knob - 1, sy - 2, knob + 1, sy + 8, Theme.TEXT);
                RenderUtil.text(LABELS[i], sx - 0.5f, sy - 9, Theme.TEXT_DIM, false);
            }
        }

        @Override
        public void mouseClicked(int mouseX, int mouseY, int button) {
            int sw = x + width - 22;
            if (button == 1 && RenderUtil.hovered(mouseX, mouseY, sw, y + 4, 16, 14)) {
                setting.setChroma(!setting.isChroma());
                return;
            }
            if (button != 0) {
                return;
            }
            for (int i = 0; i < 4; i++) {
                if (RenderUtil.hovered(mouseX, mouseY, sliderX(i) - 2, sliderY() - 3, sliderWidth() + 4, 12)) {
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
            float t = Math.max(0, Math.min(1, (mouseX - sliderX(dragging)) / (float) sliderWidth()));
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
