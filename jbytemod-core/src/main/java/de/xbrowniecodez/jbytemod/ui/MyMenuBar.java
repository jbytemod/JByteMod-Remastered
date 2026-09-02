package de.xbrowniecodez.jbytemod.ui;

import android.util.Patterns;
import com.sun.tools.attach.VirtualMachine;
import com.sun.tools.attach.VirtualMachineDescriptor;
import de.xbrowniecodez.jbytemod.Main;
import de.xbrowniecodez.jbytemod.JByteMod;
import de.xbrowniecodez.jbytemod.archive.AabArchive;
import de.xbrowniecodez.jbytemod.archive.AndroidArchive;
import de.xbrowniecodez.jbytemod.plugin.Plugin;
import de.xbrowniecodez.jbytemod.ui.dialogue.ApkSigningDialog;
import de.xbrowniecodez.jbytemod.utils.apk.ApkSigningConfig;
import me.grax.jbytemod.res.LanguageRes;
import me.grax.jbytemod.res.Option;
import me.grax.jbytemod.res.Options;
import me.grax.jbytemod.ui.*;
import de.xbrowniecodez.jbytemod.ui.dialogue.ClassDialogue;
import de.xbrowniecodez.jbytemod.ui.lists.entries.SearchEntry;
import me.grax.jbytemod.utils.DeobfusacteUtils;
import me.grax.jbytemod.utils.ErrorDisplay;
import me.grax.jbytemod.utils.TextUtils;
import de.xbrowniecodez.jbytemod.utils.attach.AttachUtils;
import de.xbrowniecodez.jbytemod.utils.gui.LookUtils;
import me.grax.jbytemod.utils.list.LazyListModel;
import org.apache.commons.io.IOUtils;
import org.objectweb.asm.tree.*;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.List;

public class MyMenuBar extends JMenuBar {
    private JMenu pluginMenu;

    private static final Icon searchIcon = new ImageIcon(MyMenuBar.class.getResource("/resources/search.png"));
    private JByteMod jbm;
    private File lastFile;
    private boolean agent;

    public MyMenuBar(JByteMod jam, boolean agent) {
        this.jbm = jam;
        this.agent = agent;
        this.initFileMenu();
    }



    private void initFileMenu() {
        JMenu file = new JMenu(Main.INSTANCE.getJByteMod().getLanguageRes().getResource("file"));
        if (!agent) {
            JMenuItem save = new JMenuItem(Main.INSTANCE.getJByteMod().getLanguageRes().getResource("save"));
            JMenuItem saveas = new JMenuItem(Main.INSTANCE.getJByteMod().getLanguageRes().getResource("save_as"));
            JMenuItem load = new JMenuItem(Main.INSTANCE.getJByteMod().getLanguageRes().getResource("load"));
            load.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    openLoadDialogue();
                }
            });
            save.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    if (lastFile != null) {
                        jbm.saveFile(lastFile);
                    } else {
                        openSaveDialogue();
                    }
                }
            });
            saveas.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    openSaveDialogue();
                }
            });
            save.setAccelerator(KeyStroke.getKeyStroke('S', Toolkit.getDefaultToolkit().getMenuShortcutKeyMask()));
            load.setAccelerator(KeyStroke.getKeyStroke('N', Toolkit.getDefaultToolkit().getMenuShortcutKeyMask()));
            file.add(save);
            file.add(saveas);
            file.add(load);
        } else {
            JMenuItem refresh = new JMenuItem(Main.INSTANCE.getJByteMod().getLanguageRes().getResource("refresh"));
            refresh.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    jbm.refreshAgentClasses();
                }
            });
            file.add(refresh);
            JMenuItem apply = new JMenuItem(Main.INSTANCE.getJByteMod().getLanguageRes().getResource("apply"));
            apply.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    jbm.applyChangesAgent();
                }
            });
            file.add(apply);
        }
        this.add(file);

        JMenu search = new JMenu(Main.INSTANCE.getJByteMod().getLanguageRes().getResource("search"));
        JMenuItem ldc = new JMenuItem(Main.INSTANCE.getJByteMod().getLanguageRes().getResource("search_ldc"));
        ldc.addActionListener(new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent e) {
                searchLDC();
            }
        });

        search.add(ldc);
        JMenuItem fieldValue = new JMenuItem(Main.INSTANCE.getJByteMod().getLanguageRes().getResource("search_field_value"));
        fieldValue.addActionListener(new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent e) {
                searchFieldValue();
            }
        });

        search.add(fieldValue);
        JMenuItem field = new JMenuItem(Main.INSTANCE.getJByteMod().getLanguageRes().getResource("search_field"));
        field.addActionListener(new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent e) {
                searchField();
            }
        });

        search.add(field);
        JMenuItem method = new JMenuItem(Main.INSTANCE.getJByteMod().getLanguageRes().getResource("search_method"));
        method.addActionListener(new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent e) {
                searchMethod();
            }
        });

        search.add(method);
        JMenuItem replace = new JMenuItem(Main.INSTANCE.getJByteMod().getLanguageRes().getResource("replace_ldc"));
        replace.addActionListener(new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent e) {
                replaceLDC();
            }
        });

        search.add(replace);
        JMenuItem replaceFieldValue = new JMenuItem(Main.INSTANCE.getJByteMod().getLanguageRes().getResource("replace_field_value"));
        replaceFieldValue.addActionListener(new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent e) {
                replaceFieldValue();
            }
        });

        search.add(replaceFieldValue);
        this.add(search);
        JMenu utils = new JMenu(Main.INSTANCE.getJByteMod().getLanguageRes().getResource("utils"));
        JMenuItem accman = new JMenuItem("Access Helper");
        accman.addActionListener(new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent e) {
                new JAccessHelper().setVisible(true);
            }
        });
        utils.add(accman);
        JMenuItem attach = new JMenuItem(Main.INSTANCE.getJByteMod().getLanguageRes().getResource("attach"));
        attach.addActionListener(new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent e) {
                openProcessSelection();
            }
        });
        utils.add(attach);
        JMenu obf = new JMenu("Obfuscation Analysis");
        utils.add(obf);
        JMenuItem nameobf = new JMenuItem("Name Obfuscation");
        nameobf.addActionListener(new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent e) {
                if (jbm.getJarArchive() != null)
                    new JNameObfAnalysis(jbm.getJarArchive().getClasses()).setVisible(true);
            }
        });
        obf.add(nameobf);
        JMenuItem methodobf = new JMenuItem("Method Obfuscation");
        methodobf.addActionListener(new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent e) {
                if (jbm.getJarArchive() != null)
                    new JMethodObfAnalysis(jbm.getJarArchive().getClasses()).setVisible(true);
            }
        });
        obf.add(methodobf);
        this.add(utils);
        JMenu tree = new JMenu("Tree");
        utils.add(tree);
        JMenuItem rltree = new JMenuItem(Main.INSTANCE.getJByteMod().getLanguageRes().getResource("tree_reload"));
        rltree.addActionListener(new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent e) {
                jbm.getJarTree().refreshTree(jbm.getJarArchive());
            }
        });
        tree.add(rltree);
        JMenuItem collapse = new JMenuItem(Main.INSTANCE.getJByteMod().getLanguageRes().getResource("collapse_all"));
        collapse.addActionListener(new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent e) {
                jbm.getJarTree().collapseAll();
            }
        });
        tree.add(collapse);
        JMenu searchUtils = new JMenu(Main.INSTANCE.getJByteMod().getLanguageRes().getResource("search"));
        utils.add(searchUtils);
        JMenuItem url = new JMenuItem(Main.INSTANCE.getJByteMod().getLanguageRes().getResource("url_search"));
        url.addActionListener(new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent e) {
                jbm.getSearchList().searchForPatternRegex(Patterns.AUTOLINK_WEB_URL);
            }
        });
        searchUtils.add(url);
        JMenuItem email = new JMenuItem(Main.INSTANCE.getJByteMod().getLanguageRes().getResource("email_search"));
        email.addActionListener(new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent e) {
                jbm.getSearchList().searchForPatternRegex(Patterns.EMAIL_ADDRESS);
            }
        });
        searchUtils.add(email);
        // Utils:
        JMenu deobfTools = new JMenu(Main.INSTANCE.getJByteMod().getLanguageRes().getResource("deobf_tools"));
        utils.add(deobfTools);

        JMenuItem findSF = new JMenuItem(Main.INSTANCE.getJByteMod().getLanguageRes().getResource("find_sourcefiles"));
        findSF.addActionListener(new ActionListener() {

            @Override
            public void actionPerformed(final ActionEvent e) {
                if (jbm.getJarArchive() == null) {
                    return;
                }
                final JPanel panel = new JPanel(new BorderLayout(5, 5));
                final JPanel input = new JPanel(new GridLayout(0, 1));
                final JPanel labels = new JPanel(new GridLayout(0, 1));
                panel.add(labels, "West");
                panel.add(input, "Center");
                // panel.add(new JLabel(Main.INSTANCE.getJByteMod().getLanguageRes().getResource("big_jar_warn")), "South");
                labels.add(new JLabel(Main.INSTANCE.getJByteMod().getLanguageRes().getResource("find_sourcefiles_input_name")));
                final JTextField sf = new JTextField();
                input.add(sf);
                if (JOptionPane.showConfirmDialog(Main.INSTANCE.getJByteMod(), panel, Main.INSTANCE.getJByteMod().getLanguageRes().getResource("find_sourcefiles"),
                        2) == 0 && !sf.getText().isEmpty()) {
                    jbm.getSearchList().searchForSF(sf.getText());
                }
            }
        });
        search.add(findSF);

        JMenuItem findClass = new JMenuItem(Main.INSTANCE.getJByteMod().getLanguageRes().getResource("find_class_by_name"));
        findClass.addActionListener(new ActionListener() {

            @Override
            public void actionPerformed(final ActionEvent e) {
                if (jbm.getJarArchive().getClasses() == null) {
                    return;
                }
                final JPanel panel = new JPanel(new BorderLayout(5, 5));
                final JPanel input = new JPanel(new GridLayout(0, 1));
                final JPanel labels = new JPanel(new GridLayout(0, 1));
                panel.add(labels, "West");
                panel.add(input, "Center");
                // panel.add(new JLabel(Main.INSTANCE.getJByteMod().getLanguageRes().getResource("big_jar_warn")), "South");
                labels.add(new JLabel(Main.INSTANCE.getJByteMod().getLanguageRes().getResource("find_class_input_name")));
                final JTextField cst = new JTextField();
                input.add(cst);
                if (JOptionPane.showConfirmDialog(Main.INSTANCE.getJByteMod(), panel, Main.INSTANCE.getJByteMod().getLanguageRes().getResource("find_class_by_name"),
                        2) == 0 && !cst.getText().isEmpty()) {
                    LazyListModel<SearchEntry> model = new LazyListModel<>();
                    for (final ClassNode cn : jbm.getJarArchive().getClasses().values()) {
                        if (cn.name != null && cn.name.contains(cst.getText())) {
                            // TODO: task
                            SearchEntry se = new SearchEntry(cn, cn.methods.get(0), TextUtils.escape(TextUtils.max(cn.name, 100)));
                            se.setText(TextUtils.toHtml(TextUtils.escape(TextUtils.max(cn.name, 100))));
                            model.addElement(se);
                        }
                    }
                    jbm.getSearchList().setModel(model);
                }
            }
        });
        search.add(findClass);

        JMenuItem clazz_main = new JMenuItem(Main.INSTANCE.getJByteMod().getLanguageRes().getResource("find_main_class"));
        clazz_main.addActionListener(new ActionListener() {

            @Override
            public void actionPerformed(final ActionEvent e) {
                if (jbm.getJarArchive().getClasses() == null) {
                    return;
                }
                LazyListModel<SearchEntry> model = new LazyListModel<>();
                for (final ClassNode c : jbm.getJarArchive().getClasses().values()) {
                    for (final MethodNode m : c.methods) {
                        if (m.name.equals("main") && m.desc.equals("([Ljava/lang/String;)V")) {
                            // TODO: task
                            model.addElement(new SearchEntry(c, m, TextUtils.escape(TextUtils.max(c.name, 100))));
                        }
                    }
                }
                jbm.getSearchList().setModel(model);
            }
        });
        searchUtils.add(clazz_main);

        JMenuItem optimize_peephole = new JMenuItem(Main.INSTANCE.getJByteMod().getLanguageRes().getResource("optimize_peephole"));
        optimize_peephole.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(final ActionEvent e) {
                if (jbm.getJarArchive() != null && jbm.getJarArchive().getClasses() != null) {
                    int count = DeobfusacteUtils.mergeTrapHandler(jbm.getJarArchive().getClasses()) + DeobfusacteUtils.rearrangeGoto(jbm.getJarArchive().getClasses()) + DeobfusacteUtils.foldConstant(jbm.getJarArchive().getClasses()) + DeobfusacteUtils.removeUnconditionalSwitch(jbm.getJarArchive().getClasses());
                    JOptionPane.showMessageDialog(null, "Optimized " + count + " places.",
                            Main.INSTANCE.getJByteMod().getLanguageRes().getResource("optimize_peephole"), JOptionPane.INFORMATION_MESSAGE);
                }else {
                    canNotFindFile();
                }
            }
        });
        deobfTools.add(optimize_peephole);

        JMenuItem show_code = new JMenuItem(Main.INSTANCE.getJByteMod().getLanguageRes().getResource("show_code"));
        show_code.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(final ActionEvent e) {
                if (jbm.getJarArchive() != null && jbm.getJarArchive().getClasses() != null) {
                    DeobfusacteUtils.fixSignature(jbm.getJarArchive().getClasses());
                    DeobfusacteUtils.removeSyntheticBridge(jbm.getJarArchive().getClasses());
                    DeobfusacteUtils.removeLineNumber(jbm.getJarArchive().getClasses());
                    DeobfusacteUtils.removeLocalVariable(jbm.getJarArchive().getClasses());
                    DeobfusacteUtils.removeIllegalVarargs(jbm.getJarArchive().getClasses());
                    JOptionPane.showMessageDialog(null, "Finished showing codes.",
                            Main.INSTANCE.getJByteMod().getLanguageRes().getResource("show_code"), JOptionPane.INFORMATION_MESSAGE);
                }else {
                    canNotFindFile();
                }
            }
        });
        deobfTools.add(show_code);

        JMenuItem signatureFix = new JMenuItem(Main.INSTANCE.getJByteMod().getLanguageRes().getResource("signaturefix"));
        signatureFix.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(final ActionEvent e) {
                if (jbm.getJarArchive() != null && jbm.getJarArchive().getClasses() != null) {
                    DeobfusacteUtils.fixSignature(jbm.getJarArchive().getClasses());
                    JOptionPane.showMessageDialog(null, Main.INSTANCE.getJByteMod().getLanguageRes().getResource("finish_tip"),
                            Main.INSTANCE.getJByteMod().getLanguageRes().getResource("signaturefix"), JOptionPane.INFORMATION_MESSAGE);
                }else {
                    canNotFindFile();
                }
            }
        });
        deobfTools.add(signatureFix);

        JMenuItem access_fix = new JMenuItem("Synthetic Bridge Fixer");
        access_fix.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(final ActionEvent e) {
                if (jbm.getJarArchive() != null && jbm.getJarArchive().getClasses() != null) {
                    DeobfusacteUtils.removeSyntheticBridge(jbm.getJarArchive().getClasses());
                    JOptionPane.showMessageDialog(null, Main.INSTANCE.getJByteMod().getLanguageRes().getResource("finish_tip"),
                            "Synthetic Bridge Fixer", JOptionPane.INFORMATION_MESSAGE);
                }else {
                    canNotFindFile();
                }

            }
        });
        deobfTools.add(access_fix);

        JMenuItem linenumber_remove = new JMenuItem(Main.INSTANCE.getJByteMod().getLanguageRes().getResource("line_number_remove"));
        linenumber_remove.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(final ActionEvent e) {
                if (jbm.getJarArchive() != null && jbm.getJarArchive().getClasses() != null) {
                    DeobfusacteUtils.removeLineNumber(jbm.getJarArchive().getClasses());
                    JOptionPane.showMessageDialog(null, Main.INSTANCE.getJByteMod().getLanguageRes().getResource("finish_tip"),
                            Main.INSTANCE.getJByteMod().getLanguageRes().getResource("line_number_remove"), JOptionPane.INFORMATION_MESSAGE);
                }else {
                    canNotFindFile();
                }
            }
        });
        deobfTools.add(linenumber_remove);

        JMenuItem local_variable_remove = new JMenuItem(Main.INSTANCE.getJByteMod().getLanguageRes().getResource("local_variable_remove"));
        local_variable_remove.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(final ActionEvent e) {
                if (jbm.getJarArchive() != null && jbm.getJarArchive().getClasses() != null) {
                    DeobfusacteUtils.removeLocalVariable(jbm.getJarArchive().getClasses());
                    JOptionPane.showMessageDialog(null, Main.INSTANCE.getJByteMod().getLanguageRes().getResource("finish_tip"),
                            Main.INSTANCE.getJByteMod().getLanguageRes().getResource("local_variable_remove"), JOptionPane.INFORMATION_MESSAGE);
                }else {
                    canNotFindFile();
                }
            }
        });
        deobfTools.add(local_variable_remove);

        JMenuItem illegal_varargs_remove = new JMenuItem(Main.INSTANCE.getJByteMod().getLanguageRes().getResource("illegal_varargs_remove"));
        illegal_varargs_remove.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(final ActionEvent e) {
                if (jbm.getJarArchive() != null && jbm.getJarArchive().getClasses() != null) {
                    DeobfusacteUtils.removeIllegalVarargs(jbm.getJarArchive().getClasses());
                    JOptionPane.showMessageDialog(null, Main.INSTANCE.getJByteMod().getLanguageRes().getResource("finish_tip"),
                            Main.INSTANCE.getJByteMod().getLanguageRes().getResource("illegal_varargs_remove"), JOptionPane.INFORMATION_MESSAGE);
                }else {
                    canNotFindFile();
                }
            }
        });
        deobfTools.add(illegal_varargs_remove);

        JMenuItem illegal_invisible_annotations = new JMenuItem(Main.INSTANCE.getJByteMod().getLanguageRes().getResource("illegal_invisible_annotations"));
        illegal_invisible_annotations.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(final ActionEvent e) {
                if (jbm.getJarArchive() != null && jbm.getJarArchive().getClasses() != null) {
                    DeobfusacteUtils.removeIllegalInvisibleAnnotations(jbm.getJarArchive().getClasses());
                    JOptionPane.showMessageDialog(null, Main.INSTANCE.getJByteMod().getLanguageRes().getResource("finish_tip"),
                            Main.INSTANCE.getJByteMod().getLanguageRes().getResource("illegal_invisible_annotations"), JOptionPane.INFORMATION_MESSAGE);
                }else {
                    canNotFindFile();
                }
            }
        });
        deobfTools.add(illegal_invisible_annotations);

        JMenuItem fold_constant = new JMenuItem(Main.INSTANCE.getJByteMod().getLanguageRes().getResource("fold_constant"));
        fold_constant.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(final ActionEvent e) {
                if (jbm.getJarArchive() != null && jbm.getJarArchive().getClasses() != null) {
                    JOptionPane.showMessageDialog(null, "Folded " + DeobfusacteUtils.foldConstant(jbm.getJarArchive().getClasses()) + " constants.",
                            Main.INSTANCE.getJByteMod().getLanguageRes().getResource("fold_constant"), JOptionPane.INFORMATION_MESSAGE);
                }else {
                    canNotFindFile();
                }
            }
        });
        deobfTools.add(fold_constant);

        JMenuItem rearrange_goto = new JMenuItem(Main.INSTANCE.getJByteMod().getLanguageRes().getResource("rearrange_goto"));
        rearrange_goto.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(final ActionEvent e) {
                if (jbm.getJarArchive() != null && jbm.getJarArchive().getClasses() != null) {
                    JOptionPane.showMessageDialog(null, "Rearranged " + DeobfusacteUtils.rearrangeGoto(jbm.getJarArchive().getClasses()) + " goto blocks.",
                            Main.INSTANCE.getJByteMod().getLanguageRes().getResource("rearrange_goto"), JOptionPane.INFORMATION_MESSAGE);
                }else {
                    canNotFindFile();
                }
            }
        });
        deobfTools.add(rearrange_goto);

        JMenuItem merge_trap_handler = new JMenuItem(Main.INSTANCE.getJByteMod().getLanguageRes().getResource("merge_trap_handler"));
        merge_trap_handler.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(final ActionEvent e) {
                if (jbm.getJarArchive() != null && jbm.getJarArchive().getClasses() != null) {
                    JOptionPane.showMessageDialog(null, "Removed " + DeobfusacteUtils.mergeTrapHandler(jbm.getJarArchive().getClasses()) + " duplicate handlers.",
                            Main.INSTANCE.getJByteMod().getLanguageRes().getResource("merge_trap_handler"), JOptionPane.INFORMATION_MESSAGE);
                }else {
                    canNotFindFile();
                }
            }
        });
        deobfTools.add(merge_trap_handler);

        JMenuItem remove_unconditional_switch = new JMenuItem(Main.INSTANCE.getJByteMod().getLanguageRes().getResource("remove_unconditional_switch"));
        remove_unconditional_switch.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(final ActionEvent e) {
                if (jbm.getJarArchive() != null && jbm.getJarArchive().getClasses() != null) {
                    JOptionPane.showMessageDialog(null, "Removed " + DeobfusacteUtils.removeUnconditionalSwitch(jbm.getJarArchive().getClasses()) + " unconditional switch(es).",
                            Main.INSTANCE.getJByteMod().getLanguageRes().getResource("remove_unconditional_switch"), JOptionPane.INFORMATION_MESSAGE);
                }else {
                    canNotFindFile();
                }
            }
        });
        deobfTools.add(remove_unconditional_switch);

        // From old version of JbyteMod by Grax
        JMenuItem sourceRename = new JMenuItem(Main.INSTANCE.getJByteMod().getLanguageRes().getResource("rename_sourcefiles"));
        sourceRename.addActionListener(new ActionListener() {

            @Override
            public void actionPerformed(final ActionEvent e) {
                if (jbm.getJarArchive().getClasses() == null)
                    return;
                if (JOptionPane.showConfirmDialog(null, Main.INSTANCE.getJByteMod().getLanguageRes().getResource("rename_sourcefiles_warnning"),
                        Main.INSTANCE.getJByteMod().getLanguageRes().getResource("confirm"), 0) == 0) {
                    int i = 0;
                    for (final ClassNode c : jbm.getJarArchive().getClasses().values()) {
                        c.sourceFile = "Class" + i++ + ".java";
                    }
                }
            }
        });
        deobfTools.add(sourceRename);

        this.add(getSettings());
        JMenu help = new JMenu(Main.INSTANCE.getJByteMod().getLanguageRes().getResource("help"));
        JMenuItem about = new JMenuItem(Main.INSTANCE.getJByteMod().getLanguageRes().getResource("about"));
        about.addActionListener(new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent e) {
                try {
                    new AboutDialog(jbm).setVisible(true);
                } catch (Exception ex) {
                    new ErrorDisplay(ex);
                }
            }
        });

        help.add(about);
        JMenuItem licenses = new JMenuItem(Main.INSTANCE.getJByteMod().getLanguageRes().getResource("licenses"));
        licenses.addActionListener(new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent e) {
                try {
                    JDialog dialog = new JDialog(jbm,
                            Main.INSTANCE.getJByteMod().getLanguageRes().getResource("licenses"),
                            Dialog.ModalityType.MODELESS);
                    JTextArea text = new JTextArea(IOUtils.toString(
                            MyMenuBar.class.getResourceAsStream("/resources/LICENSES"), StandardCharsets.UTF_8));
                    text.setEditable(false);
                    text.setCaretPosition(0);
                    dialog.add(new JScrollPane(text));
                    dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
                    dialog.setSize(700, 650);
                    dialog.setLocationRelativeTo(jbm);
                    dialog.setVisible(true);
                } catch (Exception ex) {
                    new ErrorDisplay(ex);
                }
            }
        });

        help.add(licenses);
        int helpItemWidth = Math.max(120,
                Math.max(about.getPreferredSize().width, licenses.getPreferredSize().width) + 24);
        about.setPreferredSize(new Dimension(helpItemWidth, about.getPreferredSize().height));
        licenses.setPreferredSize(new Dimension(helpItemWidth, licenses.getPreferredSize().height));
        this.add(help);
    }

    protected void canNotFindFile() {
        JOptionPane.showMessageDialog(null, "Can't find the target file, are you sure you have loaded the file already?",
                "Warn", JOptionPane.ERROR_MESSAGE);
    }

    protected void openProcessSelection() {
        try {
            List<VirtualMachineDescriptor> list = new ArrayList<>(VirtualMachine.list());
            String currentPid = Long.toString(ProcessHandle.current().pid());
            list.removeIf(descriptor -> descriptor.id().equals(currentPid));
            VirtualMachine vm = null;

            if (list.isEmpty()) {
                String pid = JOptionPane.showInputDialog(Main.INSTANCE.getJByteMod().getLanguageRes().getResource("no_vm_found"));
                if (pid != null && !pid.isEmpty()) {
                    vm = AttachUtils.getVirtualMachine(Integer.parseInt(pid));
                }
            } else {
                JProcessSelection gui = new JProcessSelection(jbm, list);
                gui.setVisible(true);
                if (gui.getPid() != 0) {
                    vm = AttachUtils.getVirtualMachine(gui.getPid());
                }
            }
            if (vm != null) {
                jbm.attachTo(vm);
            }
        } catch (UnsatisfiedLinkError exception) {
            JOptionPane.showMessageDialog(null, "Failed to attach. Please use JDK as runtime.");
        } catch (Throwable t) {
            Throwable cause = t;
            while (cause.getCause() != null && cause.getCause() != cause) {
                cause = cause.getCause();
            }
            if (cause.getMessage() != null) {
                JOptionPane.showMessageDialog(null, "Failed to attach: " + cause.getMessage());
            } else {
                new ErrorDisplay(t);
            }
        }
    }

    private JMenu getSettings() {
        JMenu settings = new JMenu(Main.INSTANCE.getJByteMod().getLanguageRes().getResource("settings"));
        LanguageRes lr = Main.INSTANCE.getJByteMod().getLanguageRes();
        Options o = Main.INSTANCE.getJByteMod().getOptions();
        HashMap<String, JMenu> menus = new LinkedHashMap<>();
        HashMap<String, JMenu> roots = new LinkedHashMap<>();
        for (Option op : o.bools) {
            String group = op.getGroup();
            String[] groups = group.split("_");
            JMenu menu = null;
            if (menus.containsKey(group)) {
                menu = menus.get(group);
            } else {
                String full = "";
                for (String g : groups) {
                    if (!full.isEmpty()) {
                        full += "_";
                    }
                    full += g;
                    if (menus.containsKey(full)) {
                        menu = menus.get(full);
                        continue;
                    }
                    if (menu == null) {
                        menu = new JMenu(lr.getResource(g + "_group"));
                        roots.put(full, menu);
                        menus.put(full, menu);
                    } else {
                        JMenu subMenu = new JMenu(lr.getResource(g + "_group"));
                        menu.add(subMenu);
                        menu = subMenu;
                        menus.put(full, menu);
                    }
                }
            }
            switch (op.getType()) {
                case BOOLEAN:
                    JCheckBoxMenuItem jmi = new JCheckBoxMenuItem(lr.getResource(op.getName()), op.getBoolean());
                    jmi.addActionListener(new ActionListener() {

                        @Override
                        public void actionPerformed(ActionEvent e) {
                            op.setValue(jmi.isSelected());
                            o.save();
                            if(op.getName().equals("use_dark_theme")) {
                                LookUtils.changeTheme();
                            }
                        }
                    });
                    menu.add(jmi);
                    break;
                case STRING:
                    JMenu jm = new JMenu(lr.getResource(op.getName()));
                    JTextField jtf = new JTextField(op.getString());
                    jtf.setPreferredSize(new Dimension(Math.max((int) jtf.getPreferredSize().getWidth(), 128),
                            (int) jtf.getPreferredSize().getHeight()));
                    jm.add(Box.createHorizontalGlue());
                    jm.add(jtf);
                    jtf.addFocusListener(new FocusAdapter() {
                        public void focusLost(FocusEvent e) {
                            op.setValue(jtf.getText());
                            o.save();
                        }
                    });
                    menu.add(jm);
                    break;
                case INT:
                    jm = new JMenu(lr.getResource(op.getName()));
                    JFormattedTextField jnf = ClassDialogue.createNumberField(Integer.class, 0, Integer.MAX_VALUE);
                    jnf.setValue(op.getInteger());
                    jnf.setPreferredSize(new Dimension(Math.max((int) jnf.getPreferredSize().getWidth(), 64),
                            (int) jnf.getPreferredSize().getHeight()));
                    jm.add(jnf);
                    jnf.addFocusListener(new FocusAdapter() {
                        public void focusLost(FocusEvent e) {
                            op.setValue((int) jnf.getValue());
                            o.save();
                        }
                    });
                    menu.add(jm);
                    break;
                default:
                    break;
            }
        }
        for (JMenu m : roots.values()) {
            settings.add(m);
        }
        return settings;
    }

    protected void searchLDC() {
        final JPanel panel = new JPanel(new BorderLayout(5, 5));
        final JPanel input = new JPanel(new GridLayout(0, 1));
        final JPanel labels = new JPanel(new GridLayout(0, 1));
        panel.add(labels, "West");
        panel.add(input, "Center");
        panel.add(new JLabel(Main.INSTANCE.getJByteMod().getLanguageRes().getResource("big_string_warn")), "South");
        labels.add(new JLabel(Main.INSTANCE.getJByteMod().getLanguageRes().getResource("find")));
        JTextField cst = new JTextField();
        input.add(cst);
        JCheckBox exact = new JCheckBox(Main.INSTANCE.getJByteMod().getLanguageRes().getResource("exact"));
        JCheckBox regex = new JCheckBox("Regex");
        JCheckBox snstv = new JCheckBox(Main.INSTANCE.getJByteMod().getLanguageRes().getResource("case_sens"));
        labels.add(exact);
        labels.add(regex);
        input.add(snstv);
        input.add(new JPanel());
        if (JOptionPane.showConfirmDialog(this.jbm, panel, "Search LDC", JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE, searchIcon) == JOptionPane.OK_OPTION && !cst.getText().isEmpty()) {
            jbm.getSearchList().searchForConstant(cst.getText(), exact.isSelected(), snstv.isSelected(), regex.isSelected());
        }
    }

    protected void searchFieldValue() {
        final JPanel panel = new JPanel(new BorderLayout(5, 5));
        final JPanel input = new JPanel(new GridLayout(0, 1));
        final JPanel labels = new JPanel(new GridLayout(0, 1));
        panel.add(labels, "West");
        panel.add(input, "Center");
        panel.add(new JLabel(Main.INSTANCE.getJByteMod().getLanguageRes().getResource("big_string_warn")), "South");
        labels.add(new JLabel(Main.INSTANCE.getJByteMod().getLanguageRes().getResource("find")));
        JTextField cst = new JTextField();
        input.add(cst);
        JCheckBox exact = new JCheckBox(Main.INSTANCE.getJByteMod().getLanguageRes().getResource("exact"));
        JCheckBox regex = new JCheckBox("Regex");
        JCheckBox snstv = new JCheckBox(Main.INSTANCE.getJByteMod().getLanguageRes().getResource("case_sens"));
        labels.add(exact);
        labels.add(regex);
        input.add(snstv);
        input.add(new JPanel());
        if (JOptionPane.showConfirmDialog(this.jbm, panel, Main.INSTANCE.getJByteMod().getLanguageRes().getResource("search_field_value"), JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE, searchIcon) == JOptionPane.OK_OPTION && !cst.getText().isEmpty()) {
            jbm.getSearchList().searchForFieldValue(cst.getText(), exact.isSelected(), snstv.isSelected(), regex.isSelected());
        }
    }

    protected void replaceLDC() {
        final JPanel panel = new JPanel(new BorderLayout(5, 5));
        final JPanel input = new JPanel(new GridLayout(0, 1));
        final JPanel labels = new JPanel(new GridLayout(0, 1));
        panel.add(labels, "West");
        panel.add(input, "Center");
        panel.add(new JLabel(Main.INSTANCE.getJByteMod().getLanguageRes().getResource("big_string_warn")), "South");
        labels.add(new JLabel("Find: "));
        JTextField find = new JTextField();
        input.add(find);
        labels.add(new JLabel("Replace with: "));
        JTextField with = new JTextField();
        input.add(with);
        JComboBox<String> ldctype = new JComboBox<String>(new String[]{"String", "float", "double", "int", "long"});
        ldctype.setSelectedIndex(0);
        labels.add(new JLabel("Ldc Type: "));
        input.add(ldctype);
        JCheckBox exact = new JCheckBox(Main.INSTANCE.getJByteMod().getLanguageRes().getResource("exact"));
        JCheckBox cases = new JCheckBox(Main.INSTANCE.getJByteMod().getLanguageRes().getResource("case_sens"));
        labels.add(exact);
        input.add(cases);
        if (JOptionPane.showConfirmDialog(this.jbm, panel, "Replace LDC", JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE, searchIcon) == JOptionPane.OK_OPTION && !find.getText().isEmpty()) {
            int expectedType = ldctype.getSelectedIndex();
            boolean equal = exact.isSelected();
            boolean ignoreCase = !cases.isSelected();
            String findCst = find.getText();
            if (ignoreCase) {
                findCst = findCst.toLowerCase();
            }
            String replaceWith = with.getText();
            int i = 0;
            for (ClassNode cn : jbm.getJarArchive().getClasses().values()) {
                for (MethodNode mn : cn.methods) {
                    for (AbstractInsnNode ain : mn.instructions) {
                        if (ain.getType() == AbstractInsnNode.LDC_INSN) {
                            LdcInsnNode lin = (LdcInsnNode) ain;
                            Object cst = lin.cst;
                            int type;
                            if (cst instanceof String) {
                                type = 0;
                            } else if (cst instanceof Float) {
                                type = 1;
                            } else if (cst instanceof Double) {
                                type = 2;
                            } else if (cst instanceof Long) {
                                type = 3;
                            } else if (cst instanceof Integer) {
                                type = 4;
                            } else {
                                type = -1;
                            }
                            String cstStr = cst.toString();
                            if (ignoreCase) {
                                cstStr = cstStr.toLowerCase();
                            }
                            if (type == expectedType) {
                                if (equal ? cstStr.equals(findCst) : cstStr.contains(findCst)) {
                                    switch (type) {
                                        case 0:
                                            lin.cst = replaceWith;
                                            break;
                                        case 1:
                                            lin.cst = Float.parseFloat(replaceWith);
                                            break;
                                        case 2:
                                            lin.cst = Double.parseDouble(replaceWith);
                                            break;
                                        case 3:
                                            lin.cst = Long.parseLong(replaceWith);
                                            break;
                                        case 4:
                                            lin.cst = Integer.parseInt(replaceWith);
                                            break;
                                    }
                                    i++;
                                }
                            }
                        }
                    }
                }
            }
             Main.INSTANCE.getLogger().log(i + " ldc's replaced");
        }
    }

    protected void replaceFieldValue() {
        final JPanel panel = new JPanel(new BorderLayout(5, 5));
        final JPanel input = new JPanel(new GridLayout(0, 1));
        final JPanel labels = new JPanel(new GridLayout(0, 1));
        panel.add(labels, "West");
        panel.add(input, "Center");
        panel.add(new JLabel(Main.INSTANCE.getJByteMod().getLanguageRes().getResource("big_string_warn")), "South");
        labels.add(new JLabel("Find: "));
        JTextField find = new JTextField();
        input.add(find);
        labels.add(new JLabel("Replace with: "));
        JTextField with = new JTextField();
        input.add(with);
        JComboBox<String> type = new JComboBox<String>(new String[]{"String", "float", "double", "int", "long"});
        type.setSelectedIndex(0);
        labels.add(new JLabel("Type: "));
        input.add(type);
        JCheckBox exact = new JCheckBox(Main.INSTANCE.getJByteMod().getLanguageRes().getResource("exact"));
        JCheckBox cases = new JCheckBox(Main.INSTANCE.getJByteMod().getLanguageRes().getResource("case_sens"));
        labels.add(exact);
        input.add(cases);
        if (JOptionPane.showConfirmDialog(this.jbm, panel, Main.INSTANCE.getJByteMod().getLanguageRes().getResource("replace_field_value"), JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE, searchIcon) == JOptionPane.OK_OPTION && !find.getText().isEmpty()) {
            int expectedType = type.getSelectedIndex();
            boolean equal = exact.isSelected();
            boolean ignoreCase = !cases.isSelected();
            String findVal = find.getText();
            if (ignoreCase) {
                findVal = findVal.toLowerCase();
            }
            String replaceWith = with.getText();
            int i = 0;
            for (ClassNode cn : jbm.getJarArchive().getClasses().values()) {
                for (FieldNode fn : cn.fields) {
                    if (fn.value != null) {
                        Object val = fn.value;
                        int fType;
                        if (val instanceof String) {
                            fType = 0;
                        } else if (val instanceof Float) {
                            fType = 1;
                        } else if (val instanceof Double) {
                            fType = 2;
                        } else if (val instanceof Integer) {
                            fType = 3;
                        } else if (val instanceof Long) {
                            fType = 4;
                        } else {
                            fType = -1;
                        }
                        String valStr = val.toString();
                        if (ignoreCase) {
                            valStr = valStr.toLowerCase();
                        }
                        if (fType == expectedType) {
                            if (equal ? valStr.equals(findVal) : valStr.contains(findVal)) {
                                try {
                                    switch (fType) {
                                        case 0:
                                            fn.value = replaceWith;
                                            break;
                                        case 1:
                                            fn.value = Float.parseFloat(replaceWith);
                                            break;
                                        case 2:
                                            fn.value = Double.parseDouble(replaceWith);
                                            break;
                                        case 3:
                                            fn.value = Integer.parseInt(replaceWith);
                                            break;
                                        case 4:
                                            fn.value = Long.parseLong(replaceWith);
                                            break;
                                    }
                                    i++;
                                } catch (Exception e) {
                                     Main.INSTANCE.getLogger().err("Failed to parse replacement value: " + replaceWith);
                                }
                            }
                        }
                    }
                }
            }
             Main.INSTANCE.getLogger().log(i + " field values replaced");
        }
    }

    protected void searchField() {
        final JPanel panel = new JPanel(new BorderLayout(5, 5));
        final JPanel input = new JPanel(new GridLayout(0, 1));
        final JPanel labels = new JPanel(new GridLayout(0, 1));
        panel.add(labels, "West");
        panel.add(input, "Center");
        panel.add(new JLabel(Main.INSTANCE.getJByteMod().getLanguageRes().getResource("big_jar_warn")), "South");
        labels.add(new JLabel("Owner:"));
        JTextField owner = new JTextField();
        input.add(owner);
        labels.add(new JLabel("Name:"));
        JTextField name = new JTextField();
        input.add(name);
        labels.add(new JLabel("Desc:"));
        JTextField desc = new JTextField();
        input.add(desc);
        JCheckBox exact = new JCheckBox(Main.INSTANCE.getJByteMod().getLanguageRes().getResource("exact"));
        labels.add(exact);
        input.add(new JPanel());
        if (JOptionPane.showConfirmDialog(Main.INSTANCE.getJByteMod(), panel, "Search FieldInsnNode", JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE, searchIcon) == JOptionPane.OK_OPTION
                && !(name.getText().isEmpty() && owner.getText().isEmpty() && desc.getText().isEmpty())) {
            jbm.getSearchList().searchForFMInsn(owner.getText(), name.getText(), desc.getText(), exact.isSelected(), true);
        }
    }

    protected void searchMethod() {
        final JPanel panel = new JPanel(new BorderLayout(5, 5));
        final JPanel input = new JPanel(new GridLayout(0, 1));
        final JPanel labels = new JPanel(new GridLayout(0, 1));
        panel.add(labels, "West");
        panel.add(input, "Center");
        panel.add(new JLabel(Main.INSTANCE.getJByteMod().getLanguageRes().getResource("big_jar_warn")), "South");
        labels.add(new JLabel("Owner:"));
        JTextField owner = new JTextField();
        input.add(owner);
        labels.add(new JLabel("Name:"));
        JTextField name = new JTextField();
        input.add(name);
        labels.add(new JLabel("Desc:"));
        JTextField desc = new JTextField();
        input.add(desc);
        JCheckBox exact = new JCheckBox(Main.INSTANCE.getJByteMod().getLanguageRes().getResource("exact"));
        labels.add(exact);
        input.add(new JPanel());
        if (JOptionPane.showConfirmDialog(Main.INSTANCE.getJByteMod(), panel, "Search MethodInsnNode", JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE, searchIcon) == JOptionPane.OK_OPTION
                && !(name.getText().isEmpty() && owner.getText().isEmpty() && desc.getText().isEmpty())) {
            jbm.getSearchList().searchForFMInsn(owner.getText(), name.getText(), desc.getText(), exact.isSelected(), false);
        }
    }

    protected void openSaveDialogue() {
        if (jbm.getJarArchive() != null) {
            boolean isClass = jbm.getJarArchive().isSingleEntry();
            boolean isAndroidArchive = jbm.getJarArchive() instanceof AndroidArchive;
            boolean isAab = jbm.getJarArchive() instanceof AabArchive;
            String extension = isClass ? "class" : isAab ? "aab" : isAndroidArchive ? "apk" : "jar";
            String description = isClass ? "Java Class (*.class)"
                    : isAab ? "Signed Android App Bundle (*.aab)"
                    : isAndroidArchive ? "Signed Android Package (*.apk)" : "Java Package (*.jar)";
            JFileChooser jfc = new JFileChooser(new File(System.getProperty("user.home") + File.separator + "Desktop"));
            jfc.setAcceptAllFileFilterUsed(false);
            jfc.setDialogTitle("Save");
            jfc.setFileFilter(new FileNameExtensionFilter(description, extension));
            int result = jfc.showSaveDialog(this);
            if (result == JFileChooser.APPROVE_OPTION) {
                File output = jfc.getSelectedFile();
                if (!output.getName().toLowerCase(Locale.ROOT).endsWith("." + extension)) {
                    output = new File(output.getAbsolutePath() + "." + extension);
                }
                ApkSigningConfig signingConfig = isAndroidArchive ? ApkSigningDialog.show(jbm) : null;
                if (isAndroidArchive && signingConfig == null) {
                    return;
                }
                this.lastFile = output;
                 Main.INSTANCE.getLogger().log("Selected output file: " + output.getAbsolutePath());
                if (signingConfig == null) {
                    jbm.saveFile(output);
                } else {
                    jbm.saveFile(output, signingConfig);
                }
            }
        }
    }

    protected void openLoadDialogue() {
        JFileChooser jfc = new JFileChooser(new File(System.getProperty("user.home") + File.separator + "Desktop"));
        jfc.setAcceptAllFileFilterUsed(false);
        jfc.setFileFilter(new FileNameExtensionFilter(
                "Java or Android Package (*.jar, *.apk, *.aab) or Java Class (*.class)",
                "jar", "apk", "aab", "class"));
        int result = jfc.showOpenDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            File input = jfc.getSelectedFile();
             Main.INSTANCE.getLogger().log("Selected input file: " + input.getAbsolutePath());
            jbm.loadFile(input);
        }
    }

    public void addPluginMenu(ArrayList<Plugin> plugins) {
        if (pluginMenu != null) {
            remove(pluginMenu);
            pluginMenu = null;
        }
        if (jbm.getPluginManager() != null && !jbm.getPluginManager().getAvailablePlugins().isEmpty()) {
            pluginMenu = new JMenu("Plugins");
            for (Plugin p : plugins) {
                JMenuItem jmi = new JMenuItem(p.getName() + " v" + p.getVersion());
                jmi.setToolTipText("Version " + p.getVersion() + " by " + p.getAuthor());
                jmi.setEnabled(p.isClickable());
                jmi.addActionListener(e -> {
                    p.menuClick();
                });
                Dimension preferredSize = jmi.getPreferredSize();
                jmi.setPreferredSize(new Dimension(preferredSize.width + 20, preferredSize.height));
                pluginMenu.add(jmi);
            }
            pluginMenu.addSeparator();
            JMenuItem manage = new JMenuItem("Manage Plugins...");
            manage.addActionListener(event -> new PluginManagerDialog(jbm, jbm.getPluginManager()).setVisible(true));
            Dimension manageSize = manage.getPreferredSize();
            manage.setPreferredSize(new Dimension(Math.max(manageSize.width, 160), manageSize.height));
            pluginMenu.add(manage);
            add(pluginMenu);
        }
        revalidate();
        repaint();
    }

    public boolean isAgent() {
        return agent;
    }

    public File getLastFile() {
        return lastFile;
    }

    public void setLastFile(File lastFile) {
        this.lastFile = lastFile;
    }
}
