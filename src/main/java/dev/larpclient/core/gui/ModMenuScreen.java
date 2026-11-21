package dev.larpclient.core.gui;

import dev.larpclient.LarpClient;
import dev.larpclient.Reference;
import dev.larpclient.core.module.Category;
import dev.larpclient.core.module.Module;
import dev.larpclient.core.setting.Setting;
import dev.larpclient.core.util.RenderUtil;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Main LARP menu: browse, toggle and configure modules. Opened with Right Shift. */
public final class ModMenuScreen extends GuiScreen {
    private static final int SIDEBAR_WIDTH = 96;
    private static final int HEADER = 28;
    private static final int ROW = 30;
    private static Category lastCategory = Category.HUD;

    private Category category = lastCategory;
    private Module selected;
    private final List<SettingWidget<?>> widgets = new ArrayList<>();
    private GuiTextField search;
    private float scroll;
    private float targetScroll;
    private int panelX;
    private int panelY;
    private int panelW;
    private int panelH;

    public ModMenuScreen() {
    }

    public ModMenuScreen(Module openSettingsFor) {
        this.category = openSettingsFor.getCategory();
        openSettings(openSettingsFor);
    }

    @Override
    public void initGui() {
        Keyboard.enableRepeatEvents(true);
        panelW = Math.min(460, width - 30);
        panelH = Math.min(300, height - 30);
        panelX = (width - panelW) / 2;
        panelY = (height - panelH) / 2;
        String previous = search == null ? "" : search.getText();
        search = new GuiTextField(0, fontRendererObj, panelX + SIDEBAR_WIDTH + 8, panelY + 8, 120, 12);
        search.setMaxStringLength(32);
        search.setEnableBackgroundDrawing(false);
        search.setText(previous);
    }

    // ------------------------------------------------------------------ layout helpers

    private int contentX() {
        return panelX + SIDEBAR_WIDTH + 8;
    }

    private int contentY() {
        return panelY + HEADER;
    }

    private int contentW() {
        return panelW - SIDEBAR_WIDTH - 16;
    }

    private int contentH() {
        return panelH - HEADER - 8;
    }

    private List<Module> visibleModules() {
        String query = search.getText().trim().toLowerCase(Locale.ROOT);
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

    private int contentHeight() {
        if (selected == null) {
            return visibleModules().size() * ROW;
        }
        int h = 24;
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
        RenderUtil.rect(0, 0, width, height, Theme.OVERLAY);
        RenderUtil.rect(panelX, panelY, panelX + panelW, panelY + panelH, Theme.PANEL);
        RenderUtil.outline(panelX - 1, panelY - 1, panelX + panelW + 1, panelY + panelH + 1, Theme.BORDER);
        drawSidebar(mouseX, mouseY);

        // header: search box
        RenderUtil.rect(contentX() - 2, panelY + 5, contentX() + 124, panelY + 19, Theme.CARD);
        if (search.getText().isEmpty() && !search.isFocused()) {
            RenderUtil.text("Search modules...", contentX(), panelY + 8, Theme.TEXT_DIM, false);
        }
        search.drawTextBox();
        String title = selected == null ? (search.getText().isEmpty() ? category.getDisplayName() : "Search") : selected.getName();
        RenderUtil.text(title, panelX + panelW - 8 - fontRendererObj.getStringWidth(title), panelY + 8, Theme.TEXT, false);

        // scrolling content
        int maxScroll = Math.max(0, contentHeight() - contentH());
        targetScroll = Math.max(0, Math.min(maxScroll, targetScroll));
        scroll += (targetScroll - scroll) * Math.min(1, partialTicks * 0.5f + 0.3f);
        RenderUtil.scissor(contentX(), contentY(), contentW(), contentH());
        if (selected == null) {
            drawModuleList(mouseX, mouseY);
        } else {
            drawSettings(mouseX, mouseY);
        }
        RenderUtil.endScissor();
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    private void drawSidebar(int mouseX, int mouseY) {
        RenderUtil.rect(panelX, panelY, panelX + SIDEBAR_WIDTH, panelY + panelH, Theme.SIDEBAR);
        RenderUtil.text("LARP", panelX + 10, panelY + 9, Theme.ACCENT, true);
        RenderUtil.text("Client", panelX + 10 + fontRendererObj.getStringWidth("LARP "), panelY + 9, Theme.TEXT, true);
        int y = panelY + HEADER + 4;
        for (Category c : Category.values()) {
            boolean active = c == category && search.getText().isEmpty();
            boolean hover = RenderUtil.hovered(mouseX, mouseY, panelX, y, SIDEBAR_WIDTH, 18);
            if (active) {
                RenderUtil.rect(panelX, y, panelX + 2, y + 18, Theme.ACCENT);
            }
            if (active || hover) {
                RenderUtil.rect(panelX + 2, y, panelX + SIDEBAR_WIDTH, y + 18, Theme.CARD);
            }
            RenderUtil.text(c.getDisplayName(), panelX + 10, y + 5, active ? Theme.TEXT : Theme.TEXT_DIM, false);
            y += 20;
        }
        int by = panelY + panelH - 26;
        boolean hover = RenderUtil.hovered(mouseX, mouseY, panelX + 6, by, SIDEBAR_WIDTH - 12, 18);
        RenderUtil.rect(panelX + 6, by, panelX + SIDEBAR_WIDTH - 6, by + 18, hover ? Theme.ACCENT : Theme.ACCENT_DIM);
        RenderUtil.centeredText("Edit HUD", panelX + SIDEBAR_WIDTH / 2f, by + 5, Theme.TEXT, false);
        RenderUtil.text("v" + Reference.VERSION, panelX + 8, by - 12, Theme.TEXT_DIM, false);
    }

    private void drawModuleList(int mouseX, int mouseY) {
        int y = contentY() - Math.round(scroll);
        boolean inContent = RenderUtil.hovered(mouseX, mouseY, contentX(), contentY(), contentW(), contentH());
        for (Module module : visibleModules()) {
            boolean hover = inContent && RenderUtil.hovered(mouseX, mouseY, contentX(), y, contentW(), ROW - 3);
            RenderUtil.rect(contentX(), y, contentX() + contentW(), y + ROW - 3, hover ? Theme.CARD_HOVER : Theme.CARD);
            if (module.isEnabled()) {
                RenderUtil.rect(contentX(), y, contentX() + 2, y + ROW - 3, Theme.ACCENT);
            }
            RenderUtil.text(module.getName(), contentX() + 8, y + 5, Theme.TEXT, false);
            String blocked = module.blockedReason();
            String sub = blocked != null ? blocked : module.getDescription();
            RenderUtil.text(fontRendererObj.trimStringToWidth(sub, contentW() - 70), contentX() + 8, y + 16,
                    blocked != null ? Theme.DANGER : Theme.TEXT_DIM, false);
            int right = contentX() + contentW();
            SettingWidget.drawSwitch(right - 30, y + 8, module.isEnabled());
            boolean gearHover = inContent && RenderUtil.hovered(mouseX, mouseY, right - 50, y, 18, ROW - 3);
            RenderUtil.text("⚙", right - 46, y + 9, gearHover ? Theme.ACCENT : Theme.TEXT_DIM, false);
            y += ROW;
        }
    }

    private void drawSettings(int mouseX, int mouseY) {
        int y = contentY() - Math.round(scroll);
        boolean backHover = RenderUtil.hovered(mouseX, mouseY, contentX(), y, 60, 16);
        RenderUtil.text("◀ Back", contentX() + 2, y + 4, backHover ? Theme.ACCENT : Theme.TEXT_DIM, false);
        RenderUtil.text(fontRendererObj.trimStringToWidth(selected.getDescription(), contentW() - 70),
                contentX() + 60, y + 4, Theme.TEXT_DIM, false);
        y += 24;
        boolean inContent = RenderUtil.hovered(mouseX, mouseY, contentX(), contentY(), contentW(), contentH());
        for (SettingWidget<?> widget : widgets) {
            if (!widget.getSetting().isVisible()) {
                continue;
            }
            widget.layout(contentX(), y, contentW());
            widget.draw(inContent ? mouseX : -1, inContent ? mouseY : -1);
            y += widget.getHeight();
        }
    }

    // ------------------------------------------------------------------ input

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) throws IOException {
        search.mouseClicked(mouseX, mouseY, button);
        // sidebar
        int y = panelY + HEADER + 4;
        for (Category c : Category.values()) {
            if (button == 0 && RenderUtil.hovered(mouseX, mouseY, panelX, y, SIDEBAR_WIDTH, 18)) {
                category = lastCategory = c;
                selected = null;
                search.setText("");
                scroll = targetScroll = 0;
                return;
            }
            y += 20;
        }
        if (button == 0 && RenderUtil.hovered(mouseX, mouseY, panelX + 6, panelY + panelH - 26, SIDEBAR_WIDTH - 12, 18)) {
            mc.displayGuiScreen(new HudEditorScreen());
            return;
        }
        if (!RenderUtil.hovered(mouseX, mouseY, contentX(), contentY(), contentW(), contentH())) {
            return;
        }
        if (selected == null) {
            int rowY = contentY() - Math.round(scroll);
            int right = contentX() + contentW();
            for (Module module : visibleModules()) {
                if (RenderUtil.hovered(mouseX, mouseY, contentX(), rowY, contentW(), ROW - 3)) {
                    boolean gear = RenderUtil.hovered(mouseX, mouseY, right - 50, rowY, 18, ROW - 3);
                    if (button == 1 || (button == 0 && gear)) {
                        openSettings(module);
                    } else if (button == 0) {
                        module.toggle();
                    }
                    return;
                }
                rowY += ROW;
            }
        } else {
            if (button == 0 && RenderUtil.hovered(mouseX, mouseY, contentX(), contentY() - Math.round(scroll), 60, 16)) {
                selected = null;
                scroll = targetScroll = 0;
                return;
            }
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
            targetScroll -= Integer.signum(wheel) * 24;
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
        }
        if (search.isFocused()) {
            if (keyCode == Keyboard.KEY_ESCAPE) {
                search.setFocused(false);
                return;
            }
            search.textboxKeyTyped(typedChar, keyCode);
            selected = null;
            return;
        }
        if (keyCode == Keyboard.KEY_ESCAPE && selected != null) {
            selected = null;
            scroll = targetScroll = 0;
            return;
        }
        super.keyTyped(typedChar, keyCode);
    }

    @Override
    public void updateScreen() {
        search.updateCursorCounter();
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
