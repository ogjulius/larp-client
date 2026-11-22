package dev.larpclient.core.gui;

import dev.larpclient.LarpClient;
import dev.larpclient.core.gui.menu.Backdrop;
import dev.larpclient.core.gui.menu.MenuButton;
import dev.larpclient.core.gui.menu.MusicScreen;
import dev.larpclient.core.module.Category;
import dev.larpclient.core.module.Module;
import dev.larpclient.core.render.Shapes;
import dev.larpclient.core.render.Ui;
import dev.larpclient.core.setting.Setting;
import dev.larpclient.core.util.Animation;
import dev.larpclient.core.util.RenderUtil;
import net.minecraft.client.gui.GuiScreen;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Main LARP menu: browse, toggle and configure modules. Opened with Right Shift. */
public final class ModMenuScreen extends GuiScreen {
    private static final int SIDEBAR = 112;
    private static final int HEADER = 36;
    private static final int CARD_H = 44;
    private static final int GAP = 6;
    private static Category lastCategory = Category.HUD;

    private Category category = lastCategory;
    private Module selected;
    private final List<SettingWidget<?>> widgets = new ArrayList<>();
    private final Map<Module, Animation> knobs = new HashMap<>();
    private Widgets.TextInput search;
    private MenuButton editHud;
    private MenuButton music;
    private float scroll;
    private float targetScroll;
    private float px;
    private float py;
    private float pw;
    private float ph;

    public ModMenuScreen() {
    }

    public ModMenuScreen(Module openSettingsFor) {
        this.category = openSettingsFor.getCategory();
        openSettings(openSettingsFor);
    }

    @Override
    public void initGui() {
        Keyboard.enableRepeatEvents(true);
        pw = Math.min(470, width - 24);
        ph = Math.min(300, height - 24);
        px = Math.round((width - pw) / 2f);
        py = Math.round((height - ph) / 2f);
        String previous = search == null ? "" : search.text;
        search = new Widgets.TextInput(px + pw - 12 - 120, py + 11, 120, 15, "Search modules", 32);
        search.setText(previous);
        editHud = new MenuButton("Edit HUD Layout", () -> mc.displayGuiScreen(new HudEditorScreen()))
                .bounds(px + 10, py + ph - 30, SIDEBAR - 20, 20).primary();
        music = new MenuButton("Music", () -> mc.displayGuiScreen(new MusicScreen(this)))
                .bounds(px + 10, py + ph - 54, SIDEBAR - 20, 20);
    }

    // ------------------------------------------------------------------ layout

    private float contentX() {
        return px + SIDEBAR + 12;
    }

    private float contentY() {
        return py + HEADER + 4;
    }

    private float contentW() {
        return pw - SIDEBAR - 24;
    }

    private float contentH() {
        return ph - HEADER - 12;
    }

    private int columns() {
        return contentW() >= 300 ? 2 : 1;
    }

    private float cardW() {
        return (contentW() - GAP * (columns() - 1)) / columns();
    }

    private List<Module> visibleModules() {
        String query = search.text.trim().toLowerCase(Locale.ROOT);
        List<Module> result = new ArrayList<>();
        for (Module module : LarpClient.get().modules().getModules()) {
            if (query.isEmpty() ? module.getCategory() == category
                    : module.getName().toLowerCase(Locale.ROOT).contains(query)
                    || module.getDescription().toLowerCase(Locale.ROOT).contains(query)) {
                result.add(module);
            }
        }
        return result;
    }

    private void openSettings(Module module) {
        selected = module;
        widgets.clear();
        for (Setting<?> setting : module.getSettings()) {
            widgets.add(SettingWidget.create(setting));
        }
        widgets.add(SettingWidget.create(module.getToggleKey()));
        scroll = targetScroll = 0;
    }

    private float contentHeight() {
        if (selected == null) {
            int rows = (visibleModules().size() + columns() - 1) / columns();
            return rows * (CARD_H + GAP);
        }
        float h = 30;
        for (SettingWidget<?> widget : widgets) {
            if (widget.getSetting().isVisible()) {
                h += widget.getHeight();
            }
        }
        return h;
    }

    // ------------------------------------------------------------------ drawing

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        if (mc.theWorld == null) {
            Backdrop.drawDimmed(width, height);
        } else {
            Shapes.rect(0, 0, width, height, 0x80000000);
        }
        Widgets.panel(px, py, pw, ph);
        drawSidebar(mouseX, mouseY);

        String title = selected != null ? "" : search.text.isEmpty() ? category.getDisplayName() : "Search results";
        Ui.TITLE.draw(title, contentX(), py + 13, 0xFFFFFFFF);
        if (selected == null) {
            search.draw();
        }

        float max = Math.max(0, contentHeight() - contentH());
        targetScroll = Math.max(0, Math.min(max, targetScroll));
        scroll += (targetScroll - scroll) * 0.35f;
        RenderUtil.scissor(contentX() - 2, contentY(), contentW() + 4, contentH());
        if (selected == null) {
            drawModules(mouseX, mouseY);
        } else {
            drawSettings(mouseX, mouseY);
        }
        RenderUtil.endScissor();
        if (selected != null) {
            drawSettingsHeader(mouseX, mouseY);
        }
        if (max > 0 && scroll < max - 1) {
            Shapes.verticalGradient(contentX() - 2, py + ph - 26, contentW() + 4, 14, 0x00121217, 0xF2121217);
        }
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    private void drawSidebar(int mouseX, int mouseY) {
        Shapes.rect(px + SIDEBAR, py + 10, 0.5f, ph - 20, 0x14FFFFFF);
        Ui.TITLE.draw("LARP", px + 14, py + 12, 0xFFFFFFFF);
        Ui.LABEL.draw("CLIENT", px + 15 + Ui.TITLE.width("LARP") + 4, py + 15.5f, 0xFFB9A4FF, 1.6f);

        float y = py + HEADER + 6;
        Ui.LABEL.draw("CATEGORIES", px + 14, y, 0x66FFFFFF, 1.2f);
        y += 12;
        for (Category c : Category.values()) {
            boolean active = c == category && search.text.isEmpty() && selected == null
                    || selected != null && selected.getCategory() == c;
            boolean hover = RenderUtil.hovered(mouseX, mouseY, px + 8, y, SIDEBAR - 16, 18);
            if (active || hover) {
                Shapes.roundedRect(px + 8, y, SIDEBAR - 16, 18, 4, active ? 0x1AFFFFFF : 0x0CFFFFFF);
            }
            if (active) {
                Shapes.roundedRect(px + 8, y + 5, 2, 8, 1, Theme.ACCENT);
            }
            Ui.BODY_STRONG.draw(c.getDisplayName(), px + 16, y + 5, active ? 0xFFFFFFFF : 0xA0FFFFFF);
            int enabled = 0;
            List<Module> list = LarpClient.get().modules().getModules(c);
            for (Module m : list) {
                if (m.isEnabled()) {
                    enabled++;
                }
            }
            Ui.SMALL.drawRight(enabled + "/" + list.size(), px + SIDEBAR - 14, y + 5.5f, 0x66FFFFFF);
            y += 20;
        }
        music.draw(mouseX, mouseY);
        editHud.draw(mouseX, mouseY);
    }

    private void drawModules(int mouseX, int mouseY) {
        boolean inContent = RenderUtil.hovered(mouseX, mouseY, contentX(), contentY(), contentW(), contentH());
        List<Module> modules = visibleModules();
        long now = System.currentTimeMillis();
        for (int i = 0; i < modules.size(); i++) {
            Module module = modules.get(i);
            float cx = contentX() + (i % columns()) * (cardW() + GAP);
            float cy = contentY() + (i / columns()) * (CARD_H + GAP) - scroll;
            boolean hover = inContent && RenderUtil.hovered(mouseX, mouseY, cx, cy, cardW(), CARD_H);
            Shapes.roundedRect(cx, cy, cardW(), CARD_H, 5, hover ? 0x14FFFFFF : 0x0AFFFFFF);
            Shapes.roundedOutline(cx, cy, cardW(), CARD_H, 5, 0.5f, module.isEnabled() ? 0x608B5CF6 : 0x10FFFFFF);

            Ui.BODY_STRONG.draw(Ui.ellipsize(Ui.BODY_STRONG, module.getName(), cardW() - 40), cx + 9, cy + 8, 0xFFFFFFFF);
            String blocked = module.blockedReason();
            String sub = blocked != null ? blocked : module.getDescription();
            Ui.SMALL.draw(Ui.ellipsize(Ui.SMALL, sub, cardW() - 18), cx + 9, cy + 20, blocked != null ? 0xFFFF7A7F : 0x80FFFFFF);

            Animation knob = knobs.computeIfAbsent(module, m -> new Animation(m.isEnabled() ? 1 : 0));
            knob.setTarget(module.isEnabled() ? 1 : 0);
            float k = (float) knob.update(now, 0.3);
            float sx = cx + cardW() - 27;
            float sy = cy + 7;
            Shapes.roundedRect(sx, sy, 18, 10, 5, RenderUtil.blend(0x30FFFFFF, Theme.ACCENT, k));
            Shapes.circle(sx + 5 + 8 * k, sy + 5, 3.6f, 0xFFFFFFFF);

            boolean optionsHover = hover && RenderUtil.hovered(mouseX, mouseY, cx + 4, cy + CARD_H - 13, 50, 12);
            Ui.SMALL.draw("Options", cx + 9, cy + CARD_H - 11, optionsHover ? Theme.ACCENT : 0x99B9A4FF);
        }
        if (modules.isEmpty()) {
            Ui.BODY.drawCentered("No modules match “" + search.text + "”", contentX() + contentW() / 2f,
                    contentY() + 40, 0x80FFFFFF);
        }
    }

    private void drawSettingsHeader(int mouseX, int mouseY) {
        boolean backHover = RenderUtil.hovered(mouseX, mouseY, contentX() - 4, py + 9, 18, 18);
        if (backHover) {
            Shapes.circle(contentX() + 3, py + 18, 8, 0x14FFFFFF);
        }
        int arrow = backHover ? 0xFFFFFFFF : 0xA0FFFFFF;
        Shapes.line(contentX() + 5, py + 14, contentX() + 1, py + 18, 1.3f, arrow);
        Shapes.line(contentX() + 1, py + 18, contentX() + 5, py + 22, 1.3f, arrow);
        Ui.TITLE.draw(selected.getName(), contentX() + 14, py + 13, 0xFFFFFFFF);
        float right = px + pw - 12;
        Widgets.toggle(right - 18, py + 14, selected.isEnabled());
        Ui.SMALL.drawRight(selected.isEnabled() ? "Enabled" : "Disabled", right - 24, py + 15.5f,
                selected.isEnabled() ? 0xFFB9A4FF : 0x80FFFFFF);
    }

    private void drawSettings(int mouseX, int mouseY) {
        float y = contentY() - scroll;

        Ui.SMALL.draw(Ui.ellipsize(Ui.SMALL, selected.getDescription(), contentW()), contentX(), y + 4, 0x80FFFFFF);
        y += 22;
        boolean inContent = RenderUtil.hovered(mouseX, mouseY, contentX(), contentY(), contentW(), contentH());
        for (SettingWidget<?> widget : widgets) {
            if (!widget.getSetting().isVisible()) {
                continue;
            }
            widget.layout((int) contentX(), (int) y, (int) contentW());
            widget.draw(inContent ? mouseX : -1, inContent ? mouseY : -1);
            y += widget.getHeight();
        }
    }

    // ------------------------------------------------------------------ input

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) throws IOException {
        if (selected == null) {
            search.mouseClicked(mouseX, mouseY);
        }
        float y = py + HEADER + 18;
        for (Category c : Category.values()) {
            if (button == 0 && RenderUtil.hovered(mouseX, mouseY, px + 8, y, SIDEBAR - 16, 18)) {
                category = lastCategory = c;
                selected = null;
                search.setText("");
                scroll = targetScroll = 0;
                MenuButton.playClick();
                return;
            }
            y += 20;
        }
        if (editHud.click(mouseX, mouseY, button) || music.click(mouseX, mouseY, button)) {
            return;
        }
        if (selected != null) {
            if (button == 0 && RenderUtil.hovered(mouseX, mouseY, contentX() - 2, py + 9, 16, 18)) {
                selected = null;
                scroll = targetScroll = 0;
                return;
            }
            if (button == 0 && RenderUtil.hovered(mouseX, mouseY, px + pw - 32, py + 12, 22, 14)) {
                selected.toggle();
                return;
            }
        }
        if (!RenderUtil.hovered(mouseX, mouseY, contentX(), contentY(), contentW(), contentH())) {
            return;
        }
        if (selected == null) {
            List<Module> modules = visibleModules();
            for (int i = 0; i < modules.size(); i++) {
                float cx = contentX() + (i % columns()) * (cardW() + GAP);
                float cy = contentY() + (i / columns()) * (CARD_H + GAP) - scroll;
                if (RenderUtil.hovered(mouseX, mouseY, cx, cy, cardW(), CARD_H)) {
                    boolean options = RenderUtil.hovered(mouseX, mouseY, cx + 4, cy + CARD_H - 13, 50, 12);
                    if (button == 1 || (button == 0 && options)) {
                        openSettings(modules.get(i));
                    } else if (button == 0) {
                        modules.get(i).toggle();
                    }
                    MenuButton.playClick();
                    return;
                }
            }
        } else {
            for (SettingWidget<?> widget : widgets) {
                if (widget.getSetting().isVisible()) {
                    widget.mouseClicked(mouseX, mouseY, button);
                }
            }
        }
    }

    @Override
    protected void mouseClickMove(int mouseX, int mouseY, int button, long timeSinceLastClick) {
        for (SettingWidget<?> widget : widgets) {
            widget.mouseDragged(mouseX, mouseY);
        }
    }

    @Override
    protected void mouseReleased(int mouseX, int mouseY, int state) {
        for (SettingWidget<?> widget : widgets) {
            widget.mouseReleased();
        }
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel != 0) {
            targetScroll -= Integer.signum(wheel) * 30;
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (selected != null) {
            for (SettingWidget<?> widget : widgets) {
                if (widget.keyTyped(typedChar, keyCode)) {
                    return;
                }
            }
            if (keyCode == Keyboard.KEY_ESCAPE) {
                selected = null;
                scroll = targetScroll = 0;
                return;
            }
        } else if (search.focused) {
            if (keyCode == Keyboard.KEY_ESCAPE) {
                search.focused = false;
                return;
            }
            if (search.keyTyped(typedChar, keyCode)) {
                scroll = targetScroll = 0;
                return;
            }
        }
        super.keyTyped(typedChar, keyCode);
    }

    @Override
    public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
        LarpClient.get().config().save();
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
