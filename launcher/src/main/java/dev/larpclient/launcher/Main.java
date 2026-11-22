package dev.larpclient.launcher;

import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLaf;
import dev.larpclient.launcher.ui.LauncherFrame;
import dev.larpclient.launcher.ui.Palette;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import java.io.InputStream;
import java.util.Map;
import java.util.Properties;

public final class Main {
    public static final String VERSION = readVersion();

    private Main() {
    }

    public static void main(String[] args) throws Exception {
        if (Cli.wants(args)) {
            System.exit(Cli.run(args));
        }
        System.setProperty("awt.useSystemAAFontSettings", "on");
        System.setProperty("swing.aatext", "true");
        // Nicer window class name for Linux docks/taskbars.
        System.setProperty("sun.awt.wmClass", "larp-client");
        FlatLaf.setGlobalExtraDefaults(Map.of("@accentColor", Palette.hex(Palette.ACCENT)));
        FlatDarkLaf.setup();
        UIManager.put("defaultFont", dev.larpclient.launcher.ui.Fonts.regular(13f));
        UIManager.put("Button.background", Palette.CARD_HOVER);
        UIManager.put("Button.hoverBackground", new java.awt.Color(0x2B2B33));
        UIManager.put("Button.borderColor", Palette.BORDER);
        UIManager.put("Button.font", dev.larpclient.launcher.ui.Fonts.semibold(13f));
        UIManager.put("Button.default.background", Palette.ACCENT);
        UIManager.put("Button.default.foreground", java.awt.Color.WHITE);
        UIManager.put("TextComponent.selectionBackground", Palette.ACCENT_DARK);
        UIManager.put("Slider.trackColor", Palette.BORDER);
        UIManager.put("Slider.thumbColor", java.awt.Color.WHITE);
        UIManager.put("Slider.trackValueColor", Palette.ACCENT);
        UIManager.put("Slider.trackWidth", 4);
        UIManager.put("Slider.thumbSize", new java.awt.Dimension(14, 14));
        UIManager.put("CheckBox.icon.selectedBackground", Palette.ACCENT);
        UIManager.put("ToolTip.background", Palette.CARD_HOVER);
        UIManager.put("Button.arc", 10);
        UIManager.put("Component.arc", 10);
        UIManager.put("TextComponent.arc", 10);
        UIManager.put("ScrollBar.thumbArc", 999);
        UIManager.put("ScrollBar.width", 8);
        UIManager.put("Panel.background", Palette.BG);
        UIManager.put("TextField.background", new java.awt.Color(0x0E0E12));
        UIManager.put("Spinner.background", new java.awt.Color(0x0E0E12));
        UIManager.put("Component.focusWidth", 1);
        String page = "Home";
        for (int i = 0; i + 1 < args.length; i++) {
            if (args[i].equals("--page")) {
                page = args[i + 1];
            }
        }
        String startPage = page;
        SwingUtilities.invokeLater(() -> new LauncherFrame(startPage).setVisible(true));
    }

    private static String readVersion() {
        try (InputStream in = Main.class.getResourceAsStream("/larp/launcher.properties")) {
            Properties p = new Properties();
            if (in != null) {
                p.load(in);
            }
            return p.getProperty("version", "dev");
        } catch (Exception e) {
            return "dev";
        }
    }
}
