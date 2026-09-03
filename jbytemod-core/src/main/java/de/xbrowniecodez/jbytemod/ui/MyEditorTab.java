package de.xbrowniecodez.jbytemod.ui;

import de.xbrowniecodez.jbytemod.JByteMod;
import de.xbrowniecodez.jbytemod.Main;
import lombok.Getter;
import me.grax.jbytemod.analysis.errors.InsnError;
import me.grax.jbytemod.analysis.errors.InsnWarning;
import me.grax.jbytemod.ui.DecompilerTab;
import me.grax.jbytemod.ui.InfoPanel;
import me.grax.jbytemod.ui.graph.ControlFlowPanel;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.Objects;

@Getter
public class MyEditorTab extends JPanel {
    private final String analysisText = Main.INSTANCE.getJByteMod().getLanguageRes().getResource("analysis");
    private final JPanel code;
    private final JPanel info;
    private final DecompilerTab decompiler;
    private final ControlFlowPanel analysis;
    private final CallGraphPanel callGraph;
    private final JPanel center;
    private final JButton codeButton;
    private final JLabel bytecodeStatus = new JLabel();
    private boolean classSelected = false;

    public MyEditorTab(JByteMod jbm) {
        setLayout(new BorderLayout());
        this.center = new JPanel();
        center.setLayout(new GridLayout());
        JLabel label = new JLabel("JByte Mod");

        MyCodeEditor codeEditor = new MyCodeEditor(jbm, label);
        codeEditor.getErrorList().addPropertyChangeListener("model",
                event -> updateBytecodeStatus((ListModel<?>) event.getNewValue()));
        jbm.setCodeList(codeEditor.getEditor());
        this.code = withBorder(createCodeHeader(label), codeEditor);

        InfoPanel sp = new InfoPanel(jbm);
        jbm.setInfoPanel(sp);

        this.info = this.withBorder(new JLabel(jbm.getLanguageRes().getResource("settings")), sp);

        this.decompiler = new DecompilerTab(jbm);
        this.decompiler.setName("decompiler");

        jbm.setControlFlowPanel(this.analysis = new ControlFlowPanel(jbm));
        this.analysis.setName("analysis");

        this.callGraph = new CallGraphPanel(jbm);
        this.callGraph.setName("callGraph");

        center.add(code);

        JPanel selector = new JPanel();
        codeButton = new JButton("Code");
        codeButton.setSelected(true);
        codeButton.addActionListener(e -> showPanel(code));
        JButton infoBtn = new JButton("Info");
        infoBtn.addActionListener(e -> showPanel(info));
        JButton decompilerBtn = new JButton("Decompiler");
        decompilerBtn.addActionListener(e -> {
            showPanel(decompiler);
            decompiler.decompile(jbm.getCurrentNode(), jbm.getCurrentMethod(), false);
        });
        JButton analysisBtn = new JButton(analysisText);
        analysisBtn.addActionListener(e -> {
            showPanel(analysis);
            if (!classSelected) {
                analysis.generateList();
            } else {
                analysis.clear();
            }
        });
        JButton callGraphBtn = new JButton("Call Graph");
        callGraphBtn.addActionListener(e -> {
            showPanel(callGraph);
            callGraph.setRoot(jbm.getCurrentNode(), jbm.getCurrentMethod());
            callGraph.generateGraph();
        });

        selector.add(codeButton);
        selector.add(infoBtn);
        selector.add(decompilerBtn);
        selector.add(analysisBtn);
        selector.add(callGraphBtn);
        selector.setLayout(new FlowLayout(FlowLayout.LEFT));
        this.add(center, BorderLayout.CENTER);
        this.add(selector, BorderLayout.PAGE_END);
    }

    private void showPanel(Component panel) {
        if (center.getComponent(0) != panel) {
            center.removeAll();
            center.add(panel);
            center.revalidate();
            repaint();
        }
    }

    private JPanel createCodeHeader(JLabel method) {
        JPanel header = new JPanel(new BorderLayout(8, 0));
        header.setOpaque(false);
        header.add(method, BorderLayout.CENTER);
        bytecodeStatus.setIcon(SvgIcons.icon("status/warning"));
        bytecodeStatus.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 2));
        bytecodeStatus.setVisible(false);
        header.add(bytecodeStatus, BorderLayout.EAST);
        return header;
    }

    private void updateBytecodeStatus(ListModel<?> model) {
        int errors = 0;
        int warnings = 0;
        String firstProblem = null;
        for (int i = 0; i < model.getSize(); i++) {
            Object value = model.getElementAt(i);
            if (value instanceof InsnError error) {
                errors++;
                if (firstProblem == null) firstProblem = error.getDesc();
            } else if (value instanceof InsnWarning warning) {
                warnings++;
                if (firstProblem == null) firstProblem = warning.getDesc();
            }
        }

        if (errors == 0 && warnings == 0) {
            bytecodeStatus.setVisible(false);
            bytecodeStatus.setToolTipText(null);
            return;
        }

        StringBuilder text = new StringBuilder();
        if (errors > 0) text.append(errors).append(errors == 1 ? " error" : " errors");
        if (warnings > 0) {
            if (!text.isEmpty()) text.append(", ");
            text.append(warnings).append(warnings == 1 ? " warning" : " warnings");
        }
        bytecodeStatus.setText(text.toString());
        bytecodeStatus.setForeground(errors > 0 ? new Color(220, 75, 75) : new Color(220, 155, 45));
        bytecodeStatus.setToolTipText(firstProblem);
        bytecodeStatus.setVisible(true);
    }

    private JPanel withBorder(Component label, Component c) {
        JPanel panel = new JPanel();
        panel.setLayout(new BorderLayout(0, 0));
        JPanel lpad = new JPanel();
        lpad.setBorder(new EmptyBorder(1, 5, 0, 5));
        lpad.setLayout(new GridLayout());
        lpad.add(label);
        panel.add(lpad, BorderLayout.NORTH);
        JScrollPane scp = new JScrollPane(c);
        scp.getVerticalScrollBar().setUnitIncrement(16);
        panel.add(scp, BorderLayout.CENTER);
        return panel;
    }

    public void selectClass(ClassNode cn) {
        decompiler.cancelDecompilation();

        String selectedComponentName = center.getComponent(0).getName();
        if(Objects.nonNull(selectedComponentName)) {
            if(selectedComponentName.equals("decompiler"))
                decompiler.decompile(cn, null, false);
            else if (selectedComponentName.equals("analysis"))
                analysis.clear();
            else if (selectedComponentName.equals("callGraph"))
                callGraph.clear();
        }

        this.classSelected = true;
    }

    public void selectMethod(ClassNode cn, MethodNode mn) {
        decompiler.cancelDecompilation();

        String selectedComponentName = center.getComponent(0).getName();
        if(Objects.nonNull(selectedComponentName)) {
            if (selectedComponentName.equals("decompiler"))
                decompiler.decompile(cn, mn, false);
            else if (selectedComponentName.equals("analysis"))
                analysis.generateList();
            else if (selectedComponentName.equals("callGraph")) {
                callGraph.setRoot(cn, mn);
                callGraph.generateGraph();
            }
        }
        this.classSelected = false;
    }
}
