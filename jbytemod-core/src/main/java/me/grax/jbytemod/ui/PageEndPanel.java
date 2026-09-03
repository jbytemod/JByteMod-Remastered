package me.grax.jbytemod.ui;

import de.xbrowniecodez.jbytemod.ui.MemoryBar;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.time.Year;

/**
 * Panel displayed at the bottom of the page.
 */
public class PageEndPanel extends JPanel {
    private static final int currentYear = Year.now().getValue();
    private static final String COPYRIGHT_TEXT = "\u00A9 brownie 2020 - " + currentYear;
    private JProgressBar progressBar;
    private JLabel percentLabel;
    private JLabel copyrightLabel;
    private MemoryBar memoryBar;

    public PageEndPanel() {
        progressBar = new JProgressBar() {
            @Override
            public void setValue(int n) {
                int value = Math.max(0, Math.min(n, 100));
                if (value == 100) {
                    super.setValue(0);
                    percentLabel.setText("");
                } else {
                    super.setValue(value);
                    percentLabel.setText(value + "%");
                }
            }
        };

        setLayout(new GridLayout(1, 4, 10, 10));
        setBorder(new EmptyBorder(3, 0, 0, 0));

        add(progressBar);
        add(percentLabel = new JLabel());
        percentLabel.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));

        copyrightLabel = new JLabel(COPYRIGHT_TEXT);
        copyrightLabel.setHorizontalAlignment(SwingConstants.RIGHT);
        copyrightLabel.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        add(copyrightLabel);

        memoryBar = new MemoryBar();
        add(memoryBar);
    }

    public void setValue(int n) {
        SwingUtilities.invokeLater(() -> {
            progressBar.setValue(n);
            progressBar.repaint();
        });
    }

    public void setTip(String tooltipText) {
        if (tooltipText != null) {
            copyrightLabel.setText(tooltipText);
        } else {
            copyrightLabel.setText(COPYRIGHT_TEXT);
        }
    }
}
