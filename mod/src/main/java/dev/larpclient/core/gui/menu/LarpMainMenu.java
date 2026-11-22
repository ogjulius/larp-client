package dev.larpclient.core.gui.menu;

import dev.larpclient.Reference;
import dev.larpclient.core.gui.ModMenuScreen;
import dev.larpclient.core.gui.Theme;
import dev.larpclient.core.render.Shapes;
import dev.larpclient.core.render.Ui;
import dev.larpclient.core.util.Animation;
import dev.larpclient.core.util.RenderUtil;
import net.minecraft.client.gui.GuiMultiplayer;
import net.minecraft.client.gui.GuiOptions;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiSelectWorld;
import net.minecraftforge.fml.client.GuiModList;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** LARP's title screen: blurred panorama, left-aligned wordmark and navigation. */
public final class LarpMainMenu extends GuiScreen {
    private static long firstOpenedAt = -1;

    private final List<NavItem> items = new ArrayList<>();
    private final MusicWidget music = new MusicWidget();
    private final Animation fade = new Animation(0);
    private float left;
    private float top;

    private final class NavItem {
        final String label;
        final Runnable action;
        final boolean muted;
        final Animation hover = new Animation(0);
        float y;

        NavItem(String label, Runnable action, boolean muted) {
            this.label = label;
            this.action = action;
            this.muted = muted;
        }

        boolean hovered(int mouseX, int mouseY) {
            return RenderUtil.hovered(mouseX, mouseY, left - 6, y - 3, 150, 17);
        }
    }

    @Override
    public void initGui() {
        if (firstOpenedAt < 0) {
            firstOpenedAt = System.currentTimeMillis();
        }
        fade.setTarget(1);
        items.clear();
        items.add(new NavItem("Singleplayer", () -> mc.displayGuiScreen(new GuiSelectWorld(this)), false));
        items.add(new NavItem("Multiplayer", () -> mc.displayGuiScreen(new GuiMultiplayer(this)), false));
        items.add(new NavItem("LARP Mods", () -> mc.displayGuiScreen(new ModMenuScreen()), false));
        items.add(new NavItem("Music", () -> mc.displayGuiScreen(new MusicScreen(this)), false));
        items.add(new NavItem("Settings", () -> mc.displayGuiScreen(new GuiOptions(this, mc.gameSettings)), false));
        items.add(new NavItem("Forge Mods", () -> mc.displayGuiScreen(new GuiModList(this)), true));
        items.add(new NavItem("Quit Game", () -> mc.shutdown(), true));

        left = Math.max(24, width * 0.075f);
        float blockHeight = 66 + items.size() * 18;
        top = Math.max(16, (height - blockHeight) / 2f - 10);
        float y = top + 60;
        for (NavItem item : items) {
            if (item.muted && !items.get(items.indexOf(item) - 1).muted) {
                y += 6; // small gap before secondary actions
            }
            item.y = y;
            y += 18;
        }
        music.setPosition(width - MusicWidget.WIDTH - 12, height - MusicWidget.HEIGHT - 12);
    }

    /** 0 → 1 entrance progress for an element appearing {@code delayMs} after first open. */
    private static float intro(long delayMs) {
        float p = (System.currentTimeMillis() - firstOpenedAt - delayMs) / 450f;
        p = Math.max(0, Math.min(1, p));
        return 1 - (1 - p) * (1 - p) * (1 - p); // ease-out cubic
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        Backdrop.drawMenu(width, height);

        float logoIn = intro(0);
        float lx = left - (1 - logoIn) * 8;
        int logoAlpha = (int) (255 * logoIn) << 24;
        Ui.DISPLAY.draw("LARP", lx - 1, top, logoAlpha | 0xFFFFFF, -0.6f);
        Ui.LABEL.draw("CLIENT", lx + 1.5f, top + 35, logoAlpha | 0xB9A4FF, 2.6f);

        long now = System.currentTimeMillis();
        for (int i = 0; i < items.size(); i++) {
            NavItem item = items.get(i);
            float in = intro(120 + i * 45L);
            if (in <= 0) {
                continue;
            }
            item.hover.setTarget(item.hovered(mouseX, mouseY) ? 1 : 0);
            float h = (float) item.hover.update(now, 0.28);
            float x = left + h * 5 - (1 - in) * 10;
            if (h > 0.01f) {
                Shapes.roundedRect(left - 6, item.y - 2.5f + (1 - h) * 6, 2, (14) * h, 1, Theme.ACCENT);
            }
            int base = item.muted ? 0x8C8C96 : 0xC8C8D2;
            int color = RenderUtil.blend(0xFF000000 | base, 0xFFFFFFFF, h);
            int alpha = (int) (((color >>> 24) & 0xFF) * in);
            Ui.NAV.draw(item.label, x, item.y, (alpha << 24) | (color & 0xFFFFFF));
        }

        float footIn = intro(500);
        int footAlpha = (int) (0x80 * footIn) << 24;
        String footer = "LARP Client " + Reference.VERSION + "   \u00B7   Minecraft " + Reference.MC_VERSION;
        String legal = "   \u00B7   Not affiliated with Mojang or Microsoft";
        float room = width - MusicWidget.WIDTH - 24 - left;
        if (Ui.SMALL.width(footer + legal) <= room) {
            footer += legal;
        }
        Ui.SMALL.draw(footer, left, height - 16, footAlpha | 0xFFFFFF);

        drawAccount(intro(300));
        if (intro(450) > 0) {
            music.draw(mouseX, mouseY);
        }

        float f = (float) fade.update(now, 0.08);
        if (f < 1) {
            Shapes.rect(0, 0, width, height, (int) ((1 - f) * 255) << 24);
        }
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    private void drawAccount(float in) {
        if (in <= 0) {
            return;
        }
        String name = mc.getSession().getUsername();
        float w = Ui.BODY_STRONG.width(name) + 32;
        float x = width - w - 12;
        float y = 10;
        Shapes.roundedRect(x, y, w, 18, 9, (int) (0x70 * in) << 24 | 0x0A0A0E);
        Shapes.roundedOutline(x, y, w, 18, 9, 0.5f, (int) (0x20 * in) << 24 | 0xFFFFFF);
        PlayerHead.draw(x + 4, y + 3, 12);
        Ui.BODY_STRONG.draw(name, x + 21, y + 4.5f, (int) (255 * in) << 24 | 0xFFFFFF);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) throws IOException {
        if (music.click(mouseX, mouseY, button, this)) {
            return;
        }
        if (button != 0) {
            return;
        }
        for (NavItem item : items) {
            if (item.hovered(mouseX, mouseY)) {
                MenuButton.playClick();
                item.action.run();
                return;
            }
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        // The title screen ignores ESC.
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
