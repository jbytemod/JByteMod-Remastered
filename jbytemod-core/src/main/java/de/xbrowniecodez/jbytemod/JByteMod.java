package de.xbrowniecodez.jbytemod;

import com.sun.tools.attach.VirtualMachine;
import com.sun.tools.attach.VirtualMachineDescriptor;
import de.xbrowniecodez.jbytemod.utils.BytecodeUtils;
import de.xbrowniecodez.jbytemod.utils.Utils;
import de.xbrowniecodez.jbytemod.utils.update.objects.Version;
import lombok.Getter;
import lombok.Setter;
import de.xbrowniecodez.jbytemod.plugin.PluginManager;
import me.grax.jbytemod.JarArchive;
import me.grax.jbytemod.decompiler.Decompiler;
import me.grax.jbytemod.res.LanguageRes;
import me.grax.jbytemod.res.Options;
import me.grax.jbytemod.ui.*;
import de.xbrowniecodez.jbytemod.ui.MyMenuBar;
import de.xbrowniecodez.jbytemod.ui.MyToolBar;
import de.xbrowniecodez.jbytemod.ui.MyTabbedPane;
import de.xbrowniecodez.jbytemod.ui.ClassTree;
import de.xbrowniecodez.jbytemod.ui.lists.LVPList;
import me.grax.jbytemod.ui.graph.ControlFlowPanel;
import me.grax.jbytemod.ui.lists.MyCodeList;
import de.xbrowniecodez.jbytemod.ui.lists.SearchList;
import de.xbrowniecodez.jbytemod.ui.lists.TCBList;
import de.xbrowniecodez.jbytemod.ui.tree.SortedTreeNode;
import me.grax.jbytemod.utils.ErrorDisplay;
import de.xbrowniecodez.jbytemod.utils.gui.LookUtils;
import de.xbrowniecodez.jbytemod.utils.attach.RemoteJarArchive;
import de.xbrowniecodez.jbytemod.archive.AabArchive;
import de.xbrowniecodez.jbytemod.archive.ApkArchive;
import de.xbrowniecodez.jbytemod.utils.apk.ApkSigningConfig;
import de.xbrowniecodez.jbytemod.utils.task.AttachTask;
import de.xbrowniecodez.jbytemod.utils.task.LoadTask;
import de.xbrowniecodez.jbytemod.utils.task.RetransformTask;
import me.grax.jbytemod.utils.task.SaveTask;
import de.xbrowniecodez.jbytemod.utils.OpcodeUtils;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.lang.instrument.Instrumentation;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;

@Getter
@Setter
public class JByteMod extends JFrame {
    private final boolean agent;
    private final Version version = new Version(Utils.readPropertiesFile().getProperty("version"));
    private final String title = "JByteMod Remastered v" + version;
    private LanguageRes languageRes;
    private String lastEditFile = "";
    private HashMap<ClassNode, MethodNode> lastSelectedTreeEntries = new LinkedHashMap<>();
    private JarArchive jarArchive;
    private Instrumentation agentInstrumentation;
    private Options options;
    private ClassTree jarTree;
    private MyCodeList codeList;
    private PageEndPanel pageEndPanel;
    private SearchList searchList;
    private DecompilerPanel decompilerPanel;
    private ControlFlowPanel controlFlowPanel;
    private TCBList tcbList;
    private MyTabbedPane tabbedPane;
    private InfoPanel infoPanel;
    private LVPList lvpList;
    private MyMenuBar myMenuBar;
    private MyToolBar toolBar;
    private ClassNode currentNode;
    private MethodNode currentMethod;
    private PluginManager pluginManager;
    private File filePath;

    public JByteMod(boolean agent) throws Exception {
        this.agent = agent;
        this.options = new Options();
        this.languageRes = new LanguageRes();
    }

    public static void agentmain(String agentArgs, Instrumentation instrumentation) throws Exception {
        if (!instrumentation.isRedefineClassesSupported()) {
            throw new IllegalStateException("Class redefinition is disabled in the target JVM");
        }
        Main.INSTANCE.startAgent(instrumentation);
    }

    public void initializeFrame(boolean agent) {
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent we) {
                handleWindowClosing(agent);
            }
        });

        setBounds(100, 100, 1280, 720);
        setTitle(title);
        setJMenuBar(myMenuBar = new MyMenuBar(this, agent));
        jarTree = new ClassTree(this);
        JPanel contentPane = new JPanel();
        contentPane.setBorder(new EmptyBorder(5, 5, 5, 5));
        contentPane.setLayout(new BorderLayout(5, 5));
        setContentPane(contentPane);
        setTcbList(new TCBList());
        setLvpList(new LVPList());
        createSplitPane(contentPane);
        contentPane.add(pageEndPanel = new PageEndPanel(), BorderLayout.PAGE_END);
        contentPane.add(toolBar = new MyToolBar(this), BorderLayout.PAGE_START);

        if (jarArchive != null) {
            refreshTree();
        }
    }

    private void createSplitPane(Container container) {
        JPanel borderPanel = new JPanel();
        borderPanel.setBorder(null);
        borderPanel.setLayout(new GridLayout());
        JSplitPane splitPane = new MySplitPane(this, jarTree);
        JPanel b2 = new JPanel();
        b2.setBorder(new EmptyBorder(5, 0, 5, 0));
        b2.setLayout(new GridLayout());
        b2.add(splitPane);
        borderPanel.add(b2);
        container.add(borderPanel, BorderLayout.CENTER);
    }

    private void handleWindowClosing(boolean agent) {
        if (JOptionPane.showConfirmDialog(JByteMod.this, languageRes.getResource("exit_warn"), languageRes.getResource("is_sure"),
                JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) {
            if (pluginManager != null) {
                pluginManager.close();
            }
            if (agent) {
                dispose();
            } else {
                Runtime.getRuntime().exit(0);
            }
        }
    }

    public void applyChangesAgent() {
        if (jarArchive instanceof RemoteJarArchive) {
            new RetransformTask(this, null, jarArchive).execute();
            return;
        }
        if (agentInstrumentation == null) {
            throw new RuntimeException();
        }
        new RetransformTask(this, agentInstrumentation, jarArchive).execute();
    }

    public void attachTo(VirtualMachine vm) throws Exception {
        new AttachTask(this, vm).execute();
    }

    public void connectToAgent(RemoteJarArchive archive) {
        if (jarArchive instanceof RemoteJarArchive previousArchive && previousArchive != archive) {
            try {
                previousArchive.close();
            } catch (Exception ignored) {
            }
        }

        clearResourceEditor();
        jarArchive = archive;
        filePath = null;
        lastEditFile = "attached process";
        lastSelectedTreeEntries.clear();
        Decompiler.clearCache();

        setJMenuBar(myMenuBar = new MyMenuBar(this, true));
        if (pluginManager != null) myMenuBar.addPluginMenu(pluginManager.getPlugins());
        Container contentPane = getContentPane();
        if (toolBar != null) contentPane.remove(toolBar);
        contentPane.add(toolBar = new MyToolBar(this), BorderLayout.PAGE_START);

        setProcessTitle(archive.getProcessId());
        refreshTree();
        notifyPlugins();
        revalidate();
        repaint();
    }

    /**
     * Load .jar, .class, .apk, or .aab file
     */
    public void loadFile(File input) {
        try {
            loadFileChecked(input);
        } catch (Throwable e) {
            new ErrorDisplay(e);
        }
    }

    public LoadTask loadFileChecked(File input) throws Exception {
        this.filePath = input;
        lastSelectedTreeEntries.clear();
        Decompiler.clearCache();
        String ap = input.getAbsolutePath().toLowerCase(Locale.ROOT);

        LoadTask task = null;
        if (ap.endsWith(".jar") || ap.endsWith(".apk") || ap.endsWith(".aab")) {
            task = loadZipFile(input);
        } else if (ap.endsWith(".class")) {
            loadClassFile(input);
            notifyPlugins();
        } else {
            throw new UnsupportedOperationException(languageRes.getResource("jar_warn"));
        }
        return task;
    }

    private LoadTask loadZipFile(File input) throws Exception {
        String name = input.getName().toLowerCase(Locale.ROOT);
        JarArchive archive = name.endsWith(".apk")
                ? new ApkArchive(new HashMap<>(), new HashMap<>())
                : name.endsWith(".aab")
                        ? new AabArchive(new HashMap<>(), new HashMap<>())
                        : new JarArchive(new HashMap<>(), new HashMap<>());
        LoadTask task = new LoadTask(this, input, archive);
        replaceArchive(archive);
        task.execute();
        setTitleSuffix(input.getName());
        return task;
    }

    private void loadClassFile(File input) throws Exception {
        JarArchive archive = new JarArchive(BytecodeUtils.getClassNodeFromBytes(Files.readAllBytes(input.toPath())));
        replaceArchive(archive);
        setTitleSuffix(input.getName());
        refreshTree();
    }

    private void replaceArchive(JarArchive archive) {
        if (jarArchive instanceof RemoteJarArchive remoteArchive) {
            try {
                remoteArchive.close();
            } catch (Exception ignored) {
            }
        }
        clearResourceEditor();
        jarArchive = archive;
    }

    private void notifyPlugins() {
        if (pluginManager == null || jarArchive == null || jarArchive.getClasses() == null) {
            return;
        }
        pluginManager.fileLoaded(jarArchive.getClasses());
    }

    public void refreshAgentClasses() {
        if (jarArchive instanceof RemoteJarArchive remoteArchive) {
            pageEndPanel.setValue(0);
            new SwingWorker<Void, Void>() {
                @Override
                protected Void doInBackground() throws Exception {
                    remoteArchive.refresh(progress -> pageEndPanel.setValue(Math.min(progress, 99)));
                    return null;
                }

                @Override
                protected void done() {
                    try {
                        get();
                        Decompiler.clearCache();
                        refreshTree();
                        pageEndPanel.setValue(100);
                    } catch (Exception exception) {
                        Throwable cause = exception.getCause() == null ? exception : exception.getCause();
                        new ErrorDisplay(cause);
                    }
                }
            }.execute();
            return;
        }
        if (agentInstrumentation == null) {
            throw new RuntimeException();
        }
        this.refreshTree();
    }

    public void terminateAttachedJvm() {
        if (!(jarArchive instanceof RemoteJarArchive remoteArchive)) {
            return;
        }
        int choice = JOptionPane.showConfirmDialog(this,
                "Terminate the connected JVM? This will immediately stop the target application.",
                "Terminate JVM",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE);
        if (choice != JOptionPane.YES_OPTION) {
            return;
        }

        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws Exception {
                remoteArchive.terminate();
                return null;
            }

            @Override
            protected void done() {
                try {
                    get();
                    completeAttachedJvmTermination(remoteArchive);
                } catch (Exception exception) {
                    Throwable cause = exception.getCause() == null ? exception : exception.getCause();
                    new ErrorDisplay(cause);
                }
            }
        }.execute();
    }

    public void detachAttachedJvm() {
        if (!(jarArchive instanceof RemoteJarArchive remoteArchive)) {
            return;
        }

        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws Exception {
                remoteArchive.close();
                return null;
            }

            @Override
            protected void done() {
                try {
                    get();
                    completeAttachedJvmDetachment(remoteArchive);
                } catch (Exception exception) {
                    Throwable cause = exception.getCause() == null ? exception : exception.getCause();
                    new ErrorDisplay(cause);
                }
            }
        }.execute();
    }

    public void completeAttachedJvmTermination(RemoteJarArchive remoteArchive) {
        completeAttachedJvmSession(remoteArchive, "terminated process snapshot", "Target terminated");
    }

    public void completeAttachedJvmDetachment(RemoteJarArchive remoteArchive) {
        completeAttachedJvmSession(remoteArchive, "detached process snapshot", "Detached snapshot");
    }

    private void completeAttachedJvmSession(RemoteJarArchive remoteArchive, String editFile, String titleSuffix) {
        if (jarArchive != remoteArchive) {
            return;
        }
        JarArchive snapshot = new JarArchive(remoteArchive.getClasses(), new HashMap<>());
        snapshot.setJarManifest(remoteArchive.getJarManifest());
        clearResourceEditor();
        jarArchive = snapshot;
        lastEditFile = editFile;
        setJMenuBar(myMenuBar = new MyMenuBar(this, false));
        if (pluginManager != null) myMenuBar.addPluginMenu(pluginManager.getPlugins());
        Container contentPane = getContentPane();
        contentPane.remove(toolBar);
        contentPane.add(toolBar = new MyToolBar(this), BorderLayout.PAGE_START);
        setTitleSuffix(titleSuffix);
        notifyPlugins();
        revalidate();
        repaint();
    }

    public void reloadPlugins() {
        if (pluginManager != null) {
            pluginManager.close();
        }
        pluginManager = new PluginManager(this);
        myMenuBar.addPluginMenu(pluginManager.getPlugins());
        notifyPlugins();
    }

    public void refreshTree() {
        Main.INSTANCE.getLogger().log("Building tree..");
        this.jarTree.refreshTree(jarArchive);
    }

    public void saveFile(File output) {
        saveFile(output, ApkSigningConfig.debugKey());
    }

    public void saveFile(File output, ApkSigningConfig apkSigningConfig) {
        try {
            saveFileChecked(output, apkSigningConfig);
        } catch (Throwable t) {
            apkSigningConfig.close();
            new ErrorDisplay(t);
        }
    }

    public SaveTask saveFileChecked(File output) {
        return saveFileChecked(output, ApkSigningConfig.debugKey());
    }

    public SaveTask saveFileChecked(File output, ApkSigningConfig apkSigningConfig) {
        if (jarArchive == null || jarArchive.getClasses() == null) {
            apkSigningConfig.close();
            throw new IllegalStateException("No archive is open");
        }
        SaveTask task = new SaveTask(this, output, jarArchive, apkSigningConfig);
        task.execute();
        return task;
    }

    public void selectClass(ClassNode cn) {
        if (Main.INSTANCE.getJByteMod().getOptions().get("select_code_tab").getBoolean()) {
            tabbedPane.setSelectedIndex(0);
        }
        this.currentNode = cn;
        this.currentMethod = null;
        Decompiler.clearCache();
        decompilerPanel.setDecompilerText("");
        controlFlowPanel.setMethodNode(null);
        controlFlowPanel.clear();
        infoPanel.selectClass(cn);
        codeList.loadFields(cn);
        tabbedPane.selectClass(cn);
        if (pluginManager != null) {
            pluginManager.classSelected(cn);
        }
        lastSelectedTreeEntries.put(cn, null);
        if (lastSelectedTreeEntries.size() > 5) {
            lastSelectedTreeEntries.remove(lastSelectedTreeEntries.keySet().iterator().next());
        }
    }

    public void selectResource(String path) {
        currentNode = null;
        currentMethod = null;
        tabbedPane.selectResource(path);
    }

    private void clearResourceEditor() {
        if (tabbedPane != null) {
            tabbedPane.clearResourceEditor();
        }
    }

    private boolean selectEntry(MethodNode mn, DefaultTreeModel tm, SortedTreeNode node) {
        for (int i = 0; i < tm.getChildCount(node); i++) {
            SortedTreeNode child = (SortedTreeNode) tm.getChild(node, i);
            if (child.getMethodNode() != null && child.getMethodNode().equals(mn)) {
                TreePath tp = new TreePath(tm.getPathToRoot(child));
                jarTree.setSelectionPath(tp);
                jarTree.scrollPathToVisible(tp);
                return true;
            }
            if (!child.isLeaf()) {
                if (selectEntry(mn, tm, child)) {
                    return true;
                }
            }
        }
        return false;
    }

    public void selectMethod(ClassNode cn, MethodNode mn) {
        if (Main.INSTANCE.getJByteMod().getOptions().get("select_code_tab").getBoolean()) {
            tabbedPane.setSelectedIndex(0);
        }
        OpcodeUtils.clearLabelCache();
        this.currentNode = cn;
        this.currentMethod = mn;
        Decompiler.clearCache();
        infoPanel.selectMethod(cn, mn);
        if (!codeList.loadInstructions(mn)) {
            codeList.setSelectedIndex(-1);
        }
        tcbList.addNodes(cn, mn);
        lvpList.addNodes(cn, mn);
        controlFlowPanel.setMethodNode(mn);
        decompilerPanel.setDecompilerText("");
        tabbedPane.selectMethod(cn, mn);
        if (pluginManager != null) {
            pluginManager.methodSelected(cn, mn);
        }
        lastSelectedTreeEntries.put(cn, mn);
        if (lastSelectedTreeEntries.size() > 5) {
            lastSelectedTreeEntries.remove(lastSelectedTreeEntries.keySet().iterator().next());
        }
    }

    private void setTitleSuffix(String suffix) {
        this.setTitle(title + " - " + suffix);
    }

    private void setProcessTitle(long processId) {
        setTitleSuffix("PID " + processId + " - " + processDisplayName(processId));
    }

    private static String processDisplayName(long processId) {
        try {
            String pid = Long.toString(processId);
            for (VirtualMachineDescriptor descriptor : VirtualMachine.list()) {
                if (descriptor.id().equals(pid) && !descriptor.displayName().isBlank()) {
                    return descriptor.displayName();
                }
            }
        } catch (Throwable ignored) {
        }
        try {
            return ProcessHandle.of(processId)
                    .flatMap(process -> process.info().command())
                    .orElse("Unknown JVM");
        } catch (Throwable ignored) {
            return "Unknown JVM";
        }
    }

    @Override
    public void setVisible(boolean b) {
        if (!agent) {
            LookUtils.setTheme();
        }
        this.initializeFrame(agent);
        if (agent) {
            setProcessTitle(ProcessHandle.current().pid());
            LookUtils.applyAgentTheme(this, () -> super.setVisible(b));
            return;
        } else {
            this.setPluginManager(new PluginManager(this));
            this.myMenuBar.addPluginMenu(pluginManager.getPlugins());
            notifyPlugins();
        }
        super.setVisible(b);
    }

    public void treeSelection(ClassNode cn, MethodNode mn) {
        //selection may take some time
        new Thread(() -> {
            DefaultTreeModel tm = (DefaultTreeModel) jarTree.getModel();
            if (this.selectEntry(mn, tm, (SortedTreeNode) tm.getRoot())) {
                jarTree.repaint();
            }
        }).start();
    }

    public void treeSelection(ClassNode cn, FieldNode fn) {
        treeSelection(cn);
    }

    public void treeSelection(ClassNode cn) {
        new Thread(() -> {
            DefaultTreeModel tm = (DefaultTreeModel) jarTree.getModel();
            if (this.selectClassEntry(cn, tm, (SortedTreeNode) tm.getRoot())) {
                jarTree.repaint();
            }
        }).start();
    }

    private boolean selectClassEntry(ClassNode cn, DefaultTreeModel tm, SortedTreeNode node) {
        for (int i = 0; i < tm.getChildCount(node); i++) {
            SortedTreeNode child = (SortedTreeNode) tm.getChild(node, i);
            if (child.getClassNode() != null && child.getClassNode().name.equals(cn.name) && child.getMethodNode() == null) {
                TreePath tp = new TreePath(tm.getPathToRoot(child));
                jarTree.setSelectionPath(tp);
                jarTree.scrollPathToVisible(tp);
                return true;
            }
            if (!child.isLeaf()) {
                if (selectClassEntry(cn, tm, child)) {
                    return true;
                }
            }
        }
        return false;
    }

    public void selectField(ClassNode cn, FieldNode fn) {
        selectClass(cn);
        codeList.selectField(fn);
    }
}
