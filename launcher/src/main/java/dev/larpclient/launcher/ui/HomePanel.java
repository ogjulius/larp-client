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
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.font.TextAttribute;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/** Landing page: wordmark, launch button with inline progress, and the active account. */
public final class HomePanel extends HomeBackdrop {
    private final LauncherFrame frame;
    private final LaunchButton launch = new LaunchButton();
    private final JLabel status = new JLabel(" ");
    private final JLabel accountName = new JLabel();
    private final JLabel accountType = new JLabel();
    private BufferedImage head;
    /** How long the launcher lingers after starting the game, to report an immediate crash. */
    private static final long CRASH_GRACE_SECONDS = 4;

    private volatile Process game;

    HomePanel(LauncherFrame frame) {
        super(new BorderLayout());
        this.frame = frame;
        setBorder(BorderFactory.createEmptyBorder(64, 56, 44, 48));

        JPanel hero = new JPanel();
        hero.setOpaque(false);
        hero.setLayout(new BoxLayout(hero, BoxLayout.Y_AXIS));
        hero.add(pill("MINECRAFT " + Installer.MC_VERSION + "  ·  FORGE"));
        hero.add(Box.createVerticalStrut(18));
        JLabel wordmark = new JLabel("LARP");
        wordmark.setFont(Fonts.bold(88f).deriveFont(Map.of(TextAttribute.TRACKING, -0.035f)));
        wordmark.setForeground(Palette.TEXT);
        hero.add(wordmark);
        JLabel tagline = new JLabel("A quiet, fast client for 1.8.9 PvP.");
        tagline.setFont(Fonts.regular(18f));
        tagline.setForeground(new Color(0xB4B4C0));
        hero.add(Box.createVerticalStrut(2));
        hero.add(tagline);
        for (java.awt.Component c : hero.getComponents()) {
            ((JComponent) c).setAlignmentX(LEFT_ALIGNMENT);
        }
        add(hero, BorderLayout.NORTH);

        JPanel bottom = new JPanel(new BorderLayout(24, 0));
        bottom.setOpaque(false);
        JPanel launchBlock = new JPanel();
        launchBlock.setOpaque(false);
        launchBlock.setLayout(new BoxLayout(launchBlock, BoxLayout.Y_AXIS));
        launch.setAlignmentX(LEFT_ALIGNMENT);
        launch.setMaximumSize(new Dimension(300, 60));
        launch.onClick(this::launch);
        status.setFont(Fonts.regular(12.5f));
        status.setForeground(Palette.TEXT_DIM);
        status.setAlignmentX(LEFT_ALIGNMENT);
        status.setBorder(BorderFactory.createEmptyBorder(12, 4, 0, 0));
        launchBlock.add(launch);
        launchBlock.add(status);
        bottom.add(launchBlock, BorderLayout.WEST);
        bottom.add(accountCard(), BorderLayout.EAST);
        add(bottom, BorderLayout.SOUTH);
    }

    private JComponent pill(String text) {
        JLabel label = new JLabel(text) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = Components.smooth(g);
                g2.setColor(new Color(255, 255, 255, 14));
                g2.fill(new RoundRectangle2D.Float(0, 0, getWidth() - 1, getHeight() - 1, getHeight(), getHeight()));
                g2.setColor(new Color(255, 255, 255, 26));
                g2.draw(new RoundRectangle2D.Float(0, 0, getWidth() - 1, getHeight() - 1, getHeight(), getHeight()));
                g2.dispose();
                super.paintComponent(g);
            }
        };
        label.setFont(Fonts.semibold(11f).deriveFont(Map.of(TextAttribute.TRACKING, 0.08f)));
        label.setForeground(Palette.ACCENT_LIGHT);
        label.setBorder(BorderFactory.createEmptyBorder(6, 12, 6, 12));
        label.setMaximumSize(label.getPreferredSize());
        return label;
    }

    private JComponent accountCard() {
        JPanel card = new JPanel(new BorderLayout(12, 0)) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = Components.smooth(g);
                g2.setColor(new Color(16, 16, 20, 200));
                g2.fill(new RoundRectangle2D.Float(0, 0, getWidth() - 1, getHeight() - 1, 16, 16));
                g2.setColor(Palette.HAIRLINE);
                g2.draw(new RoundRectangle2D.Float(0, 0, getWidth() - 1, getHeight() - 1, 16, 16));
                g2.dispose();
            }
        };
        card.setOpaque(false);
        card.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 18));
        card.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        JComponent avatar = new JComponent() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = Components.smooth(g);
                g2.setClip(new RoundRectangle2D.Float(0, 0, 36, 36, 10, 10));
                if (head != null) {
                    g2.drawImage(head, 0, 0, 36, 36, null);
                } else {
                    g2.setColor(Palette.ACCENT_DARK);
                    g2.fillRect(0, 0, 36, 36);
                }
                g2.dispose();
            }
        };
        avatar.setPreferredSize(new Dimension(36, 36));
        JPanel text = new JPanel();
        text.setOpaque(false);
        text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
        accountName.setFont(Fonts.semibold(14f));
        accountName.setForeground(Palette.TEXT);
        accountType.setFont(Fonts.regular(12f));
        accountType.setForeground(Palette.TEXT_DIM);
        text.add(accountName);
        text.add(Box.createVerticalStrut(2));
        text.add(accountType);
        card.add(avatar, BorderLayout.WEST);
        card.add(text, BorderLayout.CENTER);
        Components.onClick(card, () -> frame.show("Accounts"));
        JPanel wrap = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        wrap.setOpaque(false);
        JPanel vertical = new JPanel(new BorderLayout());
        vertical.setOpaque(false);
        vertical.add(card, BorderLayout.SOUTH);
        wrap.add(vertical);
        return wrap;
    }

    void refresh() {
        Account account = frame.accounts.selected();
        if (account == null) {
            accountName.setText("No account");
            accountType.setText("Add one to play  ›");
        } else {
            accountName.setText(account.username);
            accountType.setText((account.type == Account.Type.MICROSOFT ? "Microsoft account" : "Offline profile") + "  ·  switch");
        }
        BufferedImage skin = Skins.get(account, frame.files, this::refresh);
        head = skin == null ? null : GameArt.face(skin, 36);
        if (!hasPanorama) {
            BufferedImage panorama = GameArt.blurredPanorama(frame.files);
            if (panorama != null) {
                setPanorama(panorama);
                hasPanorama = true;
            }
        }
        if (game == null) {
            launch.setIdle(account == null ? "Add account" : "Launch");
        }
        status.setText(account == null ? "Add an offline profile or Microsoft account to play" : "Ready  ·  " + String.format("%.1f GB memory", frame.settings.memoryMb / 1024.0));
        repaint();
    }

    private boolean hasPanorama;

    private void progress(String text, double fraction) {
        SwingUtilities.invokeLater(() -> {
            String detail = fraction >= 0 && fraction < 1 ? text + "  ·  " + (int) (fraction * 100) + "%" : text;
            launch.setBusy("Preparing", detail, fraction);
        });
    }

    private void launch() {
        Account account = frame.accounts.selected();
        if (account == null) {
            frame.show("Accounts");
            frame.accountsPanel.focusUsername();
            return;
        }
        status.setForeground(Palette.TEXT_DIM);
        status.setText(" ");
        launch.setBusy("Preparing", "Checking files", -1);
        Thread worker = new Thread(() -> runLaunch(account), "larp-launch");
        worker.setDaemon(true);
        worker.start();
    }

    private void runLaunch(Account account) {
        try {
            if (account.needsRefresh()) {
                progress("Refreshing Microsoft sign-in", -1);
                new MicrosoftAuth(frame.settings.microsoftClientId).refresh(account);
                frame.accounts.save();
            }
            InstalledGame installed = new Installer(frame.files).install(this::progress);
            SwingUtilities.invokeLater(this::refresh); // first install: panorama + skins now available
            Path java = JavaRuntime.resolve(frame.settings.javaPath, frame.files, this::progress);
            GameLauncher launcher = new GameLauncher(frame.files);
            progress("Installing LARP", -1);
            launcher.prepareInstance();
            List<String> command = launcher.buildCommand(installed, account, frame.settings, java);
            frame.console.append("[launcher] Java: " + java);
            frame.console.append("[launcher] " + GameLauncher.redact(command, account));
            progress("Starting Minecraft", -1);
            if (!frame.settings.keepLauncherOpen) {
                // Hand Minecraft off and quit. Output goes to a file because no one will read the pipe.
                Path log = frame.files.instance.resolve("logs").resolve("launcher-output.log");
                game = launcher.startDetached(command, frame.settings.threadedGl, log);
                frame.console.append("[launcher] Game output: " + log);
                // Stay a moment to catch an instant failure (bad JVM args, missing Java 8, ...).
                if (!game.waitFor(CRASH_GRACE_SECONDS, TimeUnit.SECONDS)) {
                    System.exit(0);
                }
                int exit = game.exitValue();
                game = null;
                frame.console.append("[launcher] Minecraft exited with code " + exit + " (see " + log + ")");
                SwingUtilities.invokeLater(() -> {
                    launch.setIdle("Launch");
                    status.setForeground(new Color(0xFF7A7F));
                    status.setText("Minecraft closed right away (code " + exit + "). Log: " + log);
                });
                return;
            }
            game = launcher.start(command, frame.console::append, frame.settings.threadedGl);
            SwingUtilities.invokeLater(() -> launch.setBusy("Running", "Minecraft is open", 1));
            int exit = game.waitFor();
            game = null;
            frame.console.append("[launcher] Minecraft exited with code " + exit);
            SwingUtilities.invokeLater(() -> {
                launch.setIdle("Launch");
                frame.setVisible(true);
                if (exit != 0) {
                    status.setForeground(new Color(0xFF7A7F));
                    status.setText("Minecraft closed unexpectedly (code " + exit + "). Details are on the Console page.");
                } else {
                    refresh();
                }
            });
        } catch (Exception e) {
            frame.console.append("[launcher] Launch failed: " + e);
            SwingUtilities.invokeLater(() -> {
                launch.setIdle("Launch");
                frame.setVisible(true);
                status.setForeground(new Color(0xFF7A7F));
                status.setText(e.getMessage() == null ? e.toString() : e.getMessage());
            });
        }
    }
}
