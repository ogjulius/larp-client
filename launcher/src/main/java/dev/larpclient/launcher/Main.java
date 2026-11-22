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
        UIManager.put("Button.arc", 12);
        UIManager.put("Component.arc", 10);
        UIManager.put("TextComponent.arc", 10);
        UIManager.put("ScrollBar.thumbArc", 999);
        UIManager.put("ScrollBar.width", 8);
        UIManager.put("Panel.background", Palette.BG);
        UIManager.put("TextField.background", Palette.CARD);
        UIManager.put("Spinner.background", Palette.CARD);
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
