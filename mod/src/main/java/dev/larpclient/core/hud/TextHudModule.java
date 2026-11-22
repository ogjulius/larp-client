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

    @Override
    public int getWidth() {
        String text = displayText(true);
        int textWidth = text == null ? 0 : RenderUtil.font().getStringWidth(text);
        return background.isOn() ? Math.max(MIN_BOX_WIDTH, textWidth + PADDING * 2) : textWidth;
    }

    @Override
    public int getHeight() {
        int fontHeight = RenderUtil.font().FONT_HEIGHT;
        return background.isOn() ? fontHeight + PADDING * 2 - 1 : fontHeight;
    }

    @Override
    public void render(float partialTicks, boolean editing) {
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
}
