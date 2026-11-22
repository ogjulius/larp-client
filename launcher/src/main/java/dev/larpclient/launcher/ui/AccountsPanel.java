package dev.larpclient.launcher.ui;

import dev.larpclient.launcher.auth.Account;
import dev.larpclient.launcher.auth.MicrosoftAuth;
import dev.larpclient.launcher.util.Platform;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.IOException;

/** Manage offline profiles and Microsoft accounts. */
public final class AccountsPanel extends JPanel {
    private final LauncherFrame frame;
    private final JPanel list = new JPanel();
    private final JTextField offlineName = new JTextField();
    private final JLabel message = new JLabel(" ");

    AccountsPanel(LauncherFrame frame) {
        super(new BorderLayout(0, 22));
        this.frame = frame;
        setBackground(Palette.BG);
        setBorder(BorderFactory.createEmptyBorder(40, 44, 36, 44));
        add(Components.pageHeader("Accounts", "Choose who you play as."), BorderLayout.NORTH);

        JPanel body = new JPanel();
        body.setOpaque(false);
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));

        list.setOpaque(false);
        list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
        JPanel listGroup = new FillWidth(new BorderLayout());
        listGroup.setOpaque(false);
        listGroup.add(Components.sectionLabel("Your accounts"), BorderLayout.NORTH);
        listGroup.add(list, BorderLayout.CENTER);
        listGroup.setAlignmentX(Component.LEFT_ALIGNMENT);
        body.add(listGroup);
        body.add(Box.createVerticalStrut(22));

        JPanel addGroup = new FillWidth(new BorderLayout());
        addGroup.setOpaque(false);
        addGroup.setAlignmentX(Component.LEFT_ALIGNMENT);
        addGroup.add(Components.sectionLabel("Add an account"), BorderLayout.NORTH);
        Components.Card card = new Components.Card(null, 16);
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBorder(BorderFactory.createEmptyBorder(4, 20, 4, 18));

        JButton ms = new JButton("Sign in");
        ms.putClientProperty("JButton.buttonType", "default");
        ms.setBackground(Palette.ACCENT);
        ms.setForeground(Color.WHITE);
        ms.addActionListener(e -> microsoftLogin());
        card.add(addRow("Microsoft account", "Required for online-mode servers such as Hypixel.", ms));
        card.add(new SettingsPanel.Hairline());

        offlineName.setPreferredSize(new Dimension(180, 32));
        offlineName.putClientProperty("JTextField.placeholderText", "Username");
        offlineName.addActionListener(e -> addOffline());
        JButton add = new JButton("Add");
        add.addActionListener(e -> addOffline());
        JPanel offlineControls = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        offlineControls.setOpaque(false);
        offlineControls.add(offlineName);
        offlineControls.add(add);
        card.add(addRow("Offline profile", "For singleplayer and offline-mode servers.", offlineControls));
        addGroup.add(card, BorderLayout.CENTER);
        message.setFont(Fonts.regular(12.5f));
        message.setBorder(BorderFactory.createEmptyBorder(10, 4, 0, 0));
        addGroup.add(message, BorderLayout.SOUTH);
        body.add(addGroup);

        body.add(Box.createVerticalGlue());
        add(body, BorderLayout.CENTER);
        reload();
    }

    /** Panel that stretches horizontally but never vertically inside a BoxLayout. */
    private static final class FillWidth extends JPanel {
        FillWidth(java.awt.LayoutManager layout) {
            super(layout);
            setOpaque(false);
        }

        @Override
        public Dimension getMaximumSize() {
            return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
        }
    }

    private JComponent addRow(String title, String hint, JComponent control) {
        JPanel row = new FillWidth(new BorderLayout(24, 0));
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
        JPanel center = new JPanel(new java.awt.GridBagLayout());
        center.setOpaque(false);
        center.add(control);
        row.add(center, BorderLayout.EAST);
        return row;
    }

    /** Put the caret in the username box (used when Launch is pressed without an account). */
    void focusUsername() {
        SwingUtilities.invokeLater(offlineName::requestFocusInWindow);
    }

    void reload() {
        list.removeAll();
        Components.Card card = new Components.Card(null, 16) {
            @Override
            public Dimension getMaximumSize() {
                return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
            }
        };
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBorder(BorderFactory.createEmptyBorder(4, 14, 4, 14));
        if (frame.accounts.all().isEmpty()) {
            JLabel empty = new JLabel("No accounts yet — add one below.");
            empty.setFont(Fonts.regular(13f));
            empty.setForeground(Palette.TEXT_DIM);
            empty.setBorder(BorderFactory.createEmptyBorder(16, 6, 16, 6));
            card.add(empty);
        }
        boolean first = true;
        for (Account account : frame.accounts.all()) {
            if (!first) {
                card.add(new SettingsPanel.Hairline());
            }
            first = false;
            card.add(accountRow(account));
        }
        card.setAlignmentX(Component.LEFT_ALIGNMENT);
        list.add(card);
        list.revalidate();
        list.repaint();
    }

    private JComponent accountRow(Account account) {
        boolean selected = account == frame.accounts.selected();
        JPanel row = new FillWidth(new BorderLayout(14, 0));
        row.setBorder(BorderFactory.createEmptyBorder(10, 4, 10, 4));
        BufferedImage skin = Skins.get(account, frame.files, this::reload);
        BufferedImage face = skin == null ? null : GameArt.face(skin, 36);
        JComponent avatar = new JComponent() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = Components.smooth(g);
                g2.setClip(new RoundRectangle2D.Float(0, 0, 36, 36, 10, 10));
                if (face != null) {
                    g2.drawImage(face, 0, 0, null);
                } else {
                    g2.setColor(Palette.ACCENT_DARK);
                    g2.fillRect(0, 0, 36, 36);
                }
                g2.dispose();
            }
        };
        avatar.setPreferredSize(new Dimension(36, 36));
        row.add(avatar, BorderLayout.WEST);

        JPanel text = new JPanel();
        text.setOpaque(false);
        text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
        JLabel name = new JLabel(account.username);
        name.setFont(Fonts.semibold(14f));
        name.setForeground(Palette.TEXT);
        JLabel type = new JLabel(account.type == Account.Type.MICROSOFT ? "Microsoft account" : "Offline profile");
        type.setFont(Fonts.regular(12f));
        type.setForeground(Palette.TEXT_DIM);
        text.add(name);
        text.add(Box.createVerticalStrut(2));
        text.add(type);
        row.add(text, BorderLayout.CENTER);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        actions.setOpaque(false);
        if (selected) {
            actions.add(badge("Active"));
        } else {
            JButton use = new JButton("Use");
            use.addActionListener(e -> {
                frame.accounts.select(account);
                persist();
            });
            actions.add(use);
        }
        JButton remove = new JButton("Remove");
        remove.putClientProperty("JButton.buttonType", "borderless");
        remove.setForeground(Palette.TEXT_DIM);
        remove.addActionListener(e -> {
            frame.accounts.remove(account);
            persist();
        });
        actions.add(remove);
        JPanel center = new JPanel(new java.awt.GridBagLayout());
        center.setOpaque(false);
        center.add(actions);
        row.add(center, BorderLayout.EAST);
        return row;
    }

    private static JComponent badge(String text) {
        JLabel label = new JLabel(text) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = Components.smooth(g);
                g2.setColor(new Color(70, 196, 126, 36));
                g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), getHeight(), getHeight()));
                g2.dispose();
                super.paintComponent(g);
            }
        };
        label.setFont(Fonts.semibold(11.5f));
        label.setForeground(Palette.SUCCESS);
        label.setBorder(BorderFactory.createEmptyBorder(4, 10, 4, 10));
        return label;
    }

    private void persist() {
        try {
            frame.accounts.save();
        } catch (IOException e) {
            showMessage("Could not save accounts: " + e.getMessage(), true);
        }
        reload();
        frame.refreshAccount();
    }

    private void addOffline() {
        String name = offlineName.getText().trim();
        if (!Account.isValidUsername(name)) {
            showMessage("Usernames are 3–16 characters: letters, numbers and underscores.", true);
            return;
        }
        boolean first = frame.accounts.all().isEmpty();
        frame.accounts.add(Account.offline(name));
        offlineName.setText("");
        showMessage("Added " + name + ".", false);
        persist();
        if (first) {
            frame.show("Home"); // first account: straight back to the Launch button
        }
    }

    private void showMessage(String text, boolean error) {
        message.setText(text);
        message.setForeground(error ? new Color(0xFF7A7F) : Palette.SUCCESS);
    }

    private void microsoftLogin() {
        MicrosoftAuth auth;
        try {
            auth = new MicrosoftAuth(frame.settings.microsoftClientId);
        } catch (IllegalArgumentException e) {
            showMessage("Microsoft sign-in needs an approved Azure client ID — set it in Settings.", true);
            return;
        }
        JDialog dialog = new JDialog(frame, "Sign in with Microsoft", true);
        dialog.setUndecorated(true);
        Components.Card content = new Components.Card(null, 18);
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBorder(BorderFactory.createEmptyBorder(26, 28, 24, 28));
        JLabel title = new JLabel("Sign in with Microsoft");
        title.setFont(Fonts.bold(18f));
        title.setForeground(Palette.TEXT);
        JLabel info = new JLabel("Requesting a code…");
        info.setFont(Fonts.regular(13f));
        info.setForeground(Palette.TEXT_DIM);
        JLabel code = new JLabel(" ");
        code.setFont(Fonts.bold(36f));
        code.setForeground(Palette.TEXT);
        JLabel link = new JLabel(" ");
        link.setFont(Fonts.regular(13f));
        link.setForeground(Palette.ACCENT_LIGHT);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        actions.setOpaque(false);
        JButton open = new JButton("Open browser");
        JButton copy = new JButton("Copy code");
        JButton cancel = new JButton("Cancel");
        open.setEnabled(false);
        copy.setEnabled(false);
        actions.add(open);
        actions.add(Box.createHorizontalStrut(8));
        actions.add(copy);
        actions.add(Box.createHorizontalStrut(8));
        actions.add(cancel);
        for (JComponent c : new JComponent[]{title, info, code, link, actions}) {
            c.setAlignmentX(LEFT_ALIGNMENT);
            content.add(c);
            content.add(Box.createVerticalStrut(c == title ? 6 : 12));
        }
        dialog.setContentPane(content);
        dialog.getRootPane().setBorder(BorderFactory.createLineBorder(Palette.BORDER));
        dialog.setSize(440, 270);
        dialog.setLocationRelativeTo(frame);

        boolean[] cancelled = {false};
        cancel.addActionListener(e -> {
            cancelled[0] = true;
            dialog.dispose();
        });
        Thread worker = new Thread(() -> {
            try {
                MicrosoftAuth.DeviceCode deviceCode = auth.requestDeviceCode();
                SwingUtilities.invokeLater(() -> {
                    info.setText("Go to the address below and enter this code:");
                    code.setText(deviceCode.userCode);
                    link.setText(deviceCode.verificationUri);
                    open.setEnabled(true);
                    copy.setEnabled(true);
                    open.addActionListener(e -> Platform.open(deviceCode.verificationUri));
                    copy.addActionListener(e -> Toolkit.getDefaultToolkit().getSystemClipboard()
                            .setContents(new StringSelection(deviceCode.userCode), null));
                });
                Platform.open(deviceCode.verificationUri);
                Account account = auth.awaitLogin(deviceCode, () -> cancelled[0]);
                SwingUtilities.invokeLater(() -> {
                    frame.accounts.add(account);
                    persist();
                    showMessage("Signed in as " + account.username + ".", false);
                    dialog.dispose();
                });
            } catch (IOException e) {
                SwingUtilities.invokeLater(() -> {
                    if (!cancelled[0]) {
                        showMessage(e.getMessage(), true);
                    }
                    dialog.dispose();
                });
            }
        }, "larp-msa");
        worker.setDaemon(true);
        worker.start();
        dialog.setVisible(true);
    }
}
