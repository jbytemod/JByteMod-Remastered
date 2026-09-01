package de.xbrowniecodez.jbytemod.ui.dialogue;

import de.xbrowniecodez.jbytemod.utils.apk.ApkSigningConfig;

import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JRadioButton;
import javax.swing.JTextField;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.io.File;
import java.util.Arrays;

public final class ApkSigningDialog {
    private ApkSigningDialog() {
    }

    public static ApkSigningConfig show(Component parent) {
        JRadioButton debugKey = new JRadioButton("JByteMod debug key", true);
        JRadioButton customKey = new JRadioButton("Custom keystore");
        ButtonGroup keyChoices = new ButtonGroup();
        keyChoices.add(debugKey);
        keyChoices.add(customKey);

        JTextField keystore = new JTextField(28);
        JTextField alias = new JTextField(20);
        JPasswordField storePassword = new JPasswordField(20);
        JPasswordField keyPassword = new JPasswordField(20);
        JButton browse = new JButton("Browse...");
        browse.addActionListener(event -> chooseKeystore(parent, keystore));

        JPanel customFields = createCustomFields(
                keystore, browse, alias, storePassword, keyPassword);
        Runnable updateEnabled = () -> setEnabled(customFields, customKey.isSelected());
        debugKey.addActionListener(event -> updateEnabled.run());
        customKey.addActionListener(event -> updateEnabled.run());
        updateEnabled.run();

        JPanel choices = new JPanel();
        choices.add(debugKey);
        choices.add(customKey);

        JPanel panel = new JPanel(new BorderLayout(5, 5));
        panel.add(new JLabel("Choose how the rebuilt APK should be signed:"), BorderLayout.NORTH);
        panel.add(choices, BorderLayout.CENTER);
        panel.add(customFields, BorderLayout.SOUTH);

        while (JOptionPane.showConfirmDialog(parent, panel, "APK signing",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) == JOptionPane.OK_OPTION) {
            if (debugKey.isSelected()) {
                clear(storePassword, keyPassword);
                return ApkSigningConfig.debugKey();
            }
            File file = new File(keystore.getText().trim());
            if (!file.isFile()) {
                JOptionPane.showMessageDialog(parent, "Select an existing JKS or PKCS#12 keystore.",
                        "Invalid keystore", JOptionPane.ERROR_MESSAGE);
                continue;
            }
            char[] storeChars = storePassword.getPassword();
            char[] keyChars = keyPassword.getPassword();
            ApkSigningConfig config = ApkSigningConfig.customKey(
                    file.toPath(), alias.getText(), storeChars, keyChars);
            Arrays.fill(storeChars, '\0');
            Arrays.fill(keyChars, '\0');
            clear(storePassword, keyPassword);
            return config;
        }
        clear(storePassword, keyPassword);
        return null;
    }

    private static JPanel createCustomFields(JTextField keystore, JButton browse,
            JTextField alias, JPasswordField storePassword, JPasswordField keyPassword) {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createTitledBorder("Custom signing key"));
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.insets = new Insets(3, 3, 3, 3);
        constraints.anchor = GridBagConstraints.WEST;
        constraints.fill = GridBagConstraints.HORIZONTAL;

        addRow(panel, constraints, 0, "Keystore:", keystore, browse);
        addRow(panel, constraints, 1, "Alias (optional):", alias, null);
        addRow(panel, constraints, 2, "Store password:", storePassword, null);
        addRow(panel, constraints, 3, "Key password (optional):", keyPassword, null);
        return panel;
    }

    private static void addRow(JPanel panel, GridBagConstraints constraints, int row,
            String label, Component field, Component extra) {
        constraints.gridy = row;
        constraints.gridx = 0;
        constraints.weightx = 0;
        panel.add(new JLabel(label), constraints);
        constraints.gridx = 1;
        constraints.weightx = 1;
        panel.add(field, constraints);
        if (extra != null) {
            constraints.gridx = 2;
            constraints.weightx = 0;
            panel.add(extra, constraints);
        }
    }

    private static void chooseKeystore(Component parent, JTextField field) {
        JFileChooser chooser = new JFileChooser(new File(System.getProperty("user.home", ".")));
        chooser.setDialogTitle("Select APK signing keystore");
        chooser.setFileFilter(new FileNameExtensionFilter(
                "Java keystore (*.jks, *.keystore, *.p12, *.pfx)",
                "jks", "keystore", "p12", "pfx"));
        if (chooser.showOpenDialog(parent) == JFileChooser.APPROVE_OPTION) {
            field.setText(chooser.getSelectedFile().getAbsolutePath());
        }
    }

    private static void setEnabled(Component component, boolean enabled) {
        component.setEnabled(enabled);
        if (component instanceof java.awt.Container container) {
            for (Component child : container.getComponents()) {
                setEnabled(child, enabled);
            }
        }
    }

    private static void clear(JPasswordField... fields) {
        for (JPasswordField field : fields) {
            field.setText("");
        }
    }
}
