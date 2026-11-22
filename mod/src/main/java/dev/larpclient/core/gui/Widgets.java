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

    /** Single-line text input rendered with Inter: cursor, Home/End, Delete, click-to-place, clipboard. */
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
        private int cursor;
        private int scroll; // index of the first visible character
        private long lastInput;

        public TextInput(float x, float y, float w, float h, String placeholder, int maxLength) {
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
            this.placeholder = placeholder;
            this.maxLength = maxLength;
        }

        public void setText(String value) {
            text = value == null ? "" : value;
            cursor = text.length();
            scroll = 0;
            selectAll = false;
        }

        private float inner() {
            return w - 12;
        }

        /** Keep the cursor inside the visible window. */
        private void clampScroll(LarpFont font) {
            cursor = Math.max(0, Math.min(text.length(), cursor));
            scroll = Math.max(0, Math.min(scroll, cursor));
            while (scroll < cursor && font.width(text.substring(scroll, cursor)) > inner()) {
                scroll++;
            }
        }

        private String visible(LarpFont font) {
            int end = scroll;
            while (end < text.length() && font.width(text.substring(scroll, end + 1)) <= inner()) {
                end++;
            }
            return text.substring(scroll, end);
        }

        public void draw() {
            if (cursor > text.length()) {
                cursor = text.length(); // text replaced from outside
            }
            Shapes.roundedRect(x, y, w, h, 4, focused ? 0x1CFFFFFF : 0x10FFFFFF);
            Shapes.roundedOutline(x, y, w, h, 4, 0.6f, focused ? 0xA08B5CF6 : 0x14FFFFFF);
            LarpFont font = Ui.BODY;
            clampScroll(font);
            float ty = y + (h - font.height()) / 2f;
            if (text.isEmpty()) {
                font.draw(placeholder, x + (focused ? 8 : 6), ty, 0x66FFFFFF);
            } else {
                String shown = visible(font);
                if (selectAll && focused) {
                    Shapes.rect(x + 6, ty, font.width(shown), font.height(), 0x608B5CF6);
                }
                font.draw(shown, x + 6, ty, 0xFFFFFFFF);
            }
            boolean blinkOn = (System.currentTimeMillis() - lastInput) < 500 || (System.currentTimeMillis() / 530) % 2 == 0;
            if (focused && blinkOn && !selectAll) {
                float cx = x + 6 + font.width(text.substring(scroll, cursor)) + 0.25f;
                Shapes.rect(cx, ty + 0.5f, 0.75f, font.height() - 1, 0xFFFFFFFF);
            }
        }

        public void mouseClicked(int mouseX, int mouseY) {
            focused = RenderUtil.hovered(mouseX, mouseY, x, y, w, h);
            selectAll = false;
            if (focused) {
                // place the cursor at the nearest character boundary
                LarpFont font = Ui.BODY;
                float rel = mouseX - (x + 6);
                int best = scroll;
                for (int i = scroll; i <= text.length(); i++) {
                    if (font.width(text.substring(scroll, i)) <= rel + 1.5f) {
                        best = i;
                    } else {
                        break;
                    }
                }
                cursor = best;
                lastInput = System.currentTimeMillis();
            }
        }

        public boolean keyTyped(char c, int keyCode) {
            if (!focused) {
                return false;
            }
            lastInput = System.currentTimeMillis();
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
            if (GuiScreen.isKeyComboCtrlX(keyCode)) {
                GuiScreen.setClipboardString(text);
                setText("");
                return true;
            }
            switch (keyCode) {
                case Keyboard.KEY_LEFT:
                    cursor = selectAll ? 0 : GuiScreen.isCtrlKeyDown() ? wordLeft() : Math.max(0, cursor - 1);
                    selectAll = false;
                    return true;
                case Keyboard.KEY_RIGHT:
                    cursor = selectAll ? text.length() : GuiScreen.isCtrlKeyDown() ? wordRight() : Math.min(text.length(), cursor + 1);
                    selectAll = false;
                    return true;
                case Keyboard.KEY_HOME:
                    cursor = 0;
                    selectAll = false;
                    return true;
                case Keyboard.KEY_END:
                    cursor = text.length();
                    selectAll = false;
                    return true;
                case Keyboard.KEY_BACK:
                    if (selectAll) {
                        setText("");
                    } else if (cursor > 0) {
                        int from = GuiScreen.isCtrlKeyDown() ? wordLeft() : cursor - 1;
                        text = text.substring(0, from) + text.substring(cursor);
                        cursor = from;
                    }
                    return true;
                case Keyboard.KEY_DELETE:
                    if (selectAll) {
                        setText("");
                    } else if (cursor < text.length()) {
                        int to = GuiScreen.isCtrlKeyDown() ? wordRight() : cursor + 1;
                        text = text.substring(0, cursor) + text.substring(to);
                    }
                    return true;
                default:
                    break;
            }
            if (ChatAllowedCharacters.isAllowedCharacter(c)) {
                insert(String.valueOf(c));
                return true;
            }
            return false;
        }

        private int wordLeft() {
            int i = cursor;
            while (i > 0 && !Character.isLetterOrDigit(text.charAt(i - 1))) {
                i--;
            }
            while (i > 0 && Character.isLetterOrDigit(text.charAt(i - 1))) {
                i--;
            }
            return i;
        }

        private int wordRight() {
            int i = cursor;
            while (i < text.length() && !Character.isLetterOrDigit(text.charAt(i))) {
                i++;
            }
            while (i < text.length() && Character.isLetterOrDigit(text.charAt(i))) {
                i++;
            }
            return i;
        }

        private void insert(String s) {
            if (selectAll) {
                setText("");
            }
            String clean = ChatAllowedCharacters.filterAllowedCharacters(s.replace("\n", "").replace("\r", "").trim());
            int room = maxLength - text.length();
            if (room <= 0) {
                return;
            }
            if (clean.length() > room) {
                clean = clean.substring(0, room);
            }
            text = text.substring(0, cursor) + clean + text.substring(cursor);
            cursor += clean.length();
        }
    }
}
