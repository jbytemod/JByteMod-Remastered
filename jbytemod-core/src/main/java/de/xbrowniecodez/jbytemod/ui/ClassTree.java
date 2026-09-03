package de.xbrowniecodez.jbytemod.ui;

import de.xbrowniecodez.jbytemod.Main;
import de.xbrowniecodez.jbytemod.JByteMod;
import de.xbrowniecodez.jbytemod.archive.AndroidArchive;
import me.grax.jbytemod.JarArchive;
import de.xbrowniecodez.jbytemod.ui.dialogue.InsnEditDialogue;
import de.xbrowniecodez.jbytemod.ui.tree.SortedTreeNode;
import me.grax.jbytemod.utils.ErrorDisplay;
import me.grax.jbytemod.utils.MethodUtils;
import me.grax.jbytemod.utils.asm.FrameGen;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import javax.swing.*;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class ClassTree extends JTree {

    private final Set<String> expandedNodes = new HashSet<>();
    private JByteMod jbm;
    private DefaultTreeModel model;
    private HashMap<String, SortedTreeNode> preloadMap;
    private boolean treeInitialized;

    public ClassTree(JByteMod jam) {
        this.jbm = jam;
        this.setRootVisible(true);
        this.setShowsRootHandles(true);
        this.setCellRenderer(new TreeCellRenderer());
        this.addTreeSelectionListener(new TreeSelectionListener() {
            public void valueChanged(TreeSelectionEvent e) {
                SortedTreeNode node = (SortedTreeNode) ClassTree.this.getLastSelectedPathComponent();
                if (node == null)
                    return;
                if (node.getClassNode() != null && node.getMethodNode() != null) {
                    jam.selectMethod(node.getClassNode(), node.getMethodNode());
                } else if (node.getClassNode() != null) {
                    jam.selectClass(node.getClassNode());
                } else if (node.getNodeKind() == SortedTreeNode.NodeKind.RESOURCE
                        && node.getTreePathKey() != null) {
                    jam.selectResource(node.getTreePathKey().substring("resource:".length()));
                }
            }
        });
        this.model = new DefaultTreeModel(new SortedTreeNode(
                "No archive", SortedTreeNode.NodeKind.ARCHIVE, "archive"));
        this.setModel(model);
        this.setTransferHandler(new FileDropHandler(jbm::loadFile));
        this.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
        addListener();
    }

    public void refreshTree(JarArchive jar) {
        DefaultTreeModel tm = this.model;
        SortedTreeNode root = new SortedTreeNode(
                archiveLabel(jar), jar instanceof AndroidArchive
                        ? SortedTreeNode.NodeKind.ANDROID_ARCHIVE
                        : SortedTreeNode.NodeKind.ARCHIVE, "archive");

        int classCount = jar.getClasses() == null ? 0 : jar.getClasses().size();
        SortedTreeNode classesRoot = new SortedTreeNode(
                "Classes (" + classCount + ")", SortedTreeNode.NodeKind.CLASSES, "classes");
        root.add(classesRoot);

        preloadMap = new HashMap<>();
        if (jar.getClasses() != null)
            for (ClassNode c : jar.getClasses().values()) {
                String name = c.name.replace("<html>", "HTMLCrashtag");
                String[] path = array_unique(name.split("/"));
                name = String.join("/", path);

                int i = 0;
                int slashIndex = 0;
                SortedTreeNode prev = classesRoot;
                while (true) {
                    slashIndex = name.indexOf("/", slashIndex + 1);
                    if (slashIndex == -1) {
                        break;
                    }
                    String p = name.substring(0, slashIndex);
                    if (preloadMap.containsKey(p)) {
                        prev = preloadMap.get(p);
                    } else {
                        try{
                            SortedTreeNode stn = new SortedTreeNode(path[i],
                                    SortedTreeNode.NodeKind.PACKAGE, "package:" + p);
                            prev.add(stn);
                            prev = stn;
                            preloadMap.put(p, prev);
                        }catch(ArrayIndexOutOfBoundsException ex){
                             Main.INSTANCE.getLogger().println("Failed to load " + path[i]);
                        }
                    }
                    i++;
                }
                SortedTreeNode clazz = new SortedTreeNode(c);
                prev.add(clazz);
                for (MethodNode m : c.methods) {
                    clazz.add(new SortedTreeNode(c, m));
                }
            }
        addResources(root, jar.getOutput());
        boolean sort = Main.INSTANCE.getJByteMod().getOptions().get("sort_methods").getBoolean();
        sort(tm, root, sort);
        tm.setRoot(root);
        if (!treeInitialized) {
            expandedNodes.add("archive");
            expandedNodes.add("classes");
            treeInitialized = true;
        }
        expandSaved(root);
        revalidate();
        repaint();
    }

    private void addResources(SortedTreeNode root, Map<String, byte[]> resources) {
        List<String> names = resources == null ? List.of() : resources.keySet().stream()
                .map(name -> name.replace('\\', '/'))
                .filter(name -> !name.isBlank())
                .filter(name -> !name.endsWith("/"))
                .sorted()
                .toList();
        SortedTreeNode resourcesRoot = new SortedTreeNode(
                "Resources (" + names.size() + ")", SortedTreeNode.NodeKind.RESOURCES, "resources");
        root.add(resourcesRoot);

        Map<String, SortedTreeNode> directories = new HashMap<>();
        for (String name : names) {
            String[] path = Arrays.stream(name.split("/"))
                    .filter(segment -> !segment.isEmpty())
                    .toArray(String[]::new);
            if (path.length == 0) continue;

            SortedTreeNode parent = resourcesRoot;
            StringBuilder directoryPath = new StringBuilder();
            for (int index = 0; index < path.length - 1; index++) {
                if (!directoryPath.isEmpty()) directoryPath.append('/');
                directoryPath.append(path[index]);
                String key = directoryPath.toString();
                SortedTreeNode directory = directories.get(key);
                if (directory == null) {
                    directory = new SortedTreeNode(path[index],
                            SortedTreeNode.NodeKind.RESOURCE_DIRECTORY, "resource-directory:" + key);
                    parent.add(directory);
                    directories.put(key, directory);
                }
                parent = directory;
            }
            parent.add(new SortedTreeNode(path[path.length - 1],
                    SortedTreeNode.NodeKind.RESOURCE, "resource:" + name));
        }
    }

    private String archiveLabel(JarArchive archive) {
        File source = jbm.getFilePath();
        if (source != null && source.isFile()) {
            return source.getName() + " (" + formatSize(source.length()) + ")";
        }
        String name = jbm.getLastEditFile();
        if (name == null || name.isBlank()) {
            name = archive.isSingleEntry() ? "Class file" : "Archive";
        }
        return name;
    }

    private static String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        String[] units = {"KB", "MB", "GB", "TB"};
        double value = bytes;
        int unit = -1;
        do {
            value /= 1024d;
            unit++;
        } while (value >= 1024d && unit < units.length - 1);
        return String.format(Locale.ROOT, "%.1f %s", value, units[unit]);
    }


    public static String[] array_unique(String[] ss) {
        // array_unique
        List<String> list =new ArrayList<String>();
        for(String s:ss){
            if(!list.contains(s))			//或者list.indexOf(s)!=-1
                list.add(s);
        }
        return list.toArray(new String[list.size()]);
    }

    public void expandSaved(SortedTreeNode node) {
        TreePath tp = new TreePath(node.getPath());
        if (expandedNodes.contains(expansionKey(node, tp))) {
            super.expandPath(tp);
        }
        if (node.getChildCount() >= 0) {
            for (Enumeration<?> e = node.children(); e.hasMoreElements(); ) {
                SortedTreeNode n = (SortedTreeNode) e.nextElement();
                expandSaved(n);
            }
        }
    }

    @Override
    public void expandPath(TreePath path) {
        SortedTreeNode stn = (SortedTreeNode) path.getLastPathComponent();
        expandedNodes.add(expansionKey(stn, path));
        super.expandPath(path);
    }

    @Override
    public void collapsePath(TreePath path) {
        SortedTreeNode stn = (SortedTreeNode) path.getLastPathComponent();
        expandedNodes.remove(expansionKey(stn, path));
        super.collapsePath(path);
    }

    private String expansionKey(SortedTreeNode node, TreePath path) {
        if (node.getTreePathKey() != null) {
            return node.getTreePathKey();
        }
        return "path:" + path;
    }

    private void addListener() {
        this.addMouseListener(new MouseAdapter() {
            public void mousePressed(MouseEvent me) {
                if (SwingUtilities.isRightMouseButton(me)) {
                    TreePath tp = ClassTree.this.getPathForLocation(me.getX(), me.getY());
                    if (tp != null && tp.getParentPath() != null) {
                        ClassTree.this.setSelectionPath(tp);
                        if (ClassTree.this.getLastSelectedPathComponent() == null) {
                            return;
                        }
                        SortedTreeNode stn = (SortedTreeNode) ClassTree.this.getLastSelectedPathComponent();
                        MethodNode mn = stn.getMethodNode();
                        ClassNode cn = stn.getClassNode();

                        if (mn != null) {
                            //method selected
                            JPopupMenu menu = new JPopupMenu();
                            JMenuItem edit = new JMenuItem(Main.INSTANCE.getJByteMod().getLanguageRes().getResource("edit"));
                            edit.addActionListener(new ActionListener() {
                                public void actionPerformed(ActionEvent e) {
                                    new InsnEditDialogue(mn, mn).open();
                                    changedChilds((TreeNode) model.getRoot());
                                }
                            });
                            menu.add(edit);
                            JMenuItem duplicate = new JMenuItem(Main.INSTANCE.getJByteMod().getLanguageRes().getResource("duplicate"));
                            duplicate.addActionListener(new ActionListener() {
                                public void actionPerformed(ActionEvent e) {
                                    MethodNode dup = MethodUtils.copy(mn);
                                    String name = JOptionPane.showInputDialog(null, "Duplicated method name ?", "Rename", JOptionPane.QUESTION_MESSAGE);
                                    if(MethodUtils.equalName(cn, name)){
                                        JOptionPane.showMessageDialog(null, "The name is already existed.", "Existed Name!", JOptionPane.WARNING_MESSAGE);
                                        return;
                                    }
                                    dup.name = name;
                                    cn.methods.add(dup);
                                    jbm.getJarTree().refreshTree(jbm.getJarArchive());
                                }
                            });
                            menu.add(duplicate);
                            JMenuItem search = new JMenuItem(Main.INSTANCE.getJByteMod().getLanguageRes().getResource("search"));
                            search.addActionListener(new ActionListener() {
                                public void actionPerformed(ActionEvent e) {
                                    jbm.getSearchList().searchForFMInsn(cn.name, mn.name, mn.desc, false, false);
                                }
                            });
                            menu.add(search);
                            JMenuItem remove = new JMenuItem(Main.INSTANCE.getJByteMod().getLanguageRes().getResource("remove"));
                            remove.addActionListener(new ActionListener() {
                                public void actionPerformed(ActionEvent e) {
                                    if (JOptionPane.showConfirmDialog(Main.INSTANCE.getJByteMod(), Main.INSTANCE.getJByteMod().getLanguageRes().getResource("confirm_remove"),
                                            Main.INSTANCE.getJByteMod().getLanguageRes().getResource("confirm"), JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) {
                                        cn.methods.remove(mn);
                                        model.removeNodeFromParent(stn);
                                    }
                                }
                            });
                            menu.add(remove);
                            JMenu tools = new JMenu(Main.INSTANCE.getJByteMod().getLanguageRes().getResource("tools"));
                            JMenuItem clear = new JMenuItem(Main.INSTANCE.getJByteMod().getLanguageRes().getResource("clear"));
                            clear.addActionListener(new ActionListener() {
                                public void actionPerformed(ActionEvent e) {
                                    if (JOptionPane.showConfirmDialog(Main.INSTANCE.getJByteMod(), Main.INSTANCE.getJByteMod().getLanguageRes().getResource("confirm_clear"), Main.INSTANCE.getJByteMod().getLanguageRes().getResource("confirm"),
                                            JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) {
                                        MethodUtils.clear(mn);
                                        jbm.selectMethod(cn, mn);
                                    }
                                }
                            });
                            tools.add(clear);

                            JMenuItem lines = new JMenuItem(Main.INSTANCE.getJByteMod().getLanguageRes().getResource("remove_lines"));
                            lines.addActionListener(new ActionListener() {
                                public void actionPerformed(ActionEvent e) {
                                    if (JOptionPane.showConfirmDialog(Main.INSTANCE.getJByteMod(), Main.INSTANCE.getJByteMod().getLanguageRes().getResource("confirm_lines"), Main.INSTANCE.getJByteMod().getLanguageRes().getResource("confirm"),
                                            JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) {
                                        MethodUtils.removeLines(mn);
                                        jbm.selectMethod(cn, mn);
                                    }
                                }
                            });
                            tools.add(lines);
                            JMenuItem deadcode = new JMenuItem(Main.INSTANCE.getJByteMod().getLanguageRes().getResource("remove_dead_code"));
                            deadcode.addActionListener(new ActionListener() {
                                public void actionPerformed(ActionEvent e) {
                                    if (JOptionPane.showConfirmDialog(Main.INSTANCE.getJByteMod(), Main.INSTANCE.getJByteMod().getLanguageRes().getResource("confirm_dead_code"),
                                            Main.INSTANCE.getJByteMod().getLanguageRes().getResource("confirm"), JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) {
                                        MethodUtils.removeDeadCode(cn, mn);
                                        jbm.selectMethod(cn, mn);
                                    }
                                }
                            });
                            tools.add(deadcode);
                            menu.add(tools);
                            menu.show(ClassTree.this, me.getX(), me.getY());
                        } else if (cn != null) {
                            //class selected
                            JPopupMenu menu = new JPopupMenu();
                            JMenuItem insert = new JMenuItem(Main.INSTANCE.getJByteMod().getLanguageRes().getResource("add_method"));
                            insert.addActionListener(new ActionListener() {
                                public void actionPerformed(ActionEvent e) {
                                    MethodNode mn = new MethodNode(1, "", "()V", null, null);
                                    mn.maxLocals = 1;
                                    if (new InsnEditDialogue(mn, mn).open()) {
                                        if (mn.name.isEmpty() || mn.desc.isEmpty()) {
                                            ErrorDisplay.error("Method name / desc cannot be empty");
                                            return;
                                        }
                                        cn.methods.add(mn);
                                        model.insertNodeInto(new SortedTreeNode(cn, mn), stn, 0);
                                    }
                                }
                            });
                            menu.add(insert);

                            JMenuItem edit = new JMenuItem(Main.INSTANCE.getJByteMod().getLanguageRes().getResource("edit"));
                            edit.addActionListener(new ActionListener() {
                                public void actionPerformed(ActionEvent e) {
                                    if (new InsnEditDialogue(mn, cn).open()) {
                                        jbm.refreshTree();
                                    }
                                }
                            });
                            menu.add(edit);
                            JMenu tools = new JMenu(Main.INSTANCE.getJByteMod().getLanguageRes().getResource("tools"));
                            JMenuItem frames = new JMenuItem(Main.INSTANCE.getJByteMod().getLanguageRes().getResource("generate_frames"));
                            frames.addActionListener(new ActionListener() {
                                public void actionPerformed(ActionEvent e) {
                                    FrameGen.regenerateFrames(jbm, cn);
                                }
                            });
                            JMenuItem remove = new JMenuItem(Main.INSTANCE.getJByteMod().getLanguageRes().getResource("remove"));
                            remove.addActionListener(new ActionListener() {
                                public void actionPerformed(ActionEvent e) {
                                    if (JOptionPane.showConfirmDialog(Main.INSTANCE.getJByteMod(), Main.INSTANCE.getJByteMod().getLanguageRes().getResource("confirm_remove"),
                                            Main.INSTANCE.getJByteMod().getLanguageRes().getResource("confirm"), JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) {
                                        jbm.getJarArchive().getClasses().remove(cn.name);
                                        TreeNode parent = stn.getParent();
                                        model.removeNodeFromParent(stn);
                                        while (parent != null && !parent.children().hasMoreElements()
                                                && parent != model.getRoot()
                                                && !((SortedTreeNode) parent).isStructural()) {
                                            TreeNode par = parent.getParent();
                                            model.removeNodeFromParent((MutableTreeNode) parent);
                                            parent = par;
                                        }
                                    }
                                }
                            });
                            menu.add(remove);
                            tools.add(frames);
                            menu.add(tools);
                            menu.show(ClassTree.this, me.getX(), me.getY());
                        } else {
                            if (stn.isStructural() || stn.isResourceNode()) {
                                return;
                            }
                            JPopupMenu menu = new JPopupMenu();
                            JMenuItem remove = new JMenuItem(Main.INSTANCE.getJByteMod().getLanguageRes().getResource("remove"));
                            remove.addActionListener(new ActionListener() {
                                public void actionPerformed(ActionEvent e) {
                                    if (JOptionPane.showConfirmDialog(Main.INSTANCE.getJByteMod(), Main.INSTANCE.getJByteMod().getLanguageRes().getResource("confirm_remove"),
                                            Main.INSTANCE.getJByteMod().getLanguageRes().getResource("confirm"), JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) {
                                        TreeNode parent = stn.getParent();
                                        deleteItselfAndChilds(stn);
                                        while (parent != null && !parent.children().hasMoreElements()
                                                && parent != model.getRoot()
                                                && !((SortedTreeNode) parent).isStructural()) {
                                            TreeNode par = parent.getParent();
                                            model.removeNodeFromParent((MutableTreeNode) parent);
                                            parent = par;
                                        }
                                    }
                                }
                            });
                            menu.add(remove);
                            JMenuItem add = new JMenuItem(Main.INSTANCE.getJByteMod().getLanguageRes().getResource("add"));
                            add.addActionListener(new ActionListener() {
                                public void actionPerformed(ActionEvent e) {
                                    ClassNode cn = new ClassNode();
                                    cn.version = 52;
                                    cn.name = getPath(stn);
                                    cn.superName = "java/lang/Object";
                                    if (new InsnEditDialogue(mn, cn).open()) {
                                        jbm.getJarArchive().getClasses().put(cn.name, cn);
                                        jbm.refreshTree();
                                    }
                                }
                            });
                            menu.add(add);
                            menu.show(ClassTree.this, me.getX(), me.getY());
                        }
                    }
                } else {
                    TreePath tp = ClassTree.this.getPathForLocation(me.getX(), me.getY());
                    if (tp != null && tp.getParentPath() != null) {
                        ClassTree.this.setSelectionPath(tp);
                        if (ClassTree.this.getLastSelectedPathComponent() == null) {
                            return;
                        }
                        SortedTreeNode stn = (SortedTreeNode) ClassTree.this.getLastSelectedPathComponent();
                        if (stn.getMethodNode() == null && stn.getClassNode() == null) {
                            if (ClassTree.this.isExpanded(tp)) {
                                ClassTree.this.collapsePath(tp);
                            } else {
                                ClassTree.this.expandPath(tp);
                            }
                        }
                    }
                }
            }
        });
    }

    private String getPath(SortedTreeNode stn) {
        String path = "";
        while (stn != null && stn != model.getRoot()) {
            if (stn.getNodeKind() == SortedTreeNode.NodeKind.PACKAGE) {
                path = stn + "/" + path;
            }
            stn = (SortedTreeNode) stn.getParent();
        }
        return path;
    }

    private void sort(DefaultTreeModel model, SortedTreeNode node, boolean sm) {
        if (!node.isLeaf() && (sm ? true : (!node.toString().endsWith(".class")))) {
            node.sort();
            for (int i = 0; i < model.getChildCount(node); i++) {
                SortedTreeNode child = ((SortedTreeNode) model.getChild(node, i));
                sort(model, child, sm);
            }
        }
    }

    public void refreshMethod(ClassNode cn, MethodNode mn) {
        changedChilds((TreeNode) model.getRoot());
    }

    public void changedChilds(TreeNode node) {
        model.nodeChanged(node);
        if (node.getChildCount() >= 0) {
            for (Enumeration<?> e = node.children(); e.hasMoreElements(); ) {
                TreeNode n = (TreeNode) e.nextElement();
                changedChilds(n);
            }
        }
    }

    public void deleteItselfAndChilds(SortedTreeNode node) {
        if (node.getChildCount() >= 0) {
            for (Enumeration<?> e = node.children(); e.hasMoreElements(); ) {
                TreeNode n = (TreeNode) e.nextElement();
                deleteItselfAndChilds((SortedTreeNode) n);
            }
        }
        if (node.getClassNode() != null)
            jbm.getJarArchive().getClasses().remove(node.getClassNode().name);
        model.removeNodeFromParent(node);
    }

    public void collapseAll() {
        expandedNodes.clear();
        Main.INSTANCE.getJByteMod().refreshTree();
    }
}
