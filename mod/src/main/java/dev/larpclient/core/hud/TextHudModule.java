package dev.larpclient.core.hud;

import dev.larpclient.core.setting.BooleanSetting;
import dev.larpclient.core.setting.ColorSetting;
import dev.larpclient.core.setting.ModeSetting;
import dev.larpclient.core.util.RenderUtil;

/** Base for single-line text HUD elements ("[FPS: 240]" style). */
public abstract class TextHudModule extends HudModule {
    private static final int PADDING = 4;
    private static final int MIN_BOX_WIDTH = 56;

    protected final BooleanSetting background;
    protected final ColorSetting backgroundColor;
    protected final ColorSetting textColor;
    protected final BooleanSetting shadow;
    protected final ModeSetting brackets;

    protected TextHudModule(String name, String description, double defaultX, double defaultY) {
        super(name, description, defaultX, defaultY);
        background = setting(new BooleanSetting("Background", "Draw a box behind the text", true));
        backgroundColor = setting(new ColorSetting("Background Color", "Box colour", 0x6F000000)
                .visibleWhen(background::isOn));
        textColor = setting(new ColorSetting("Text Color", "Text colour", 0xFFFFFFFF));
        shadow = setting(new BooleanSetting("Text Shadow", "Draw text with a drop shadow", true));
        brackets = setting(new ModeSetting("Brackets", "Wrap the text in brackets", "None", "None", "[]", "()", "<>")
                .visibleWhen(() -> !background.isOn()));
    }

    /** Current text, or {@code null} to hide the element. */
    protected abstract String getText();

    /** Optional dimmed unit drawn after the text in the modern style (e.g. "FPS"). */
    protected String getSuffix() {
        return null;
    }

    /** Text shown in the HUD editor when {@link #getText()} has nothing to show. */
    protected String getPlaceholder() {
        return getName();
    }

    private String displayText(boolean editing) {
        String text = getText();
        if (text == null && editing) {
            text = getPlaceholder();
        }
        if (text == null) {
            return null;
        }
        if (!background.isOn() && !brackets.is("None")) {
            String b = brackets.get();
            text = b.charAt(0) + text + b.charAt(1);
        }
        return text;
    }

    @Override
    public boolean shouldRender() {
        return getText() != null;
    }

    private static final int MODERN_PAD_X = 6;
    private static final int MODERN_HEIGHT = 15;

    private String modernSuffix() {
        String suffix = getSuffix();
        return suffix == null ? null : " " + suffix;
    }

    @Override
    public int getWidth() {
        if (dev.larpclient.modules.hud.HudTheme.modern()) {
            String text = getText();
            if (text == null) {
                text = getPlaceholder();
            }
            String suffix = getText() != null ? modernSuffix() : null;
            float w = HudDraw.width(text) + (suffix == null ? 0 : HudDraw.width(suffix));
            return background.isOn() ? (int) Math.ceil(Math.max(40, w + MODERN_PAD_X * 2)) : (int) Math.ceil(w);
        }
        String text = displayText(true);
        int textWidth = text == null ? 0 : RenderUtil.font().getStringWidth(text);
        return background.isOn() ? Math.max(MIN_BOX_WIDTH, textWidth + PADDING * 2) : textWidth;
    }

    @Override
    public int getHeight() {
        if (dev.larpclient.modules.hud.HudTheme.modern()) {
            return background.isOn() ? MODERN_HEIGHT : (int) Math.ceil(HudDraw.height());
        }
        int fontHeight = RenderUtil.font().FONT_HEIGHT;
        return background.isOn() ? fontHeight + PADDING * 2 - 1 : fontHeight;
    }

    @Override
    public void render(float partialTicks, boolean editing) {
        if (dev.larpclient.modules.hud.HudTheme.modern()) {
            renderModern(editing);
            return;
        }
        String text = displayText(editing);
        if (text == null) {
            return;
        }
        int width = getWidth();
        if (background.isOn()) {
            RenderUtil.rect(0, 0, width, getHeight(), backgroundColor.resolve());
            int textWidth = RenderUtil.font().getStringWidth(text);
            RenderUtil.text(text, (width - textWidth) / 2f, PADDING, textColor.resolve(), shadow.isOn());
        } else {
            RenderUtil.text(text, 0, 0, textColor.resolve(), shadow.isOn());
        }
    }

    private void renderModern(boolean editing) {
        String text = getText();
        String suffix = text != null ? modernSuffix() : null;
        if (text == null) {
            if (!editing) {
                return;
            }
            text = getPlaceholder();
        }
        int width = getWidth();
        int color = textColor.resolve();
        float contentW = HudDraw.width(text) + (suffix == null ? 0 : HudDraw.width(suffix));
        float x = background.isOn() ? (width - contentW) / 2f : 0;
        float y = background.isOn() ? (getHeight() - HudDraw.height()) / 2f : 0;
        if (background.isOn()) {
            // the module's own background colour keeps working; untouched defaults follow the theme
            int bg = backgroundColor.get() == 0x6F000000 ? dev.larpclient.modules.hud.HudTheme.background() : backgroundColor.resolve();
            HudDraw.panel(0, 0, width, getHeight(), bg);
        }
        float end = HudDraw.text(text, x, y, color);
        if (suffix != null) {
            HudDraw.text(suffix, end, y, HudDraw.dim(color, 0.6f));
        }
    }
}
