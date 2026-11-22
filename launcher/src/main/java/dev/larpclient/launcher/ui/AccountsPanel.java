package dev.larpclient.launcher.ui;

import dev.larpclient.launcher.auth.Account;
import dev.larpclient.launcher.auth.MicrosoftAuth;
import dev.larpclient.launcher.util.Platform;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.io.IOException;

/** Manage offline profiles and Microsoft accounts. */
public final class AccountsPanel extends JPanel {
    private final LauncherFrame frame;
    private final JPanel list = new JPanel();
    private final JTextField offlineName = new JTextField(16);
    private final JLabel message = Components.label(" ", 12f, Font.PLAIN, Palette.TEXT_DIM);

    AccountsPanel(LauncherFrame frame) {
        super(new BorderLayout(0, 16));
        this.frame = frame;
        setBorder(BorderFactory.createEmptyBorder(20, 30, 24, 30));
        add(Components.label("Accounts", 26f, Font.BOLD, Palette.TEXT), BorderLayout.NORTH);

        list.setOpaque(false);
        list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
        JPanel listWrap = new JPanel(new BorderLayout());
        listWrap.setOpaque(false);
        listWrap.add(list, BorderLayout.NORTH);
        add(listWrap, BorderLayout.CENTER);

        JPanel add = new JPanel();
        add.setOpaque(false);
        add.setLayout(new BoxLayout(add, BoxLayout.Y_AXIS));

        Components.Card ms = new Components.Card(new BorderLayout(16, 0), 16);
        JPanel msText = new JPanel();
        msText.setOpaque(false);
        msText.setLayout(new BoxLayout(msText, BoxLayout.Y_AXIS));
        msText.add(Components.label("Microsoft account", 15f, Font.BOLD, Palette.TEXT));
        msText.add(Components.label("Play on online-mode servers with the account that owns Minecraft.", 12f, Font.PLAIN, Palette.TEXT_DIM));
        ms.add(msText, BorderLayout.CENTER);
        JButton msButton = new JButton("Sign in with Microsoft");
        msButton.putClientProperty("JButton.buttonType", "roundRect");
        msButton.addActionListener(e -> microsoftLogin());
        ms.add(msButton, BorderLayout.EAST);
        ms.setMaximumSize(new Dimension(Integer.MAX_VALUE, 80));

        Components.Card offline = new Components.Card(new BorderLayout(16, 0), 16);
        JPanel offText = new JPanel();
        offText.setOpaque(false);
        offText.setLayout(new BoxLayout(offText, BoxLayout.Y_AXIS));
        offText.add(Components.label("Offline profile", 15f, Font.BOLD, Palette.TEXT));
        offText.add(Components.label("Singleplayer and offline-mode servers only.", 12f, Font.PLAIN, Palette.TEXT_DIM));
        offline.add(offText, BorderLayout.CENTER);
        JPanel offInput = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        offInput.setOpaque(false);
        offlineName.putClientProperty("JTextField.placeholderText", "Username");
        JButton offButton = new JButton("Add");
        offButton.addActionListener(e -> addOffline());
        offlineName.addActionListener(e -> addOffline());
        offInput.add(offlineName);
        offInput.add(offButton);
        offline.add(offInput, BorderLayout.EAST);
        offline.setMaximumSize(new Dimension(Integer.MAX_VALUE, 80));

        add.add(ms);
        add.add(Box.createVerticalStrut(12));
        add.add(offline);
        add.add(Box.createVerticalStrut(10));
        add.add(message);
        add(add, BorderLayout.SOUTH);
        reload();
    }

    void reload() {
        list.removeAll();
        if (frame.accounts.all().isEmpty()) {
            list.add(Components.label("No accounts yet. Add one below.", 13f, Font.PLAIN, Palette.TEXT_DIM));
        }
        for (Account account : frame.accounts.all()) {
            boolean selected = account == frame.accounts.selected();
            Components.Card row = new Components.Card(new BorderLayout(12, 0), 14)
                    .fill(selected ? Palette.CARD_HOVER : Palette.CARD);
            row.setBorder(BorderFactory.createEmptyBorder(10, 14, 10, 10));
            row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 56));
            JLabel name = Components.label(account.username, 15f, Font.BOLD, Palette.TEXT);
            JLabel type = Components.label(account.type == Account.Type.MICROSOFT ? "Microsoft" : "Offline",
                    12f, Font.PLAIN, selected ? Palette.ACCENT_LIGHT : Palette.TEXT_DIM);
            JPanel text = new JPanel(new BorderLayout());
            text.setOpaque(false);
            text.add(name, BorderLayout.NORTH);
            text.add(type, BorderLayout.SOUTH);
            row.add(text, BorderLayout.CENTER);
            JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
            buttons.setOpaque(false);
            if (!selected) {
                JButton use = new JButton("Use");
                use.addActionListener(e -> {
                    frame.accounts.select(account);
                    persist();
                });
                buttons.add(use);
            } else {
                buttons.add(Components.label("Active  ", 12f, Font.BOLD, Palette.SUCCESS));
            }
            JButton remove = new JButton("Remove");
            remove.addActionListener(e -> {
                frame.accounts.remove(account);
                persist();
            });
            buttons.add(remove);
            row.add(buttons, BorderLayout.EAST);
            list.add(row);
            list.add(Box.createVerticalStrut(8));
        }
        list.revalidate();
        list.repaint();
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
            showMessage("Usernames are 3-16 characters: letters, digits and _", true);
            return;
        }
        frame.accounts.add(Account.offline(name));
        offlineName.setText("");
        showMessage("Added offline profile " + name, false);
        persist();
    }

    private void showMessage(String text, boolean error) {
        message.setText(text);
        message.setForeground(error ? Palette.DANGER : Palette.SUCCESS);
    }

    private void microsoftLogin() {
        MicrosoftAuth auth;
        try {
            auth = new MicrosoftAuth(frame.settings.microsoftClientId);
        } catch (IllegalArgumentException e) {
            showMessage("Set your Azure app's client ID in Settings first (see DEVELOPMENT.md).", true);
            return;
        }
        JDialog dialog = new JDialog(frame, "Sign in with Microsoft", true);
        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBorder(BorderFactory.createEmptyBorder(22, 26, 22, 26));
        JLabel info = Components.label("Requesting a sign-in code…", 13f, Font.PLAIN, Palette.TEXT_DIM);
        JLabel code = Components.label(" ", 34f, Font.BOLD, Palette.TEXT);
        JLabel link = Components.label(" ", 13f, Font.PLAIN, Palette.ACCENT_LIGHT);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        JButton open = new JButton("Open browser");
        JButton copy = new JButton("Copy code");
        JButton cancel = new JButton("Cancel");
        actions.add(open);
        actions.add(Box.createHorizontalStrut(8));
        actions.add(copy);
        actions.add(Box.createHorizontalStrut(8));
        actions.add(cancel);
        open.setEnabled(false);
        copy.setEnabled(false);
        for (javax.swing.JComponent c : new javax.swing.JComponent[]{info, code, link, actions}) {
            c.setAlignmentX(LEFT_ALIGNMENT);
            content.add(c);
            content.add(Box.createVerticalStrut(10));
        }
        dialog.setContentPane(content);
        dialog.setSize(460, 260);
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
                    info.setText("Open the link below and enter this code:");
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
                    showMessage("Signed in as " + account.username, false);
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
