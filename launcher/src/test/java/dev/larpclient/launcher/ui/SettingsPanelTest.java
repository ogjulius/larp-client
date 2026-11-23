package dev.larpclient.launcher.ui;

import org.junit.jupiter.api.Test;

import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import java.awt.BorderLayout;
import java.awt.Dimension;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SettingsPanelTest {
    /** A20: rows wider than the window pushed every control off the right edge behind a horizontal scrollbar. */
    @Test
    void rowsFitTheWindowInsteadOfScrollingSideways() {
        JPanel body = SettingsPanel.scrollBody();
        JPanel row = new JPanel(new BorderLayout(24, 0));
        row.add(new JLabel("A very long hint that is much wider than the page could ever be, so it has to shrink"
                + " instead of pushing the control out of sight"), BorderLayout.CENTER);
        JComponent control = new JPanel();
        control.setPreferredSize(new Dimension(340, 32));
        row.add(control, BorderLayout.EAST);
        body.add(row);

        JScrollPane scroll = new JScrollPane(body);
        scroll.setSize(900, 600);
        scroll.doLayout();
        scroll.getViewport().doLayout();
        body.doLayout();
        row.doLayout();

        int viewport = scroll.getViewport().getExtentSize().width;
        assertTrue(body.getWidth() <= viewport, "body " + body.getWidth() + " > viewport " + viewport);
        assertFalse(scroll.getHorizontalScrollBar().isVisible(), "no sideways scrolling");
        assertTrue(control.getX() + control.getWidth() <= viewport, "the control stays visible");
    }
}
