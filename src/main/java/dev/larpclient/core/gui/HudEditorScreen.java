package dev.larpclient.core.gui;

import dev.larpclient.LarpClient;
import dev.larpclient.core.hud.HudModule;
import dev.larpclient.core.hud.HudRenderer;
import dev.larpclient.core.util.RenderUtil;
import net.minecraft.client.gui.GuiScreen;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.io.IOException;
import java.util.List;

/**
 * Drag HUD elements around. Elements snap to screen edges, the screen centre and to each other.
 * Scroll over an element to resize it; right-click to open its settings.
 */
public final class HudEditorScreen extends GuiScreen {
    private static final int SNAP = 4;

    private HudModule dragging;
    private float grabX;
    private float grabY;
    private Integer guideX;
    private Integer guideY;

    private List<HudModule> huds() {
        return LarpClient.get().hud().getHudModules();
    }

    private HudModule elementAt(int mouseX, int mouseY) {
        List<HudModule> list = huds();
        for (int i = list.size() - 1; i >= 0; i--) {
            HudModule hud = list.get(i);
            if (hud.isEnabled() && RenderUtil.hovered(mouseX, mouseY, hud.getScreenX(width), hud.getScreenY(height),
                    hud.getScaledWidth(), hud.getScaledHeight())) {
                return hud;
            }
        }
        return null;
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        RenderUtil.rect(0, 0, width, height, 0x40000000);
        if (dragging != null) {
            if (guideX != null) {
                RenderUtil.rect(guideX, 0, guideX + 1, height, Theme.ACCENT);
            }
            if (guideY != null) {
                RenderUtil.rect(0, guideY, width, guideY + 1, Theme.ACCENT);
            }
        } else {
            RenderUtil.rect(width / 2f, 0, width / 2f + 1, height, 0x20FFFFFF);
            RenderUtil.rect(0, height / 2f, width, height / 2f + 1, 0x20FFFFFF);
        }
        HudModule hovered = dragging != null ? dragging : elementAt(mouseX, mouseY);
        for (HudModule hud : huds()) {
            if (!hud.isEnabled()) {
                continue;
            }
            float x = hud.getScreenX(width);
            float y = hud.getScreenY(height);
            RenderUtil.rect(x - 1, y - 1, x + hud.getScaledWidth() + 1, y + hud.getScaledHeight() + 1,
                    hud == hovered ? 0x408B5CF6 : 0x20FFFFFF);
            RenderUtil.outline(x - 1, y - 1, x + hud.getScaledWidth() + 1, y + hud.getScaledHeight() + 1,
                    hud == hovered ? Theme.ACCENT : 0x60FFFFFF);
            HudRenderer.draw(hud, width, height, partialTicks, true);
        }
        String hint = "Drag to move • Scroll to resize • Right-click for settings • ESC to go back";
        RenderUtil.centeredText(hint, width / 2f, height - 14, Theme.TEXT_DIM, true);
        if (hovered != null && dragging == null) {
            RenderUtil.text(hovered.getName() + String.format(" (%.0f%%)", hovered.getScale() * 100),
                    mouseX + 8, mouseY - 10, Theme.TEXT, true);
        }
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) throws IOException {
        HudModule hud = elementAt(mouseX, mouseY);
        if (hud == null) {
            return;
        }
        if (button == 0) {
            dragging = hud;
            grabX = mouseX - hud.getScreenX(width);
            grabY = mouseY - hud.getScreenY(height);
        } else if (button == 1) {
            mc.displayGuiScreen(new ModMenuScreen(hud));
        }
    }

    @Override
    protected void mouseClickMove(int mouseX, int mouseY, int button, long timeSinceLastClick) {
        if (dragging == null) {
            return;
        }
        float w = dragging.getScaledWidth();
        float h = dragging.getScaledHeight();
        float x = mouseX - grabX;
        float y = mouseY - grabY;
        guideX = null;
        guideY = null;
        if (!Keyboard.isKeyDown(Keyboard.KEY_LSHIFT)) {
            float[] snappedX = snap(x, w, width, true);
            float[] snappedY = snap(y, h, height, false);
            x = snappedX[0];
            y = snappedY[0];
            guideX = snappedX[1] < 0 ? null : (int) snappedX[1];
            guideY = snappedY[1] < 0 ? null : (int) snappedY[1];
        }
        x = Math.max(0, Math.min(width - w, x));
        y = Math.max(0, Math.min(height - h, y));
        dragging.setScreenPosition(x, y, width, height);
    }

    /** Returns {snapped position, guide line coordinate or -1}. */
    private float[] snap(float pos, float size, int screen, boolean horizontal) {
        float[] targets = new float[2 + huds().size() * 2];
        int n = 0;
        targets[n++] = 0;
        targets[n++] = screen;
        for (HudModule other : huds()) {
            if (other == dragging || !other.isEnabled()) {
                continue;
            }
            float start = horizontal ? other.getScreenX(width) : other.getScreenY(height);
            float len = horizontal ? other.getScaledWidth() : other.getScaledHeight();
            targets[n++] = start;
            targets[n++] = start + len;
        }
        // centre line
        if (Math.abs(pos + size / 2 - screen / 2f) < SNAP) {
            return new float[]{screen / 2f - size / 2, screen / 2f};
        }
        for (int i = 0; i < n; i++) {
            float t = targets[i];
            if (Math.abs(pos - t) < SNAP) {
                return new float[]{t, t};
            }
            if (Math.abs(pos + size - t) < SNAP) {
                return new float[]{t - size, t};
            }
        }
        return new float[]{pos, -1};
    }

    @Override
    protected void mouseReleased(int mouseX, int mouseY, int state) {
        dragging = null;
        guideX = null;
        guideY = null;
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel == 0) {
            return;
        }
        int mouseX = Mouse.getEventX() * width / mc.displayWidth;
        int mouseY = height - Mouse.getEventY() * height / mc.displayHeight - 1;
        HudModule hud = elementAt(mouseX, mouseY);
        if (hud != null) {
            float x = hud.getScreenX(width);
            float y = hud.getScreenY(height);
            hud.setScale(hud.getScale() + (wheel > 0 ? 0.05 : -0.05));
            hud.setScreenPosition(Math.min(x, width - hud.getScaledWidth()), Math.min(y, height - hud.getScaledHeight()),
                    width, height);
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (keyCode == Keyboard.KEY_ESCAPE) {
            mc.displayGuiScreen(new ModMenuScreen());
            return;
        }
        super.keyTyped(typedChar, keyCode);
    }

    @Override
    public void onGuiClosed() {
        LarpClient.get().config().save();
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
