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
import java.awt.GridBagLayout;
import java.awt.Font;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;

/** Live game output. */
public final class ConsolePanel extends JPanel {
    private static final int MAX_LINES = 5000;
    private final JTextArea area = new JTextArea() {
        @Override
        protected void paintComponent(java.awt.Graphics g) {
            super.paintComponent(g);
            if (getDocument().getLength() == 0) {
                java.awt.Graphics2D g2 = Components.smooth(g);
                g2.setFont(Fonts.regular(13f));
                g2.setColor(Palette.TEXT_DIM);
                g2.drawString("Game output will appear here after you press Launch.", 18, 32);
                g2.dispose();
            }
        }
    };

    ConsolePanel(LauncherFrame frame) {
        super(new BorderLayout(0, 22));
        setBackground(Palette.BG);
        setBorder(BorderFactory.createEmptyBorder(40, 44, 36, 44));
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        header.add(Components.pageHeader("Console", "Live output from the game and the launcher."), BorderLayout.WEST);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        actions.setOpaque(false);
        JButton copy = new JButton("Copy");
        copy.addActionListener(e -> Toolkit.getDefaultToolkit().getSystemClipboard()
                .setContents(new StringSelection(area.getText()), null));
        JButton clear = new JButton("Clear");
        clear.addActionListener(e -> area.setText(""));
        JButton logs = new JButton("Logs folder");
        logs.addActionListener(e -> Platform.open(frame.files.instance.resolve("logs").toString()));
        for (JButton b : new JButton[]{copy, clear, logs}) {
            b.setFocusable(false);
        }
        actions.add(copy);
        actions.add(clear);
        actions.add(logs);
        JPanel actionsWrap = new JPanel(new GridBagLayout());
        actionsWrap.setOpaque(false);
        actionsWrap.add(actions);
        header.add(actionsWrap, BorderLayout.EAST);
        add(header, BorderLayout.NORTH);

        area.setEditable(false);
        area.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        area.setOpaque(false);
        area.setForeground(new java.awt.Color(0xC9C9D4));
        area.setBorder(BorderFactory.createEmptyBorder(14, 16, 14, 16));
        JScrollPane scroll = new JScrollPane(area);
        scroll.setBorder(null);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        Components.Card card = new Components.Card(new BorderLayout(), 16).fill(new java.awt.Color(0x0D0D10));
        card.setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 2));
        card.add(scroll);
        add(card, BorderLayout.CENTER);
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
