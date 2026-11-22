package dev.larpclient.core.gui.menu;

import dev.larpclient.Reference;
import dev.larpclient.core.gui.ModMenuScreen;
import dev.larpclient.core.gui.Theme;
import dev.larpclient.core.util.Animation;
import dev.larpclient.core.util.RenderUtil;
import net.minecraft.client.gui.GuiMultiplayer;
import net.minecraft.client.gui.GuiOptions;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiSelectWorld;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraftforge.fml.client.GuiModList;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** LARP's replacement for the vanilla title screen. */
public final class LarpMainMenu extends GuiScreen {
    private static boolean firstOpen = true;

    private final List<MenuButton> buttons = new ArrayList<>();
    private final MusicWidget music = new MusicWidget();
    private final Animation intro = new Animation(firstOpen ? 0 : 1);
    private MenuButton forgeMods;

    @Override
    public void initGui() {
        buttons.clear();
        int cx = width / 2;
        int top = height / 2 - 8;
        int bw = 200;
        buttons.add(new MenuButton("Singleplayer", () -> mc.displayGuiScreen(new GuiSelectWorld(this)))
                .bounds(cx - bw / 2, top, bw, 22).primary());
        buttons.add(new MenuButton("Multiplayer", () -> mc.displayGuiScreen(new GuiMultiplayer(this)))
                .bounds(cx - bw / 2, top + 27, bw, 22).primary());
        int small = (bw - 10) / 3;
        buttons.add(new MenuButton("Options", () -> mc.displayGuiScreen(new GuiOptions(this, mc.gameSettings)))
                .bounds(cx - bw / 2, top + 54, small, 22));
        buttons.add(new MenuButton("LARP Mods", () -> mc.displayGuiScreen(new ModMenuScreen()))
                .bounds(cx - bw / 2 + small + 5, top + 54, small, 22));
        buttons.add(new MenuButton("Quit", () -> mc.shutdown())
                .bounds(cx - bw / 2 + (small + 5) * 2, top + 54, bw - (small + 5) * 2, 22));
        forgeMods = new MenuButton("Forge Mods", () -> mc.displayGuiScreen(new GuiModList(this)))
                .bounds(width - 72, height - 22, 66, 16);
        music.setPosition(width - MusicWidget.WIDTH - 8, 8);
        if (firstOpen) {
            intro.snap(0);
            intro.setTarget(1);
            firstOpen = false;
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        float t = (float) intro.update(System.currentTimeMillis(), 0.06);
        MenuBackground.get().draw(width, height, mouseX, mouseY);

        drawLogo(t);
        for (MenuButton button : buttons) {
            button.draw(mouseX, mouseY);
        }
        forgeMods.draw(mouseX, mouseY);
        music.draw(mouseX, mouseY);
        drawAccount();

        RenderUtil.text(Reference.NAME + " " + Reference.VERSION + " • Minecraft " + Reference.MC_VERSION,
                6, height - 12, Theme.TEXT_DIM, true);
        String legal = "Not affiliated with Mojang or Microsoft";
        RenderUtil.text(legal, width - 78 - fontRendererObj.getStringWidth(legal), height - 18, 0x60FFFFFF, false);

        if (t < 1) {
            RenderUtil.rect(0, 0, width, height, ((int) ((1 - t) * 255) << 24));
        }
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    private void drawLogo(float intro) {
        float cx = width / 2f;
        float y = height / 2f - 84 + (1 - intro) * -12;
        float scale = 5f;
        String logo = "LARP";
        GlStateManager.pushMatrix();
        GlStateManager.translate(cx, y, 0);
        GlStateManager.scale(scale, scale, 1);
        float pulse = 0.5f + 0.5f * (float) Math.sin(System.currentTimeMillis() / 900.0);
        int glow = RenderUtil.blend(0xFF8B5CF6, 0xFFC4B5FD, pulse);
        float lw = fontRendererObj.getStringWidth(logo);
        fontRendererObj.drawString(logo, -lw / 2f + 0.6f, 0.6f, 0x60301060, false);
        fontRendererObj.drawString(logo, -lw / 2f, 0, glow, false);
        GlStateManager.popMatrix();

        String sub = spaced("CLIENT");
        RenderUtil.centeredText(sub, cx, y + 50, 0xFFE8E4F5, true);
        RenderUtil.rect(cx - 40, y + 62, cx + 40, y + 63, 0x608B5CF6);
    }

    private static String spaced(String text) {
        StringBuilder sb = new StringBuilder();
        for (char c : text.toCharArray()) {
            if (sb.length() > 0) {
                sb.append("  ");
            }
            sb.append(c);
        }
        return sb.toString();
    }

    private void drawAccount() {
        String name = mc.getSession().getUsername();
        int w = Math.max(90, fontRendererObj.getStringWidth(name) + 34);
        RenderUtil.rect(8, 8, 8 + w, 30, 0x90000000);
        RenderUtil.outline(8, 8, 8 + w, 30, 0x30FFFFFF);
        RenderUtil.rect(12, 12, 26, 26, Theme.ACCENT);
        RenderUtil.centeredText(name.isEmpty() ? "?" : name.substring(0, 1).toUpperCase(), 19.5f, 15, Theme.TEXT, true);
        RenderUtil.text(name, 31, 15, Theme.TEXT, true);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) throws IOException {
        if (music.click(mouseX, mouseY, button, this)) {
            return;
        }
        for (MenuButton b : buttons) {
            if (b.click(mouseX, mouseY, button)) {
                return;
            }
        }
        forgeMods.click(mouseX, mouseY, button);
    }

    @Override
    protected void mouseClickMove(int mouseX, int mouseY, int button, long time) {
        music.drag(mouseX);
    }

    @Override
    protected void mouseReleased(int mouseX, int mouseY, int state) {
        music.release();
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
