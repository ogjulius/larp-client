package dev.larpclient.core.gui.menu;

import dev.larpclient.core.gui.Theme;
import dev.larpclient.core.util.Animation;
import dev.larpclient.core.util.RenderUtil;

/** Flat animated button used by LARP menus. */
public final class MenuButton {
    public int x;
    public int y;
    public int width;
    public int height;
    public String label;
    public boolean primary;
    private final Runnable action;
    private final Animation hover = new Animation(0);

    public MenuButton(String label, Runnable action) {
        this.label = label;
        this.action = action;
    }

    public MenuButton bounds(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        return this;
    }

    public MenuButton primary() {
        this.primary = true;
        return this;
    }

    public boolean isHovered(int mouseX, int mouseY) {
        return RenderUtil.hovered(mouseX, mouseY, x, y, width, height);
    }

    public void draw(int mouseX, int mouseY) {
        hover.setTarget(isHovered(mouseX, mouseY) ? 1 : 0);
        float t = (float) hover.update(System.currentTimeMillis(), 0.25);
        int base = primary ? 0xC0603FC0 : 0x70000000;
        int hot = primary ? 0xF08B5CF6 : 0xA0302848;
        RenderUtil.rect(x, y, x + width, y + height, RenderUtil.blend(base, hot, t));
        RenderUtil.outline(x, y, x + width, y + height, RenderUtil.blend(0x30FFFFFF, 0x90B9A0FF, t));
        int lineW = (int) (width * t);
        RenderUtil.rect(x + (width - lineW) / 2f, y + height - 1, x + (width + lineW) / 2f, y + height, Theme.ACCENT);
        RenderUtil.centeredText(label, x + width / 2f, y + (height - 8) / 2f,
                RenderUtil.blend(0xFFDDDDE6, 0xFFFFFFFF, t), true);
    }

    /** @return true if clicked */
    public boolean click(int mouseX, int mouseY, int button) {
        if (button == 0 && isHovered(mouseX, mouseY)) {
            net.minecraft.client.Minecraft.getMinecraft().getSoundHandler().playSound(
                    net.minecraft.client.audio.PositionedSoundRecord.create(
                            new net.minecraft.util.ResourceLocation("gui.button.press"), 1.0F));
            action.run();
            return true;
        }
        return false;
    }
}
