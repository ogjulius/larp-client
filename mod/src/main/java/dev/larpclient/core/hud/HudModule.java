package dev.larpclient.core.hud;

import dev.larpclient.core.module.Category;
import dev.larpclient.core.module.Module;
import dev.larpclient.core.setting.NumberSetting;

/**
 * A module that draws a movable element on the in-game HUD.
 * <p>
 * Position is stored as fractions of the free screen space ({@code 0} = left/top edge,
 * {@code 1} = right/bottom edge) so layouts survive window resizes and GUI-scale changes.
 */
public abstract class HudModule extends Module {
    /** Gap kept between HUD elements and the screen edges, in GUI pixels. */
    public static final float EDGE_MARGIN = 4;

    private final NumberSetting posX;
    private final NumberSetting posY;
    private final NumberSetting scale;

    protected HudModule(String name, String description, double defaultX, double defaultY) {
        super(name, description, Category.HUD);
        this.posX = setting(new NumberSetting("X", "Horizontal position", defaultX, 0, 1, 0.0001)
                .visibleWhen(() -> false));
        this.posY = setting(new NumberSetting("Y", "Vertical position", defaultY, 0, 1, 0.0001)
                .visibleWhen(() -> false));
        this.scale = setting(new NumberSetting("Scale", "Element size", 1.0, 0.5, 2.0, 0.05));
    }

    /** Unscaled width in GUI pixels. */
    public abstract int getWidth();

    /** Unscaled height in GUI pixels. */
    public abstract int getHeight();

    /**
     * Draw at the origin (the caller has translated and scaled the matrix).
     *
     * @param editing true when drawn inside the HUD editor; render placeholder data if needed
     */
    public abstract void render(float partialTicks, boolean editing);

    /** Whether there is anything to draw right now outside the editor. */
    public boolean shouldRender() {
        return true;
    }

    public float getScale() {
        return scale.floatValue();
    }

    public void setScale(double value) {
        scale.set(value);
    }

    public float getScaledWidth() {
        return getWidth() * getScale();
    }

    public float getScaledHeight() {
        return getHeight() * getScale();
    }

    /** Screen-space X of the element's left edge. */
    public float getScreenX(int screenWidth) {
        return EDGE_MARGIN + (float) (posX.get() * Math.max(0, screenWidth - getScaledWidth() - EDGE_MARGIN * 2));
    }

    /** Screen-space Y of the element's top edge. */
    public float getScreenY(int screenHeight) {
        return EDGE_MARGIN + (float) (posY.get() * Math.max(0, screenHeight - getScaledHeight() - EDGE_MARGIN * 2));
    }

    /** Move the element so its left/top edge sits at the given screen coordinates. */
    public void setScreenPosition(float x, float y, int screenWidth, int screenHeight) {
        float freeX = Math.max(1, screenWidth - getScaledWidth() - EDGE_MARGIN * 2);
        float freeY = Math.max(1, screenHeight - getScaledHeight() - EDGE_MARGIN * 2);
        posX.set((double) Math.max(0, Math.min(1, (x - EDGE_MARGIN) / freeX)));
        posY.set((double) Math.max(0, Math.min(1, (y - EDGE_MARGIN) / freeY)));
    }
}
