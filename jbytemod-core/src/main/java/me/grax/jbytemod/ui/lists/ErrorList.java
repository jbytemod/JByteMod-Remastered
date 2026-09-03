package me.grax.jbytemod.ui.lists;

import de.xbrowniecodez.jbytemod.Main;
import de.xbrowniecodez.jbytemod.JByteMod;
import de.xbrowniecodez.jbytemod.ui.SvgIcons;
import me.grax.jbytemod.analysis.errors.EmptyMistake;
import me.grax.jbytemod.analysis.errors.ErrorAnalyzer;
import me.grax.jbytemod.analysis.errors.Mistake;
import me.grax.jbytemod.ui.lists.entries.InstrEntry;
import me.grax.jbytemod.utils.list.LazyListModel;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class ErrorList extends JList<Mistake> {
    private MyCodeList cl;
    private Icon warning;
    private ListCellRenderer<? super Mistake> oldRenderer;
    private JByteMod jbm;
    private final ExecutorService analyzerExecutor;
    private Future<?> analyzerTask;
    private long analyzerRequest;

    public ErrorList(JByteMod jbm, MyCodeList cl) {
        super(new DefaultListModel<Mistake>());
        this.jbm = jbm;
        this.analyzerExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread thread = new Thread(r, "JByteMod error analyzer");
            thread.setDaemon(true);
            return thread;
        });
        this.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        this.warning = SvgIcons.icon("status/warning");
        this.cl = cl;
        cl.setErrorList(this);
        this.setSelectionModel(new DefaultListSelectionModel() {
            @Override
            public void setSelectionInterval(int index0, int index1) {
                super.setSelectionInterval(-1, -1);
            }
        });
        this.oldRenderer = this.getCellRenderer();
        this.setCellRenderer(new CustomCellRenderer());
        this.addMouseListener(new MouseAdapter() {

            @Override
            public void mouseClicked(MouseEvent e) {
                int index = locationToIndex(e.getPoint());
                Mistake error = getModel().getElementAt(index);
                if (!(error instanceof EmptyMistake)) {
                    showPopover(error, e.getXOnScreen(), e.getYOnScreen());
                }
            }
        });
        this.updateErrors();
        //SwingUtils.disableSelection(this);
    }

    private void showPopover(Mistake error, int x, int y) {
        JPopupMenu popover = new JPopupMenu();

        // Create a custom panel to display the error message
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        panel.add(new JLabel(error.getDesc()));

        popover.add(panel);

        // Show the popover at the specified position
        popover.show(jbm, x - jbm.getLocationOnScreen().x, y - jbm.getLocationOnScreen().y);
    }


    public void updateErrors() {
        final long request;
        synchronized (this) {
            analyzerRequest++;
            request = analyzerRequest;
            if (analyzerTask != null) {
                analyzerTask.cancel(true);
                analyzerTask = null;
            }
        }

        setModel(new LazyListModel<Mistake>());
        if (!Main.INSTANCE.getJByteMod().getOptions().get("analyze_errors").getBoolean()
                || jbm.getCurrentMethod() == null) {
            return;
        }

        LazyListModel<InstrEntry> codeModel = (LazyListModel<InstrEntry>) cl.getModel();
        if (codeModel.getSize() > 1000) {
            Main.INSTANCE.getLogger().warn("Not analyzing mistakes, too many instructions!");
            return;
        }

        final ClassNode classNode = jbm.getCurrentNode();
        final MethodNode methodNode = jbm.getCurrentMethod();
        final ArrayList<AbstractInsnNode> instructions = new ArrayList<>();
        for (int i = 0; i < codeModel.getSize(); i++) {
            instructions.add(codeModel.getElementAt(i).getInstr());
        }

        Future<?> task = analyzerExecutor.submit(() -> {
            HashMap<AbstractInsnNode, Mistake> mistakes = new ErrorAnalyzer(classNode, methodNode).findErrors();
            if (Thread.currentThread().isInterrupted()) {
                return;
            }

            LazyListModel<Mistake> result = new LazyListModel<>();
            for (AbstractInsnNode instruction : instructions) {
                Mistake mistake = mistakes.get(instruction);
                result.addElement(mistake == null ? new EmptyMistake() : mistake);
            }

            SwingUtilities.invokeLater(() -> {
                synchronized (ErrorList.this) {
                    if (request != analyzerRequest || jbm.getCurrentMethod() != methodNode) {
                        return;
                    }
                }
                setModel(result);
            });
        });

        synchronized (this) {
            if (request == analyzerRequest) {
                analyzerTask = task;
            } else {
                task.cancel(true);
            }
        }
    }

    class CustomCellRenderer extends JLabel implements ListCellRenderer<Mistake> {
        public Component getListCellRendererComponent(JList<? extends Mistake> list, Mistake value, int index, boolean isSelected, boolean cellHasFocus) {
            Component c = oldRenderer.getListCellRendererComponent(list, value, index, false, false); //hacky hack
            JLabel label = (JLabel) c;
            if (value.getDesc().length() > 1) {
                label.setIcon(warning);
            }
            label.setText("\u200B"); //another hacky hack
            return c;
        }
    }
}
