package de.xbrowniecodez.jbytemod.ui;

import de.xbrowniecodez.jbytemod.Main;
import de.xbrowniecodez.jbytemod.JByteMod;
import de.xbrowniecodez.jbytemod.utils.attach.RemoteJarArchive;
import me.grax.jbytemod.ui.JAccessHelper;
import me.grax.jbytemod.utils.ErrorDisplay;

import javax.swing.*;
import java.awt.event.ActionListener;
import java.util.concurrent.ExecutionException;

public class MyToolBar extends JToolBar {
    private MyMenuBar menubar;
    private JButton reloadButton;
    private JButton applyButton;
    private JToggleButton freezeButton;

    public MyToolBar(JByteMod jbm) {
        this.menubar = (MyMenuBar) jbm.getJMenuBar();
        this.setFloatable(false);
        if (!menubar.isAgent()) {
            this.add(makeNavigationButton(Main.INSTANCE.getJByteMod().getLanguageRes().getResource("load"), getIcon("load"), e -> {
                menubar.openLoadDialogue();
            }));
            this.add(makeNavigationButton(Main.INSTANCE.getJByteMod().getLanguageRes().getResource("save"), getIcon("save"), e -> {
                if (menubar.getLastFile() != null) {
                    jbm.saveFile(menubar.getLastFile());
                } else {
                    menubar.openSaveDialogue();
                }
            }));
        } else {
            reloadButton = makeNavigationButton(Main.INSTANCE.getJByteMod().getLanguageRes().getResource("reload"), getIcon("refresh"), e -> {
                jbm.refreshAgentClasses();
            });
            this.add(reloadButton);
            applyButton = makeNavigationButton(Main.INSTANCE.getJByteMod().getLanguageRes().getResource("apply"), getIcon("save"), e -> {
                jbm.applyChangesAgent();
            });
            this.add(applyButton);
            if (jbm.getJarArchive() instanceof RemoteJarArchive) {
                freezeButton = makeNavigationToggleButton("Freeze connected JVM", SvgIcons.icon("toolbar/freeze"));
                freezeButton.addActionListener(e -> setFrozen(jbm));
                this.add(freezeButton);
                this.add(makeNavigationButton("Detach from connected JVM", SvgIcons.icon("toolbar/detach"), e -> {
                    jbm.detachAttachedJvm();
                }));
                this.add(makeNavigationButton("Terminate connected JVM", SvgIcons.icon("toolbar/terminate"), e -> {
                    jbm.terminateAttachedJvm();
                }));
            }
        }
        this.addSeparator();
        this.add(makeNavigationButton(Main.INSTANCE.getJByteMod().getLanguageRes().getResource("search"), getIcon("search"), e -> {
            menubar.searchLDC();
        }));
        this.addSeparator();
        this.add(makeNavigationButton("Access Helper", getIcon("table"), e -> {
            new JAccessHelper().setVisible(true);
        }));
        this.add(makeNavigationButton("Attach to other process", getIcon("plug"), e -> {
            menubar.openProcessSelection();
        }));
    }

    private Icon getIcon(String name) {
        return SvgIcons.icon("toolbar/" + switch (name) {
            case "table" -> "access";
            case "plug" -> "attach";
            default -> name;
        });
    }

    private JToggleButton makeNavigationToggleButton(String action, Icon icon) {
        JToggleButton button = new JToggleButton(icon);
        button.setToolTipText(action);
        button.setFocusable(false);
        button.setBorderPainted(false);
        button.setRolloverEnabled(false);
        return button;
    }

    private void setFrozen(JByteMod jbm) {
        if (!(jbm.getJarArchive() instanceof RemoteJarArchive archive)) {
            freezeButton.setSelected(false);
            return;
        }

        boolean frozen = freezeButton.isSelected();
        freezeButton.setEnabled(false);
        setTargetActionsEnabled(false);
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws Exception {
                archive.setFrozen(frozen);
                return null;
            }

            @Override
            protected void done() {
                boolean succeeded = false;
                try {
                    get();
                    showAttachedJvmFrozen(frozen);
                    succeeded = true;
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    freezeButton.setSelected(!frozen);
                } catch (ExecutionException exception) {
                    freezeButton.setSelected(!frozen);
                    Throwable cause = exception.getCause() == null ? exception : exception.getCause();
                    new ErrorDisplay(cause);
                } finally {
                    freezeButton.setEnabled(true);
                    if (!frozen || !succeeded) setTargetActionsEnabled(true);
                }
            }
        }.execute();
    }

    public void showAttachedJvmFrozen(boolean frozen) {
        if (freezeButton == null) return;
        freezeButton.setSelected(frozen);
        freezeButton.setToolTipText(frozen ? "Resume connected JVM" : "Freeze connected JVM");
        freezeButton.setIcon(SvgIcons.icon(frozen ? "toolbar/resume" : "toolbar/freeze"));
        freezeButton.setEnabled(true);
        setTargetActionsEnabled(!frozen);
    }

    private void setTargetActionsEnabled(boolean enabled) {
        if (reloadButton != null) reloadButton.setEnabled(enabled);
        if (applyButton != null) applyButton.setEnabled(enabled);
    }

    protected JButton makeNavigationButton(String action, Icon i, ActionListener a) {
        JButton button = new JButton(i);
        button.setToolTipText(action);
        button.addActionListener(a);
        button.setFocusable(false);
        button.setBorderPainted(false);
        button.setRolloverEnabled(false);
        return button;
    }
}
