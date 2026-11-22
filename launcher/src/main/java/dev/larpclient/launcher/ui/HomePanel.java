package dev.larpclient.launcher.ui;

import dev.larpclient.launcher.auth.Account;
import dev.larpclient.launcher.auth.MicrosoftAuth;
import dev.larpclient.launcher.game.GameLauncher;
import dev.larpclient.launcher.game.InstalledGame;
import dev.larpclient.launcher.game.Installer;
import dev.larpclient.launcher.game.JavaRuntime;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.nio.file.Path;
import java.util.List;

/** Landing page: hero, feature highlights, news and the big launch button. */
public final class HomePanel extends Starfield {
    private final LauncherFrame frame;
    private final Components.PlayButton play = new Components.PlayButton("LAUNCH");
    private final Components.ThinProgress progress = new Components.ThinProgress();
    private final JLabel status = Components.label("Ready to play", 13f, Font.PLAIN, Palette.TEXT_DIM);
    private final JLabel playingAs = Components.label("", 12f, Font.PLAIN, Palette.TEXT_DIM);
    private volatile Process game;

    HomePanel(LauncherFrame frame) {
        super(new BorderLayout());
        this.frame = frame;
        setBorder(BorderFactory.createEmptyBorder(30, 44, 30, 44));

        JPanel hero = new JPanel();
        hero.setOpaque(false);
        hero.setLayout(new BoxLayout(hero, BoxLayout.Y_AXIS));
        JLabel tag = Components.label("MINECRAFT " + Installer.MC_VERSION + "  •  FORGE  •  PVP", 12f, Font.BOLD, Palette.ACCENT_LIGHT);
        JLabel title = Components.label("LARP Client", 54f, Font.BOLD, Palette.TEXT);
        JLabel subtitle = Components.label("<html>A fast, clean PvP client. Custom menus, HUD mods,<br>zoom, freelook and your own music, all built in.</html>",
                16f, Font.PLAIN, Palette.TEXT_DIM);
        hero.add(tag);
        hero.add(Box.createVerticalStrut(6));
        hero.add(title);
        hero.add(Box.createVerticalStrut(8));
        hero.add(subtitle);
        hero.add(Box.createVerticalStrut(26));

        JPanel chips = new JPanel(new GridLayout(1, 4, 12, 0));
        chips.setOpaque(false);
        chips.setMaximumSize(new Dimension(640, 74));
        chips.setAlignmentX(LEFT_ALIGNMENT);
        chips.add(chip("22", "Built-in mods"));
        chips.add(chip("♫", "Music from URLs"));
        chips.add(chip("0 ms", "Disabled-mod cost"));
        chips.add(chip("Linux", "First-class support"));
        hero.add(chips);
        for (java.awt.Component c : hero.getComponents()) {
            ((javax.swing.JComponent) c).setAlignmentX(LEFT_ALIGNMENT);
        }
        add(hero, BorderLayout.NORTH);

        // Bottom: launch area
        JPanel bottom = new JPanel(new BorderLayout(24, 0));
        bottom.setOpaque(false);
        JPanel launchInfo = new JPanel();
        launchInfo.setOpaque(false);
        launchInfo.setLayout(new BoxLayout(launchInfo, BoxLayout.Y_AXIS));
        launchInfo.add(Box.createVerticalGlue());
        status.setAlignmentX(LEFT_ALIGNMENT);
        progress.setAlignmentX(LEFT_ALIGNMENT);
        progress.setMaximumSize(new Dimension(Integer.MAX_VALUE, 6));
        playingAs.setAlignmentX(LEFT_ALIGNMENT);
        launchInfo.add(playingAs);
        launchInfo.add(Box.createVerticalStrut(6));
        launchInfo.add(status);
        launchInfo.add(Box.createVerticalStrut(10));
        launchInfo.add(progress);
        launchInfo.add(Box.createVerticalGlue());
        JPanel playWrap = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        playWrap.setOpaque(false);
        playWrap.add(play);
        bottom.add(playWrap, BorderLayout.WEST);
        bottom.add(launchInfo, BorderLayout.CENTER);
        bottom.add(news(), BorderLayout.EAST);
        add(bottom, BorderLayout.SOUTH);

        play.addActionListener(e -> launch());
        progress.setVisible(false);
    }

    private JPanel chip(String big, String small) {
        Components.Card card = new Components.Card(new BorderLayout(), 16).fill(Palette.alpha(Palette.SIDEBAR, 190));
        card.setBorder(BorderFactory.createEmptyBorder(10, 14, 10, 14));
        card.add(Components.label(big, 20f, Font.BOLD, Palette.TEXT), BorderLayout.NORTH);
        card.add(Components.label(small, 11f, Font.PLAIN, Palette.TEXT_DIM), BorderLayout.SOUTH);
        return card;
    }

    private JPanel news() {
        Components.Card card = new Components.Card(new BorderLayout(0, 8), 16).fill(Palette.alpha(Palette.SIDEBAR, 200));
        card.setPreferredSize(new Dimension(270, 150));
        card.add(Components.label("What's new", 14f, Font.BOLD, Palette.TEXT), BorderLayout.NORTH);
        card.add(Components.label("<html><body style='width:220px'>"
                + "• New main menu with live background<br>"
                + "• Music player: paste any MP3 / radio URL<br>"
                + "• HUD editor with snapping and scaling<br>"
                + "• Entity culling & particle limiter for FPS"
                + "</body></html>", 12f, Font.PLAIN, Palette.TEXT_DIM), BorderLayout.CENTER);
        return card;
    }

    void refresh() {
        Account account = frame.accounts.selected();
        playingAs.setText(account == null ? "Add an account to play" : "Playing as " + account.describe());
    }

    private void setStatus(String text, double fraction) {
        SwingUtilities.invokeLater(() -> {
            status.setText(text + (fraction >= 0 && fraction < 1 ? String.format("  %d%%", (int) (fraction * 100)) : ""));
            progress.setValue(fraction);
        });
    }

    private void launch() {
        Account account = frame.accounts.selected();
        if (account == null) {
            frame.show("Accounts");
            return;
        }
        play.setEnabled(false);
        play.setText("PREPARING");
        progress.setVisible(true);
        status.setForeground(Palette.TEXT_DIM);
        Thread worker = new Thread(() -> runLaunch(account), "larp-launch");
        worker.setDaemon(true);
        worker.start();
    }

    private void runLaunch(Account account) {
        try {
            if (account.needsRefresh()) {
                setStatus("Refreshing Microsoft sign-in", -1);
                new MicrosoftAuth(frame.settings.microsoftClientId).refresh(account);
                frame.accounts.save();
            }
            InstalledGame installed = new Installer(frame.files).install(this::setStatus);
            Path java = JavaRuntime.resolve(frame.settings.javaPath, frame.files, this::setStatus);
            GameLauncher launcher = new GameLauncher(frame.files);
            setStatus("Installing LARP mods", -1);
            launcher.prepareInstance();
            List<String> command = launcher.buildCommand(installed, account, frame.settings, java);
            frame.console.append("[launcher] Java: " + java);
            frame.console.append("[launcher] " + GameLauncher.redact(command, account));
            setStatus("Starting Minecraft", -1);
            game = launcher.start(command, frame.console::append);
            SwingUtilities.invokeLater(() -> {
                play.setText("PLAYING");
                status.setText("Minecraft is running");
                progress.setVisible(false);
                if (!frame.settings.keepLauncherOpen) {
                    frame.setVisible(false);
                }
            });
            int exit = game.waitFor();
            game = null;
            frame.console.append("[launcher] Minecraft exited with code " + exit);
            if (!frame.settings.keepLauncherOpen) {
                System.exit(0);
            }
            SwingUtilities.invokeLater(() -> {
                resetButton();
                if (exit != 0) {
                    status.setForeground(Palette.DANGER);
                    status.setText("Minecraft crashed (exit code " + exit + "). Check the Console page.");
                } else {
                    status.setText("Ready to play");
                }
            });
        } catch (Exception e) {
            frame.console.append("[launcher] Launch failed: " + e);
            SwingUtilities.invokeLater(() -> {
                resetButton();
                status.setForeground(Palette.DANGER);
                status.setText("<html>" + escape(e.getMessage() == null ? e.toString() : e.getMessage()) + "</html>");
            });
        }
    }

    private void resetButton() {
        play.setEnabled(true);
        play.setText("LAUNCH");
        progress.setVisible(false);
        frame.setVisible(true);
    }

    private static String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
