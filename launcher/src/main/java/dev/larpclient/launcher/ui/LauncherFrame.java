package dev.larpclient.launcher.ui;

import dev.larpclient.launcher.auth.AccountStore;
import dev.larpclient.launcher.config.LauncherSettings;
import dev.larpclient.launcher.game.GameFiles;
import dev.larpclient.launcher.util.Platform;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.ArrayList;
import java.util.List;

/** Main window: icon rail, page cards and overlaid window controls. */
public final class LauncherFrame extends JFrame {
    private static final int WIDTH = 1080;
    private static final int HEIGHT = 680;

    final LauncherSettings settings = LauncherSettings.load(Platform.configDir().resolve("launcher.json"));
    final AccountStore accounts = new AccountStore(Platform.configDir().resolve("accounts.json"));
    final GameFiles files = new GameFiles(Platform.dataDir());

    private final CardLayout cards = new CardLayout();
    private final JPanel pages = new JPanel(cards);
    private final List<NavRail> nav = new ArrayList<>();

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
        setIconImage(Icons.appIcon(128));

        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(Palette.BG);
        root.setBorder(BorderFactory.createLineBorder(new Color(0x23232B)));
        root.add(rail(), BorderLayout.WEST);

        pages.add(home, "Home");
        pages.add(accountsPanel, "Accounts");
        pages.add(settingsPanel, "Settings");
        pages.add(console, "Console");
        root.add(new Overlay(pages, windowControls()), BorderLayout.CENTER);
        setContentPane(root);

        refreshAccount();
        show(startPage);

        // Some window managers (e.g. Hyprland with XWayland) ignore the initial size hint. Re-assert
        // the intended size once the window is mapped; floating windows accept client resizes.
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowOpened(WindowEvent e) {
                getRootPane().requestFocusInWindow();
                new javax.swing.Timer(150, ev -> {
                    if (getWidth() != WIDTH || getHeight() != HEIGHT) {
                        setSize(WIDTH, HEIGHT);
                        setLocationRelativeTo(null);
                    }
                    ((javax.swing.Timer) ev.getSource()).stop();
                }).start();
                reportLoadProblems();
            }
        });
    }

    /** Tell the user when a config file was unreadable and has been moved aside (it is never silently lost). */
    private void reportLoadProblems() {
        List<String> problems = new ArrayList<>();
        if (settings.loadProblem() != null) {
            problems.add(settings.loadProblem());
        }
        if (accounts.loadProblem() != null) {
            problems.add(accounts.loadProblem());
        }
        if (!problems.isEmpty()) {
            javax.swing.SwingUtilities.invokeLater(() -> javax.swing.JOptionPane.showMessageDialog(this,
                    String.join("\n\n", problems) + "\n\nFolder: " + Platform.configDir(),
                    "LARP Client", javax.swing.JOptionPane.WARNING_MESSAGE));
        }
    }

    void show(String page) {
        cards.show(pages, page);
        // Don't let the first button on a page grab a focus ring.
        getRootPane().requestFocusInWindow();
        for (NavRail item : nav) {
            item.setActive(item.getLabel().equals(page));
        }
        if (page.equals("Accounts")) {
            accountsPanel.reload();
        }
    }

    void refreshAccount() {
        home.refresh();
        repaint();
    }

    private JComponent rail() {
        JPanel rail = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                g.setColor(new Color(255, 255, 255, 10));
                g.fillRect(getWidth() - 1, 0, 1, getHeight());
            }
        };
        rail.setBackground(Palette.SIDEBAR);
        rail.setLayout(new BoxLayout(rail, BoxLayout.Y_AXIS));
        rail.setPreferredSize(new Dimension(84, 0));
        rail.setBorder(BorderFactory.createEmptyBorder(20, 0, 16, 0));

        JLabel logo = new JLabel(new javax.swing.ImageIcon(Icons.appIcon(40)));
        logo.setAlignmentX(Component.CENTER_ALIGNMENT);
        rail.add(logo);
        rail.add(Box.createVerticalStrut(28));

        Object[][] items = {{"Home", Icons.Glyph.HOME}, {"Accounts", Icons.Glyph.USER},
                {"Settings", Icons.Glyph.SLIDERS}, {"Console", Icons.Glyph.TERMINAL}};
        for (Object[] item : items) {
            NavRail entry = new NavRail((String) item[0], (Icons.Glyph) item[1], this::show);
            entry.setAlignmentX(Component.CENTER_ALIGNMENT);
            nav.add(entry);
            rail.add(entry);
            rail.add(Box.createVerticalStrut(4));
        }
        rail.add(Box.createVerticalGlue());
        NavRail folder = new NavRail("Files", Icons.Glyph.FOLDER, label -> {
            try {
                java.nio.file.Files.createDirectories(files.instance);
            } catch (java.io.IOException ignored) {
                // opening will simply fail
            }
            Platform.open(files.instance.toString());
        });
        folder.setAlignmentX(Component.CENTER_ALIGNMENT);
        folder.setToolTipText("Open the game folder");
        rail.add(folder);
        return rail;
    }

    private JComponent windowControls() {
        JPanel controls = new JPanel();
        controls.setOpaque(false);
        controls.setLayout(new BoxLayout(controls, BoxLayout.X_AXIS));
        controls.add(windowButton(Icons.Glyph.MINIMIZE, false, () -> setState(ICONIFIED)));
        controls.add(windowButton(Icons.Glyph.CLOSE, true,
                () -> dispatchEvent(new WindowEvent(this, WindowEvent.WINDOW_CLOSING))));
        controls.setSize(controls.getPreferredSize());

        // Drag the undecorated window from the empty top strip.
        Point[] origin = new Point[1];
        MouseAdapter drag = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                origin[0] = e.getLocationOnScreen();
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                Point now = e.getLocationOnScreen();
                Point p = getLocation();
                setLocation(p.x + now.x - origin[0].x, p.y + now.y - origin[0].y);
                origin[0] = now;
            }
        };
        pages.addMouseListener(drag);
        pages.addMouseMotionListener(drag);
        return controls;
    }

    private JComponent windowButton(Icons.Glyph glyph, boolean danger, Runnable action) {
        JComponent button = new JComponent() {
            boolean hover;

            {
                setPreferredSize(new Dimension(46, 36));
                setMaximumSize(new Dimension(46, 36));
                setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                Components.onClick(this, action);
                addMouseListener(new MouseAdapter() {
                    @Override
                    public void mouseEntered(MouseEvent e) {
                        hover = true;
                        repaint();
                    }

                    @Override
                    public void mouseExited(MouseEvent e) {
                        hover = false;
                        repaint();
                    }

                });
            }

            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = Components.smooth(g);
                if (hover) {
                    g2.setColor(danger ? Palette.DANGER : new Color(255, 255, 255, 20));
                    g2.fillRect(0, 0, getWidth(), getHeight());
                }
                Icons.draw(g2, glyph, getWidth() / 2f - 10, getHeight() / 2f - 10, hover ? Color.WHITE : Palette.TEXT_DIM);
                g2.dispose();
            }
        };
        return button;
    }

    /** Lays the window controls over the top-right corner of the pages. */
    private static final class Overlay extends JPanel {
        private final JComponent content;
        private final JComponent controls;

        Overlay(JComponent content, JComponent controls) {
            super(null);
            this.content = content;
            this.controls = controls;
            add(controls);
            add(content);
        }

        @Override
        public void doLayout() {
            content.setBounds(0, 0, getWidth(), getHeight());
            Dimension d = controls.getPreferredSize();
            controls.setBounds(getWidth() - d.width, 0, d.width, d.height);
        }

        @Override
        public boolean isOptimizedDrawingEnabled() {
            return false; // children overlap
        }
    }
}
