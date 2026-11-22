package dev.larpclient.core.gui.menu;

import dev.larpclient.core.gui.Theme;
import dev.larpclient.core.render.LarpFont;
import dev.larpclient.core.render.Shapes;
import dev.larpclient.core.render.Ui;
import dev.larpclient.core.util.Animation;
import dev.larpclient.core.util.RenderUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.util.ResourceLocation;

/** Rounded button used in LARP panels. */
public final class MenuButton {
    public float x;
    public float y;
    public float width;
    public float height;
    public String label;
    private boolean primary;
    private final Runnable action;
    private final Animation hover = new Animation(0);

    public MenuButton(String label, Runnable action) {
        this.label = label;
        this.action = action;
    }

    public MenuButton bounds(float x, float y, float width, float height) {
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
        float t = (float) hover.update(System.currentTimeMillis(), 0.3);
        int fill = primary ? RenderUtil.blend(Theme.ACCENT, 0xFF9D74FF, t) : RenderUtil.blend(0x14FFFFFF, 0x24FFFFFF, t);
        Shapes.roundedRect(x, y, width, height, 4, fill);
        if (!primary) {
            Shapes.roundedOutline(x, y, width, height, 4, 0.5f, 0x18FFFFFF);
        }
        LarpFont font = Ui.BODY_STRONG;
        font.drawCentered(label, x + width / 2f, y + (height - font.height()) / 2f, 0xFFFFFFFF);
    }

    public boolean click(int mouseX, int mouseY, int button) {
        if (button == 0 && isHovered(mouseX, mouseY)) {
            playClick();
            action.run();
            return true;
        }
        return false;
    }

    public static void playClick() {
        Minecraft.getMinecraft().getSoundHandler().playSound(
                PositionedSoundRecord.create(new ResourceLocation("gui.button.press"), 1.0F));
    }
}
