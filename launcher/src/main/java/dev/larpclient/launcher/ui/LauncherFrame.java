package dev.larpclient.launcher.ui;

import dev.larpclient.launcher.Main;
import dev.larpclient.launcher.auth.Account;
import dev.larpclient.launcher.auth.AccountStore;
import dev.larpclient.launcher.config.LauncherSettings;
import dev.larpclient.launcher.game.GameFiles;
import dev.larpclient.launcher.util.Platform;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.LinkedHashMap;
import java.util.Map;

/** Main window: custom title bar, sidebar navigation and page cards. */
public final class LauncherFrame extends JFrame {
    final LauncherSettings settings = LauncherSettings.load(Platform.configDir().resolve("launcher.json"));
    final AccountStore accounts = new AccountStore(Platform.configDir().resolve("accounts.json"));
    final GameFiles files = new GameFiles(Platform.dataDir());

    private final CardLayout cards = new CardLayout();
    private final JPanel pages = new JPanel(cards);
    private final Map<String, Components.NavButton> nav = new LinkedHashMap<>();
    private final JLabel accountName = Components.label("", 13f, Font.BOLD, Palette.TEXT);
    private final JLabel accountType = Components.label("", 11f, Font.PLAIN, Palette.TEXT_DIM);

    final ConsolePanel console = new ConsolePanel(this);
    final HomePanel home = new HomePanel(this);
    final AccountsPanel accountsPanel = new AccountsPanel(this);
    final SettingsPanel settingsPanel = new SettingsPanel(this);

    public LauncherFrame() {
        this("Home");
    }

    public LauncherFrame(String startPage) {
        super("LARP Client");
        setUndecorated(true);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        // Fixed size: tiling window managers (Hyprland, Sway, i3) float windows whose min == max size.
        setSize(WIDTH, HEIGHT);
        setResizable(false);
        setLocationRelativeTo(null);
        setIconImage(Icons.appIcon(64));

        JPanel root = new JPanel(new BorderLayout());
        root.setBorder(BorderFactory.createLineBorder(Palette.BORDER));
        root.add(sidebar(), BorderLayout.WEST);

        JPanel main = new JPanel(new BorderLayout());
        main.add(titleBar(), BorderLayout.NORTH);
        pages.add(home, "Home");
        pages.add(accountsPanel, "Accounts");
        pages.add(settingsPanel, "Settings");
        pages.add(console, "Console");
        main.add(pages, BorderLayout.CENTER);
        root.add(main, BorderLayout.CENTER);
        setContentPane(root);

        refreshAccount();
        show(startPage);

        // Some window managers (e.g. Hyprland with XWayland) ignore the initial size hint. Re-assert
        // the intended size once the window is mapped; floating windows accept client resizes.
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowOpened(java.awt.event.WindowEvent e) {
                new javax.swing.Timer(150, ev -> {
                    if (getWidth() != WIDTH || getHeight() != HEIGHT) {
                        setSize(WIDTH, HEIGHT);
                        setLocationRelativeTo(null);
                    }
                    ((javax.swing.Timer) ev.getSource()).stop();
                }).start();
            }
        });
    }

    private static final int WIDTH = 1060;
    private static final int HEIGHT = 660;

    void show(String page) {
        cards.show(pages, page);
        nav.forEach((name, button) -> button.setActive(name.equals(page)));
        if (page.equals("Accounts")) {
            accountsPanel.reload();
        }
    }

    void refreshAccount() {
        Account account = accounts.selected();
        accountName.setText(account == null ? "No account" : account.username);
        accountType.setText(account == null ? "Click to add one" : account.type == Account.Type.MICROSOFT ? "Microsoft" : "Offline");
        home.refresh();
        repaint();
    }

    private JPanel sidebar() {
        JPanel side = new JPanel();
        side.setBackground(Palette.SIDEBAR);
        side.setLayout(new BoxLayout(side, BoxLayout.Y_AXIS));
        side.setPreferredSize(new Dimension(210, 0));
        side.setBorder(BorderFactory.createEmptyBorder(18, 0, 14, 0));

        JPanel logo = new JPanel(new FlowLayout(FlowLayout.LEFT, 20, 0)) {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
            }
        };
        logo.setOpaque(false);
        JLabel mark = new JLabel(new javax.swing.ImageIcon(Icons.appIcon(34)));
        JPanel words = new JPanel();
        words.setOpaque(false);
        words.setLayout(new BoxLayout(words, BoxLayout.Y_AXIS));
        words.add(Components.label("LARP", 20f, Font.BOLD, Palette.TEXT));
        words.add(Components.label("CLIENT", 10f, Font.BOLD, Palette.ACCENT_LIGHT));
        logo.add(mark);
        logo.add(words);
        logo.setMaximumSize(new Dimension(Integer.MAX_VALUE, 50));
        logo.setAlignmentX(LEFT_ALIGNMENT);
        side.add(logo);
        side.add(Box.createVerticalStrut(26));

        for (String page : new String[]{"Home", "Accounts", "Settings", "Console"}) {
            Components.NavButton button = new Components.NavButton(page);
            button.setAlignmentX(LEFT_ALIGNMENT);
            button.addActionListener(e -> show(page));
            nav.put(page, button);
            side.add(button);
            side.add(Box.createVerticalStrut(2));
        }
        side.add(Box.createVerticalGlue());

        Components.Card chip = new Components.Card(new BorderLayout(10, 0), 14);
        chip.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
        chip.setMaximumSize(new Dimension(190, 54));
        chip.setAlignmentX(LEFT_ALIGNMENT);
        chip.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        JPanel avatar = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = Components.smooth(g);
                g2.setColor(Palette.ACCENT);
                g2.fillRoundRect(0, 0, 34, 34, 10, 10);
                g2.setColor(Color.WHITE);
                g2.setFont(Palette.font(Font.BOLD, 16f));
                String initial = accountName.getText().isEmpty() ? "?" : accountName.getText().substring(0, 1).toUpperCase();
                int tw = g2.getFontMetrics().stringWidth(initial);
                g2.drawString(initial, (34 - tw) / 2, 23);
                g2.dispose();
            }
        };
        avatar.setOpaque(false);
        avatar.setPreferredSize(new Dimension(34, 34));
        JPanel text = new JPanel();
        text.setOpaque(false);
        text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
        text.add(accountName);
        text.add(accountType);
        chip.add(avatar, BorderLayout.WEST);
        chip.add(text, BorderLayout.CENTER);
        chip.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                show("Accounts");
            }
        });
        JPanel chipWrap = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
        chipWrap.setOpaque(false);
        chipWrap.setAlignmentX(LEFT_ALIGNMENT);
        chipWrap.setMaximumSize(new Dimension(Integer.MAX_VALUE, 56));
        chip.setPreferredSize(new Dimension(186, 52));
        chipWrap.add(chip);
        side.add(chipWrap);
        side.add(Box.createVerticalStrut(8));
        JLabel version = Components.label("Launcher " + Main.VERSION, 10f, Font.PLAIN, Palette.TEXT_DIM);
        version.setBorder(BorderFactory.createEmptyBorder(0, 22, 0, 0));
        version.setAlignmentX(LEFT_ALIGNMENT);
        side.add(version);
        return side;
    }

    private JPanel titleBar() {
        JPanel bar = new JPanel(new BorderLayout());
        bar.setBackground(Palette.BG);
        bar.setPreferredSize(new Dimension(0, 34));
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        buttons.setOpaque(false);
        buttons.add(windowButton("–", false, () -> setState(ICONIFIED)));
        buttons.add(windowButton("✕", true, () -> dispatchEvent(
                new java.awt.event.WindowEvent(this, java.awt.event.WindowEvent.WINDOW_CLOSING))));
        bar.add(buttons, BorderLayout.EAST);

        // Drag the undecorated window by its title bar.
        Point[] origin = new Point[1];
        MouseAdapter drag = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                origin[0] = e.getPoint();
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                Point p = getLocation();
                setLocation(p.x + e.getX() - origin[0].x, p.y + e.getY() - origin[0].y);
            }
        };
        bar.addMouseListener(drag);
        bar.addMouseMotionListener(drag);
        return bar;
    }

    private JButton windowButton(String glyph, boolean danger, Runnable action) {
        JButton button = new JButton(glyph) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = Components.smooth(g);
                if (getModel().isRollover()) {
                    g2.setColor(danger ? Palette.DANGER : Palette.CARD_HOVER);
                    g2.fillRect(0, 0, getWidth(), getHeight());
                }
                g2.setColor(getModel().isRollover() ? Color.WHITE : Palette.TEXT_DIM);
                g2.setFont(Palette.font(Font.PLAIN, 13f));
                int tw = g2.getFontMetrics().stringWidth(getText());
                g2.drawString(getText(), (getWidth() - tw) / 2, getHeight() / 2 + 5);
                g2.dispose();
            }
        };
        button.setPreferredSize(new Dimension(46, 34));
        button.setContentAreaFilled(false);
        button.setBorderPainted(false);
        button.setFocusPainted(false);
        button.setRolloverEnabled(true);
        button.addActionListener(e -> action.run());
        return button;
    }
}
