package dev.larpclient.core.gui;

import dev.larpclient.core.render.LarpFont;
import dev.larpclient.core.render.Shapes;
import dev.larpclient.core.render.Ui;
import dev.larpclient.core.util.RenderUtil;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.util.ChatAllowedCharacters;
import org.lwjgl.input.Keyboard;

/** Small reusable pieces of LARP's GUI design system. */
public final class Widgets {
    public static final float RADIUS = 7;

    private Widgets() {
    }

    /** Floating panel surface. */
    public static void panel(float x, float y, float w, float h) {
        Shapes.roundedRect(x - 1, y - 1, w + 2, h + 2, RADIUS + 1, 0x40000000);
        Shapes.roundedRect(x, y, w, h, RADIUS, 0xF2121217);
        Shapes.roundedOutline(x, y, w, h, RADIUS, 0.5f, 0x1AFFFFFF);
    }

    public static void cross(float x, float y, float s, int argb) {
        float t = Math.max(1f, s * 0.17f);
        Shapes.line(x, y, x + s, y + s, t, argb);
        Shapes.line(x + s, y, x, y + s, t, argb);
    }

    public static void closeButton(float x, float y, int mouseX, int mouseY) {
        boolean hover = RenderUtil.hovered(mouseX, mouseY, x - 2, y - 2, 16, 16);
        if (hover) {
            Shapes.circle(x + 6, y + 6, 8, 0x18FFFFFF);
        }
        cross(x + 2.5f, y + 2.5f, 7, hover ? 0xFFFFFFFF : 0x99FFFFFF);
    }

    /** iOS-style switch, 18×10. */
    public static void toggle(float x, float y, boolean on) {
        Shapes.roundedRect(x, y, 18, 10, 5, on ? Theme.ACCENT : 0x30FFFFFF);
        Shapes.circle(on ? x + 13 : x + 5, y + 5, 3.6f, 0xFFFFFFFF);
    }

    public static float labeledSwitchWidth(String label) {
        return Ui.SMALL.width(label) + 6 + 18;
    }

    /** Right-aligned "Label [switch]"; returns the left edge. */
    public static float labeledSwitch(String label, float right, float y, boolean on) {
        toggle(right - 18, y, on);
        float lx = right - labeledSwitchWidth(label);
        Ui.SMALL.draw(label, lx, y + 1.5f, 0xB0FFFFFF);
        return lx;
    }

    public static void slider(float x, float y, float w, float value) {
        value = Math.max(0, Math.min(1, value));
        Shapes.roundedRect(x, y - 1.5f, w, 3, 1.5f, 0x30FFFFFF);
        Shapes.roundedRect(x, y - 1.5f, Math.max(3, w * value), 3, 1.5f, Theme.ACCENT);
        Shapes.circle(x + w * value, y, 4, 0xFFFFFFFF);
    }

    /** Minimal single-line input rendered with Inter. */
    public static final class TextInput {
        public final float x;
        public final float y;
        public final float w;
        public final float h;
        public String text = "";
        public boolean focused;
        private final String placeholder;
        private final int maxLength;
        private boolean selectAll;

        public TextInput(float x, float y, float w, float h, String placeholder, int maxLength) {
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
            this.placeholder = placeholder;
            this.maxLength = maxLength;
        }

        public void draw() {
            Shapes.roundedRect(x, y, w, h, 4, focused ? 0x1CFFFFFF : 0x10FFFFFF);
            Shapes.roundedOutline(x, y, w, h, 4, 0.6f, focused ? 0xA08B5CF6 : 0x14FFFFFF);
            LarpFont font = Ui.BODY;
            float ty = y + (h - font.height()) / 2f;
            float inner = w - 12;
            if (text.isEmpty()) {
                font.draw(placeholder, x + (focused ? 8 : 6), ty, 0x66FFFFFF);
            } else {
                String visible = text;
                while (font.width(visible) > inner && visible.length() > 1) {
                    visible = visible.substring(1);
                }
                if (selectAll && focused) {
                    Shapes.rect(x + 6, ty, font.width(visible), font.height(), 0x608B5CF6);
                }
                font.draw(visible, x + 6, ty, 0xFFFFFFFF);
            }
            if (focused && (System.currentTimeMillis() / 530) % 2 == 0 && !selectAll) {
                String visible = text;
                while (font.width(visible) > inner && visible.length() > 1) {
                    visible = visible.substring(1);
                }
                float cx = x + 6 + font.width(visible) + 0.5f;
                Shapes.rect(cx, ty + 0.5f, 0.75f, font.height() - 1, 0xFFFFFFFF);
            }
        }

        public void mouseClicked(int mouseX, int mouseY) {
            focused = RenderUtil.hovered(mouseX, mouseY, x, y, w, h);
            selectAll = false;
        }

        public boolean keyTyped(char c, int keyCode) {
            if (!focused) {
                return false;
            }
            if (GuiScreen.isKeyComboCtrlA(keyCode)) {
                selectAll = true;
                return true;
            }
            if (GuiScreen.isKeyComboCtrlV(keyCode)) {
                insert(GuiScreen.getClipboardString());
                return true;
            }
            if (GuiScreen.isKeyComboCtrlC(keyCode)) {
                GuiScreen.setClipboardString(text);
                return true;
            }
            if (keyCode == Keyboard.KEY_BACK) {
                if (selectAll || GuiScreen.isCtrlKeyDown()) {
                    text = "";
                } else if (!text.isEmpty()) {
                    text = text.substring(0, text.length() - 1);
                }
                selectAll = false;
                return true;
            }
            if (ChatAllowedCharacters.isAllowedCharacter(c)) {
                insert(String.valueOf(c));
                return true;
            }
            return false;
        }

        private void insert(String s) {
            if (selectAll) {
                text = "";
                selectAll = false;
            }
            text = ChatAllowedCharacters.filterAllowedCharacters(text + s.replace("\n", "").replace("\r", ""));
            if (text.length() > maxLength) {
                text = text.substring(0, maxLength);
            }
        }
    }
}
