package dev.larpclient.launcher.ui;

import dev.larpclient.launcher.util.Platform;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.text.BadLocationException;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;

/** Live game output. */
public final class ConsolePanel extends JPanel {
    private static final int MAX_LINES = 5000;
    private final JTextArea area = new JTextArea();

    ConsolePanel(LauncherFrame frame) {
        super(new BorderLayout(0, 12));
        setBorder(BorderFactory.createEmptyBorder(20, 30, 24, 30));
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        header.add(Components.label("Console", 26f, Font.BOLD, Palette.TEXT), BorderLayout.WEST);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        actions.setOpaque(false);
        JButton copy = new JButton("Copy");
        copy.addActionListener(e -> Toolkit.getDefaultToolkit().getSystemClipboard()
                .setContents(new StringSelection(area.getText()), null));
        JButton clear = new JButton("Clear");
        clear.addActionListener(e -> area.setText(""));
        JButton logs = new JButton("Open logs folder");
        logs.addActionListener(e -> Platform.open(frame.files.instance.resolve("logs").toString()));
        actions.add(copy);
        actions.add(clear);
        actions.add(logs);
        header.add(actions, BorderLayout.EAST);
        add(header, BorderLayout.NORTH);

        area.setEditable(false);
        area.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        area.setBackground(Palette.SIDEBAR);
        area.setForeground(Palette.TEXT);
        area.setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));
        JScrollPane scroll = new JScrollPane(area);
        scroll.setBorder(BorderFactory.createLineBorder(Palette.BORDER));
        add(scroll, BorderLayout.CENTER);
    }

    /** Thread-safe. */
    public void append(String line) {
        SwingUtilities.invokeLater(() -> {
            area.append(line + "\n");
            int excess = area.getLineCount() - MAX_LINES;
            if (excess > 0) {
                try {
                    area.replaceRange("", 0, area.getLineEndOffset(excess - 1));
                } catch (BadLocationException ignored) {
                    // cannot happen for valid line counts
                }
            }
            area.setCaretPosition(area.getDocument().getLength());
        });
    }
}
