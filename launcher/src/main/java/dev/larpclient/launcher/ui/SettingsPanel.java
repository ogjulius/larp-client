package dev.larpclient.launcher.ui;

import dev.larpclient.launcher.util.Platform;

import javax.swing.BorderFactory;
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
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.io.IOException;
import java.util.function.Consumer;

/** Launcher and game settings; every change is saved immediately. */
public final class SettingsPanel extends JPanel {
    private final LauncherFrame frame;

    SettingsPanel(LauncherFrame frame) {
        super(new BorderLayout(0, 16));
        this.frame = frame;
        setBorder(BorderFactory.createEmptyBorder(20, 30, 24, 30));
        add(Components.label("Settings", 26f, Font.BOLD, Palette.TEXT), BorderLayout.NORTH);

        JPanel body = new JPanel();
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));

        // Memory
        long totalMb = totalMemoryMb();
        int maxMb = (int) Math.max(2048, Math.min(16384, totalMb - 1024));
        JSlider memory = new JSlider(1024, maxMb, Math.min(maxMb, frame.settings.memoryMb));
        memory.setMajorTickSpacing(1024);
        memory.setSnapToTicks(false);
        JLabel memoryValue = Components.label(gb(memory.getValue()), 13f, Font.BOLD, Palette.ACCENT_LIGHT);
        memory.addChangeListener(e -> {
            int v = (memory.getValue() / 256) * 256;
            memoryValue.setText(gb(v));
            if (!memory.getValueIsAdjusting()) {
                frame.settings.memoryMb = v;
                save();
            }
        });
        JPanel memRow = new JPanel(new BorderLayout(12, 0));
        memRow.setOpaque(false);
        memRow.add(memory, BorderLayout.CENTER);
        memRow.add(memoryValue, BorderLayout.EAST);
        body.add(section("Memory", "RAM given to Minecraft. 2–4 GB is plenty for 1.8.9 PvP.", memRow));

        // Resolution
        JSpinner width = new JSpinner(new SpinnerNumberModel(frame.settings.width, 640, 7680, 10));
        JSpinner height = new JSpinner(new SpinnerNumberModel(frame.settings.height, 480, 4320, 10));
        width.setEditor(new JSpinner.NumberEditor(width, "#"));
        height.setEditor(new JSpinner.NumberEditor(height, "#"));
        JCheckBox fullscreen = new JCheckBox("Fullscreen", frame.settings.fullscreen);
        width.addChangeListener(e -> {
            frame.settings.width = (Integer) width.getValue();
            save();
        });
        height.addChangeListener(e -> {
            frame.settings.height = (Integer) height.getValue();
            save();
        });
        fullscreen.addActionListener(e -> {
            frame.settings.fullscreen = fullscreen.isSelected();
            save();
        });
        JPanel res = row(width, new JLabel("×"), height, fullscreen);
        body.add(section("Window", "Initial game window size.", res));

        // Java
        JTextField javaField = new JTextField(frame.settings.javaPath, 32);
        javaField.putClientProperty("JTextField.placeholderText", "Automatic (system Java 8, else downloaded)");
        onChange(javaField, v -> frame.settings.javaPath = v);
        JButton browse = new JButton("Browse…");
        browse.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser("/usr/lib/jvm");
            if (chooser.showOpenDialog(frame) == JFileChooser.APPROVE_OPTION) {
                javaField.setText(chooser.getSelectedFile().getAbsolutePath());
            }
        });
        body.add(section("Java executable", "Minecraft 1.8.9 needs Java 8. Leave empty for automatic.", row(javaField, browse)));

        JTextField jvm = new JTextField(frame.settings.jvmArgs, 40);
        onChange(jvm, v -> frame.settings.jvmArgs = v);
        body.add(section("JVM arguments", "Advanced. The defaults are tuned for smooth frame times.", row(jvm)));

        JCheckBox keepOpen = new JCheckBox("Keep the launcher open while playing", frame.settings.keepLauncherOpen);
        keepOpen.addActionListener(e -> {
            frame.settings.keepLauncherOpen = keepOpen.isSelected();
            save();
        });
        body.add(section("Launcher", "Shows live game output in the Console page when kept open.", row(keepOpen)));

        JTextField clientId = new JTextField(frame.settings.microsoftClientId, 32);
        clientId.putClientProperty("JTextField.placeholderText", "Azure application (client) ID");
        onChange(clientId, v -> frame.settings.microsoftClientId = v.trim());
        body.add(section("Microsoft sign-in", "Azure app ID approved for Minecraft login (see DEVELOPMENT.md).", row(clientId)));

        JButton folder = new JButton("Open game folder");
        folder.addActionListener(e -> {
            try {
                java.nio.file.Files.createDirectories(frame.files.instance);
            } catch (IOException ignored) {
                // open will just fail silently
            }
            Platform.open(frame.files.instance.toString());
        });
        JLabel where = Components.label(frame.files.root.toString(), 12f, Font.PLAIN, Palette.TEXT_DIM);
        body.add(section("Files", "Worlds, resource packs, screenshots and logs.", row(folder, where)));

        JScrollPane scroll = new JScrollPane(body);
        scroll.setBorder(null);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
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

    private JPanel section(String title, String hint, JComponent control) {
        Components.Card card = new Components.Card(new BorderLayout(0, 10), 16);
        JPanel text = new JPanel(new BorderLayout());
        text.setOpaque(false);
        text.add(Components.label(title, 14f, Font.BOLD, Palette.TEXT), BorderLayout.NORTH);
        text.add(Components.label(hint, 12f, Font.PLAIN, Palette.TEXT_DIM), BorderLayout.SOUTH);
        card.add(text, BorderLayout.NORTH);
        card.add(control, BorderLayout.CENTER);
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, card.getPreferredSize().height + 10));
        JPanel wrap = new JPanel(new BorderLayout());
        wrap.setBorder(BorderFactory.createEmptyBorder(0, 0, 12, 0));
        wrap.add(card);
        wrap.setMaximumSize(new Dimension(Integer.MAX_VALUE, card.getPreferredSize().height + 22));
        return wrap;
    }

    private static JPanel row(JComponent... components) {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        row.setOpaque(false);
        for (JComponent c : components) {
            row.add(c);
        }
        return row;
    }

    private void onChange(JTextField field, Consumer<String> setter) {
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
    }

    private void save() {
        try {
            frame.settings.save();
        } catch (IOException e) {
            frame.console.append("[launcher] Could not save settings: " + e.getMessage());
        }
    }
}
