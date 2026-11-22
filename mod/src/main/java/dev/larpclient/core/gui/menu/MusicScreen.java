package dev.larpclient.core.gui.menu;

import dev.larpclient.LarpClient;
import dev.larpclient.core.gui.SettingWidget;
import dev.larpclient.core.gui.Theme;
import dev.larpclient.core.music.MusicPlayer;
import dev.larpclient.core.music.Track;
import dev.larpclient.core.util.RenderUtil;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.io.IOException;
import java.util.List;

/** Playlist manager: add MP3/WAV/radio URLs, play, remove, shuffle, autoplay. */
public final class MusicScreen extends GuiScreen {
    private static final int ROW = 20;

    private final GuiScreen parent;
    private final MusicWidget widget = new MusicWidget();
    private GuiTextField urlField;
    private GuiTextField titleField;
    private MenuButton addButton;
    private MenuButton backButton;
    private String message;
    private int messageColor;
    private float scroll;
    private int px;
    private int py;
    private int pw;
    private int ph;

    public MusicScreen(GuiScreen parent) {
        this.parent = parent;
    }

    private static MusicPlayer player() {
        return LarpClient.get().music();
    }

    @Override
    public void initGui() {
        Keyboard.enableRepeatEvents(true);
        pw = Math.min(420, width - 30);
        ph = Math.min(290, height - 30);
        px = (width - pw) / 2;
        py = (height - ph) / 2;
        urlField = new GuiTextField(1, fontRendererObj, px + 10, py + 32, pw - 90, 14);
        urlField.setMaxStringLength(512);
        titleField = new GuiTextField(2, fontRendererObj, px + 10, py + 52, pw - 90, 14);
        titleField.setMaxStringLength(64);
        urlField.setFocused(true);
        addButton = new MenuButton("Add", this::addTrack).bounds(px + pw - 72, py + 32, 62, 34).primary();
        backButton = new MenuButton("Done", () -> mc.displayGuiScreen(parent)).bounds(px + pw - 72, py + ph - 26, 62, 18);
        widget.setPosition(px + 10, py + ph - MusicWidget.HEIGHT - 8);
    }

    private void addTrack() {
        String url = urlField.getText().trim();
        if (player().add(url, titleField.getText())) {
            message = "Added " + Track.titleFromUrl(url);
            messageColor = 0xFF55FF88;
            urlField.setText("");
            titleField.setText("");
        } else {
            message = "Enter a valid http(s) URL to an MP3/WAV file or MP3 radio stream";
            messageColor = Theme.DANGER;
        }
    }

    private int listTop() {
        return py + 84;
    }

    private int listHeight() {
        return ph - 84 - MusicWidget.HEIGHT - 16;
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        if (mc.theWorld == null) {
            MenuBackground.get().draw(width, height, mouseX, mouseY);
        } else {
            RenderUtil.rect(0, 0, width, height, Theme.OVERLAY);
        }
        RenderUtil.rect(px, py, px + pw, py + ph, Theme.PANEL);
        RenderUtil.outline(px - 1, py - 1, px + pw + 1, py + ph + 1, Theme.BORDER);
        RenderUtil.text("♫ Music", px + 10, py + 10, Theme.ACCENT, true);
        RenderUtil.text("MP3 / WAV files and MP3 radio streams", px + 60, py + 10, Theme.TEXT_DIM, false);

        field(urlField, "https://example.com/song.mp3");
        field(titleField, "Title (optional)");
        addButton.draw(mouseX, mouseY);
        if (message != null) {
            RenderUtil.text(fontRendererObj.trimStringToWidth(message, pw - 20), px + 10, py + 70, messageColor, false);
        }

        // toggles
        MusicPlayer p = player();
        int tx = px + pw - 150;
        RenderUtil.text("Shuffle", tx, py + 70, Theme.TEXT_DIM, false);
        SettingWidget.drawSwitch(tx + 38, py + 69, p.isShuffle());
        RenderUtil.text("Autoplay", tx + 68, py + 70, Theme.TEXT_DIM, false);
        SettingWidget.drawSwitch(tx + 112, py + 69, p.isAutoplay());

        // playlist
        List<Track> tracks = p.getTracks();
        int top = listTop();
        int lh = listHeight();
        scroll = Math.max(0, Math.min(Math.max(0, tracks.size() * ROW - lh), scroll));
        RenderUtil.scissor(px + 8, top, pw - 16, lh);
        int y = top - Math.round(scroll);
        if (tracks.isEmpty()) {
            RenderUtil.centeredText("Your playlist is empty — paste a URL above", px + pw / 2f, top + 20, Theme.TEXT_DIM, false);
        }
        for (int i = 0; i < tracks.size(); i++) {
            boolean hover = RenderUtil.hovered(mouseX, mouseY, px + 8, y, pw - 16, ROW - 2)
                    && RenderUtil.hovered(mouseX, mouseY, px, top, pw, lh);
            boolean playing = i == p.getCurrentIndex() && p.getState() != MusicPlayer.State.STOPPED;
            RenderUtil.rect(px + 8, y, px + pw - 8, y + ROW - 2, hover ? Theme.CARD_HOVER : Theme.CARD);
            if (playing) {
                RenderUtil.rect(px + 8, y, px + 10, y + ROW - 2, Theme.ACCENT);
            }
            RenderUtil.text((playing ? "♪ " : "") + fontRendererObj.trimStringToWidth(tracks.get(i).getTitle(), pw - 70),
                    px + 14, y + 5, playing ? Theme.ACCENT : Theme.TEXT, false);
            boolean xHover = hover && mouseX >= px + pw - 24;
            RenderUtil.text("✕", px + pw - 20, y + 5, xHover ? Theme.DANGER : Theme.TEXT_DIM, false);
            y += ROW;
        }
        RenderUtil.endScissor();

        widget.draw(mouseX, mouseY);
        backButton.draw(mouseX, mouseY);
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    private void field(GuiTextField f, String placeholder) {
        RenderUtil.rect(f.xPosition - 2, f.yPosition - 2, f.xPosition + f.width + 2, f.yPosition + f.height,
                f.isFocused() ? Theme.CARD_HOVER : Theme.CARD);
        f.setEnableBackgroundDrawing(false);
        f.drawTextBox();
        if (f.getText().isEmpty() && !f.isFocused()) {
            RenderUtil.text(placeholder, f.xPosition, f.yPosition, Theme.TEXT_DIM, false);
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) throws IOException {
        urlField.mouseClicked(mouseX, mouseY, button);
        titleField.mouseClicked(mouseX, mouseY, button);
        if (addButton.click(mouseX, mouseY, button) || backButton.click(mouseX, mouseY, button)
                || widget.click(mouseX, mouseY, button, this)) {
            return;
        }
        MusicPlayer p = player();
        int tx = px + pw - 150;
        if (button == 0 && RenderUtil.hovered(mouseX, mouseY, tx + 38, py + 68, 24, 12)) {
            p.setShuffle(!p.isShuffle());
            p.save();
            return;
        }
        if (button == 0 && RenderUtil.hovered(mouseX, mouseY, tx + 112, py + 68, 24, 12)) {
            p.setAutoplay(!p.isAutoplay());
            p.save();
            return;
        }
        if (!RenderUtil.hovered(mouseX, mouseY, px + 8, listTop(), pw - 16, listHeight())) {
            return;
        }
        int index = (int) ((mouseY - listTop() + scroll) / ROW);
        if (index >= 0 && index < p.getTracks().size() && button == 0) {
            if (mouseX >= px + pw - 24) {
                p.remove(index);
            } else {
                p.play(index);
            }
        }
    }

    @Override
    protected void mouseClickMove(int mouseX, int mouseY, int button, long time) {
        widget.drag(mouseX);
    }

    @Override
    protected void mouseReleased(int mouseX, int mouseY, int state) {
        widget.release();
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel != 0) {
            scroll -= Integer.signum(wheel) * ROW;
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (keyCode == Keyboard.KEY_ESCAPE) {
            mc.displayGuiScreen(parent);
            return;
        }
        if (keyCode == Keyboard.KEY_RETURN && urlField.isFocused()) {
            addTrack();
            return;
        }
        if (keyCode == Keyboard.KEY_TAB) {
            boolean url = urlField.isFocused();
            urlField.setFocused(!url);
            titleField.setFocused(url);
            return;
        }
        urlField.textboxKeyTyped(typedChar, keyCode);
        titleField.textboxKeyTyped(typedChar, keyCode);
    }

    @Override
    public void updateScreen() {
        urlField.updateCursorCounter();
        titleField.updateCursorCounter();
    }

    @Override
    public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
