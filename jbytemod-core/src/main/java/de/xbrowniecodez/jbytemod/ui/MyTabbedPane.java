package de.xbrowniecodez.jbytemod.ui;

import de.xbrowniecodez.jbytemod.Main;
import de.xbrowniecodez.jbytemod.JByteMod;
import de.xbrowniecodez.jbytemod.ui.lists.SearchList;
import me.grax.jbytemod.ui.OpcodeTable;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.plaf.TabbedPaneUI;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

public class MyTabbedPane extends JTabbedPane {
    private MyEditorTab editorTab;
    private final ResourceEditorPanel resourceEditor;

    public MyTabbedPane(JByteMod jbm) {
        this.editorTab = new MyEditorTab(jbm);
        this.addTab("Editor", editorTab);
        this.resourceEditor = new ResourceEditorPanel(jbm);
        this.addTab("Resource", resourceEditor);
        this.setEnabledAt(indexOfComponent(resourceEditor), false);
        SearchList searchList = new SearchList(jbm);
        jbm.setSearchList(searchList);
        JLabel search = new JLabel(Main.INSTANCE.getJByteMod().getLanguageRes().getResource("search_results"));
        this.addTab(Main.INSTANCE.getJByteMod().getLanguageRes().getResource("search"), this.withBorder(search, searchList));
        this.addTab("Opcodes", this.withBorder(new JLabel("Opcodes"), new OpcodeTable()));
        //MethodRefPanel mrp = new MethodRefPanel(jbm);
        //jbm.setMethodRefPanel(mrp);
        //this.addTab("References", mrp);
        jbm.setTabbedPane(this);
        this.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent me) {
                if (me.getButton() == 3) {
                    int tabNr = ((TabbedPaneUI) getUI()).tabForCoordinate(MyTabbedPane.this, me.getX(), me.getY());
                    if (tabNr == 0) {
                        JPopupMenu menu = new JPopupMenu();
                        for (ClassNode cn : Main.INSTANCE.getJByteMod().getLastSelectedTreeEntries().keySet()) {
                            String item = cn.name;
                            MethodNode mn = Main.INSTANCE.getJByteMod().getLastSelectedTreeEntries().get(cn);
                            if (mn != null) {
                                item += "." + mn.name;
                            }
                            if (item.length() > 128) {
                                item = "..." + item.substring(item.length() - 128);
                            }
                            JMenuItem remove = new JMenuItem(item);
                            remove.addActionListener(new ActionListener() {
                                public void actionPerformed(ActionEvent e) {
                                    if (mn != null) {
                                        jbm.selectMethod(cn, mn);
                                    } else {
                                        jbm.selectClass(cn);
                                    }
                                }
                            });
                            menu.add(remove);
                        }
                        menu.show(jbm, (int) jbm.getMousePosition().getX(), (int) jbm.getMousePosition().getY());
                    }
                }
            }
        });
    }

    public void selectClass(ClassNode cn) {
        leaveResourceEditor();
        this.editorTab.selectClass(cn);
    }

    public MyEditorTab getEditorTab() {
        return editorTab;
    }

    private JPanel withBorder(JLabel label, Component c) {
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

    public void selectMethod(ClassNode cn, MethodNode mn) {
        leaveResourceEditor();
        this.editorTab.selectMethod(cn, mn);
    }

    public void selectResource(String path) {
        if (!resourceEditor.openResource(path)) {
            return;
        }
        int index = indexOfComponent(resourceEditor);
        String normalized = path.replace('\\', '/');
        int slash = normalized.lastIndexOf('/');
        setTitleAt(index, "Resource: " + (slash < 0 ? normalized : normalized.substring(slash + 1)));
        setEnabledAt(index, true);
        setSelectedIndex(index);
    }

    public void clearResourceEditor() {
        int index = indexOfComponent(resourceEditor);
        if (getSelectedComponent() == resourceEditor) {
            setSelectedComponent(editorTab);
        }
        resourceEditor.clearResource();
        setTitleAt(index, "Resource");
        setEnabledAt(index, false);
    }

    private void leaveResourceEditor() {
        if (getSelectedComponent() == resourceEditor) {
            setSelectedComponent(editorTab);
        }
    }

    @Override
    public Dimension getMinimumSize() {
        return new Dimension(this.getWidth() / 2, 0);
    }
}
