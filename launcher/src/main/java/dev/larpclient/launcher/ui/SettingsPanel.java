package dev.larpclient.launcher.ui;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.io.IOException;
import java.util.function.Consumer;

/** Launcher and game settings, grouped like a native preferences pane. Saved on every change. */
public final class SettingsPanel extends JPanel {
    private final LauncherFrame frame;

    SettingsPanel(LauncherFrame frame) {
        super(new BorderLayout(0, 22));
        this.frame = frame;
        setBackground(Palette.BG);
        setBorder(BorderFactory.createEmptyBorder(40, 44, 0, 44));
        add(Components.pageHeader("Settings", "Changes are saved automatically."), BorderLayout.NORTH);

        JPanel body = new JPanel();
        body.setOpaque(false);
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));

        // ---- Game
        long totalMb = totalMemoryMb();
        int maxMb = (int) Math.max(2048, Math.min(16384, totalMb - 1024));
        JSlider memory = new JSlider(1024, maxMb, Math.min(maxMb, frame.settings.memoryMb));
        memory.setOpaque(false);
        memory.setPreferredSize(new Dimension(220, 24));
        JLabel memoryValue = new JLabel(gb(memory.getValue()));
        memoryValue.setFont(Fonts.semibold(13f));
        memoryValue.setForeground(Palette.TEXT);
        memoryValue.setPreferredSize(new Dimension(56, 20));
        memory.addChangeListener(e -> {
            int v = (memory.getValue() / 256) * 256;
            memoryValue.setText(gb(v));
            if (!memory.getValueIsAdjusting()) {
                frame.settings.memoryMb = v;
                save();
                frame.home.refresh();
            }
        });
        JSpinner width = new JSpinner(new SpinnerNumberModel(frame.settings.width, 640, 7680, 10));
        JSpinner height = new JSpinner(new SpinnerNumberModel(frame.settings.height, 480, 4320, 10));
        width.setEditor(new JSpinner.NumberEditor(width, "#"));
        height.setEditor(new JSpinner.NumberEditor(height, "#"));
        width.setPreferredSize(new Dimension(84, 30));
        height.setPreferredSize(new Dimension(84, 30));
        width.addChangeListener(e -> {
            frame.settings.width = (Integer) width.getValue();
            save();
        });
        height.addChangeListener(e -> {
            frame.settings.height = (Integer) height.getValue();
            save();
        });
        JCheckBox fullscreen = switchBox(frame.settings.fullscreen, v -> frame.settings.fullscreen = v);
        JLabel times = new JLabel("×");
        times.setForeground(Palette.TEXT_DIM);
        body.add(group("Game",
                row("Memory", "RAM for Minecraft. 2–4 GB is plenty for 1.8.9.", controls(memory, memoryValue)),
                row("Window size", "Size of the game window when it opens.", controls(width, times, height)),
                row("Start in fullscreen", "Compositor-managed fullscreen: works on Hyprland, Sway, KDE and GNOME.", controls(fullscreen)),
                row("Threaded OpenGL", "Mesa glthread / NVIDIA threaded optimisations. Usually a large FPS boost.",
                        controls(switchBox(frame.settings.threadedGl, v -> frame.settings.threadedGl = v)))));

        // ---- Java
        JTextField javaField = field(frame.settings.javaPath, 260, "Automatic", v -> frame.settings.javaPath = v);
        JButton browse = new JButton("Browse");
        browse.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser("/usr/lib/jvm");
            if (chooser.showOpenDialog(frame) == JFileChooser.APPROVE_OPTION) {
                javaField.setText(chooser.getSelectedFile().getAbsolutePath());
            }
        });
        JTextField jvm = field(frame.settings.jvmArgs, 340, "", v -> frame.settings.jvmArgs = v);
        body.add(group("Java",
                row("Java 8 executable", "Leave on automatic to use your system Java 8 or a managed runtime.", controls(javaField, browse)),
                row("JVM arguments", "Advanced. Defaults are tuned for consistent frame times.", controls(jvm))));

        // ---- Launcher
        JCheckBox keepOpen = switchBox(frame.settings.keepLauncherOpen, v -> frame.settings.keepLauncherOpen = v);
        JTextField clientId = field(frame.settings.microsoftClientId, 300, "Azure application (client) ID",
                v -> frame.settings.microsoftClientId = v.trim());
        JLabel where = new JLabel(frame.files.root.toString());
        where.setFont(Fonts.regular(12f));
        where.setForeground(Palette.TEXT_DIM);
        JButton open = new JButton("Open");
        open.addActionListener(e -> {
            try {
                java.nio.file.Files.createDirectories(frame.files.instance);
            } catch (IOException ignored) {
                // nothing to open then
            }
            dev.larpclient.launcher.util.Platform.open(frame.files.instance.toString());
        });
        body.add(group("Launcher",
                row("Keep launcher open", "Stay open while playing to show live logs.", controls(keepOpen)),
                row("Microsoft sign-in", "Client ID of an Azure app approved for Minecraft (see DEVELOPMENT.md).", controls(clientId)),
                row("Game folder", "Worlds, resource packs, screenshots and logs.", controls(where, open))));
        body.add(Box.createVerticalStrut(40));

        JScrollPane scroll = new JScrollPane(body);
        scroll.setBorder(null);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.getVerticalScrollBar().setUnitIncrement(18);
        add(scroll, BorderLayout.CENTER);
    }

    private static String gb(int mb) {
        return String.format("%.1f GB", mb / 1024.0);
    }

    private static long totalMemoryMb() {
        try {
            com.sun.management.OperatingSystemMXBean os =
                    (com.sun.management.OperatingSystemMXBean) java.lang.management.ManagementFactory.getOperatingSystemMXBean();
            return os.getTotalMemorySize() / (1024 * 1024);
        } catch (Throwable t) {
            return 8192;
        }
    }

    private JComponent group(String title, JComponent... rows) {
        JPanel wrap = new JPanel(new BorderLayout());
        wrap.setOpaque(false);
        wrap.add(Components.sectionLabel(title), BorderLayout.NORTH);
        Components.Card card = new Components.Card(null, 16);
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBorder(BorderFactory.createEmptyBorder(4, 20, 4, 18));
        for (int i = 0; i < rows.length; i++) {
            if (i > 0) {
                card.add(new Hairline());
            }
            card.add(rows[i]);
        }
        wrap.add(card, BorderLayout.CENTER);
        wrap.setBorder(BorderFactory.createEmptyBorder(0, 0, 22, 0));
        wrap.setAlignmentX(Component.LEFT_ALIGNMENT);
        wrap.setMaximumSize(new Dimension(Integer.MAX_VALUE, wrap.getPreferredSize().height));
        return wrap;
    }

    private JComponent row(String title, String hint, JComponent control) {
        JPanel row = new JPanel(new BorderLayout(24, 0));
        row.setOpaque(false);
        row.setBorder(BorderFactory.createEmptyBorder(14, 0, 14, 0));
        JPanel text = new JPanel();
        text.setOpaque(false);
        text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
        JLabel t = new JLabel(title);
        t.setFont(Fonts.semibold(14f));
        t.setForeground(Palette.TEXT);
        JLabel h = new JLabel(hint);
        h.setFont(Fonts.regular(12f));
        h.setForeground(Palette.TEXT_DIM);
        text.add(t);
        text.add(Box.createVerticalStrut(3));
        text.add(h);
        row.add(text, BorderLayout.CENTER);
        row.add(control, BorderLayout.EAST);
        return row;
    }

    private static JPanel controls(JComponent... components) {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        panel.setOpaque(false);
        for (JComponent c : components) {
            panel.add(c);
        }
        JPanel center = new JPanel(new java.awt.GridBagLayout());
        center.setOpaque(false);
        center.add(panel);
        return center;
    }

    private JCheckBox switchBox(boolean value, Consumer<Boolean> setter) {
        JCheckBox box = new JCheckBox();
        box.setSelected(value);
        box.setOpaque(false);
        box.putClientProperty("JComponent.sizeVariant", "large");
        box.addActionListener(e -> {
            setter.accept(box.isSelected());
            save();
        });
        return box;
    }

    private JTextField field(String value, int width, String placeholder, Consumer<String> setter) {
        JTextField field = new JTextField(value);
        field.setPreferredSize(new Dimension(width, 32));
        field.putClientProperty("JTextField.placeholderText", placeholder);
        field.getDocument().addDocumentListener(new DocumentListener() {
            private void changed() {
                setter.accept(field.getText());
                save();
            }

            @Override
            public void insertUpdate(DocumentEvent e) {
                changed();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                changed();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                changed();
            }
        });
        return field;
    }

    private void save() {
        try {
            frame.settings.save();
        } catch (IOException e) {
            frame.console.append("[launcher] Could not save settings: " + e.getMessage());
        }
    }

    /** 1px separator between rows. */
    static final class Hairline extends JComponent {
        Hairline() {
            setPreferredSize(new Dimension(10, 1));
            setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
        }

        @Override
        protected void paintComponent(Graphics g) {
            g.setColor(new Color(255, 255, 255, 12));
            g.fillRect(0, 0, getWidth(), 1);
        }
    }
}
