package me.grax.jbytemod.ui;

import de.xbrowniecodez.jbytemod.utils.AccessUtils;
import de.xbrowniecodez.jbytemod.ui.SvgIcons;
import de.xbrowniecodez.jbytemod.ui.TreeCellRenderer;
import org.objectweb.asm.Opcodes;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;

public class JAccessSelectorPanel extends JPanel implements Opcodes {

    private VisibilityButton visibility;
    private ExtrasButton extras;
    private OtherButton other;
    private JButton accessHelper;

    public JAccessSelectorPanel(int accezz) {
        this.setLayout(new GridLayout(1, 4));
        this.add(visibility = new VisibilityButton(accezz));
        this.add(extras = new ExtrasButton(accezz));
        this.add(other = new OtherButton(accezz));
        accessHelper = new JButton(SvgIcons.icon("toolbar/access"));
        accessHelper.addActionListener(e -> {
            new JAccessHelper(getAccess(), ae -> {
                setAccess(Integer.parseInt(ae.getActionCommand()));
            }).setVisible(true);
        });
        this.add(accessHelper);
    }

    public int getAccess() {
        return visibility.getVisibility() | extras.getVisibility() | other.getVisibility();
    }

    public void setAccess(int accezz) {
        visibility.updateVisibility(accezz);
        extras.updateVisibility(accezz);
        other.updateVisibility(accezz);
    }

    public class VisibilityButton extends JButton {
        private int visibility;

        public VisibilityButton(int access) {
            updateVisibility(access);
            this.addMouseListener(new MouseAdapter() {
                public void mouseClicked(MouseEvent e) {
                    JPopupMenu popupMenu = generatePopupMenu();
                    popupMenu.show(VisibilityButton.this, 0, VisibilityButton.this.getHeight());
                }
            });
        }

        public void updateVisibility(int access) {
            if (AccessUtils.isPublic(access)) {
                visibility = ACC_PUBLIC;
                this.setIcon(new ImageIcon(TreeCellRenderer.mpub.getImage()));
            } else if (AccessUtils.isPrivate(access)) {
                visibility = ACC_PRIVATE;
                this.setIcon(new ImageIcon(TreeCellRenderer.mpri.getImage()));
            } else if (AccessUtils.isProtected(access)) {
                visibility = ACC_PROTECTED;
                this.setIcon(new ImageIcon(TreeCellRenderer.mpro.getImage()));
            } else {
                visibility = 0; // default
                this.setIcon(new ImageIcon(TreeCellRenderer.mdef.getImage()));
            }
        }

        private JPopupMenu generatePopupMenu() {
            JPopupMenu pm = new JPopupMenu();
            JToggleButton pub = new JToggleButton(new ImageIcon(TreeCellRenderer.mpub.getImage()));
            pub.setToolTipText("public");
            JToggleButton pri = new JToggleButton(new ImageIcon(TreeCellRenderer.mpri.getImage()));
            pri.setToolTipText("private");
            JToggleButton pro = new JToggleButton(new ImageIcon(TreeCellRenderer.mpro.getImage()));
            pro.setToolTipText("protected");
            JToggleButton def = new JToggleButton(new ImageIcon(TreeCellRenderer.mdef.getImage()));
            def.setToolTipText("none");

            ButtonGroup group = new ButtonGroup();
            group.add(pub);
            group.add(pri);
            group.add(pro);
            group.add(def);

            switch (visibility) {
                case ACC_PUBLIC:
                    pub.setSelected(true);
                    break;
                case ACC_PRIVATE:
                    pri.setSelected(true);
                    break;
                case ACC_PROTECTED:
                    pro.setSelected(true);
                    break;
                default:
                    def.setSelected(true);
                    break;
            }

            pub.addActionListener(e -> updateVisibility(ACC_PUBLIC));
            pri.addActionListener(e -> updateVisibility(ACC_PRIVATE));
            pro.addActionListener(e -> updateVisibility(ACC_PROTECTED));
            def.addActionListener(e -> updateVisibility(0));

            pm.add(pub);
            pm.add(pri);
            pm.add(pro);
            pm.add(def);

            return pm;
        }

        public int getVisibility() {
            return visibility;
        }
    }

    public class ExtrasButton extends JButton {
        private int visibility;

        public ExtrasButton(int access) {
            updateVisibility(access);
            this.addMouseListener(new MouseAdapter() {
                public void mouseClicked(MouseEvent e) {
                    JPopupMenu popupMenu = generatePopupMenu();
                    popupMenu.show(ExtrasButton.this, 0, ExtrasButton.this.getHeight());
                }
            });
        }

        public void updateVisibility(int access) {
            visibility = 0;
            if (AccessUtils.isFinal(access)) {
                visibility |= ACC_FINAL;
            }
            if (AccessUtils.isNative(access)) {
                visibility |= ACC_NATIVE;
            }
            if (AccessUtils.isStatic(access)) {
                visibility |= ACC_STATIC;
            }
            if (AccessUtils.isSynthetic(access)) {
                visibility |= ACC_SYNTHETIC;
            }
            if (AccessUtils.isAbstract(access)) {
                visibility |= ACC_ABSTRACT;
            }

            ImageIcon preview = new ImageIcon(new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB));
            boolean empty = true;
            if (AccessUtils.isAbstract(access)) {
                preview = TreeCellRenderer.combineAccess(preview, TreeCellRenderer.abs, true);
                empty = false;
            } else {
                boolean scndRight = true;
                if (AccessUtils.isFinal(access)) {
                    preview = TreeCellRenderer.combineAccess(preview, TreeCellRenderer.fin, true);
                    empty = scndRight = false;
                } else if (AccessUtils.isNative(access)) { // do not allow triples
                    preview = TreeCellRenderer.combineAccess(preview, TreeCellRenderer.nat, true);
                    empty = scndRight = false;
                }
                if (AccessUtils.isStatic(access)) {
                    preview = TreeCellRenderer.combineAccess(preview, TreeCellRenderer.stat, scndRight);
                    empty = false;
                } else if (AccessUtils.isSynthetic(access)) {
                    preview = TreeCellRenderer.combineAccess(preview, TreeCellRenderer.syn, scndRight);
                    empty = false;
                }
            }
            this.setIcon(preview);
        }

        private JPopupMenu generatePopupMenu() {
            JPopupMenu pm = new JPopupMenu();
            JToggleButton abs = new JToggleButton(new ImageIcon(TreeCellRenderer.abs.getImage()));
            abs.setToolTipText("abstract");
            abs.setSelected(AccessUtils.isAbstract(visibility));
            JToggleButton fin = new JToggleButton(new ImageIcon(TreeCellRenderer.fin.getImage()));
            fin.setToolTipText("final");
            fin.setSelected(AccessUtils.isFinal(visibility));
            JToggleButton nat = new JToggleButton(new ImageIcon(TreeCellRenderer.nat.getImage()));
            nat.setToolTipText("native");
            nat.setSelected(AccessUtils.isNative(visibility));
            JToggleButton stat = new JToggleButton(new ImageIcon(TreeCellRenderer.stat.getImage()));
            stat.setToolTipText("static");
            stat.setSelected(AccessUtils.isStatic(visibility));
            JToggleButton syn = new JToggleButton(new ImageIcon(TreeCellRenderer.syn.getImage()));
            syn.setToolTipText("synthetic");
            syn.setSelected(AccessUtils.isSynthetic(visibility));

            abs.addActionListener(e -> {
                if (AccessUtils.isAbstract(visibility)) {
                    visibility -= ACC_ABSTRACT;
                } else {
                    visibility |= ACC_ABSTRACT;
                    if (fin.isSelected())
                        fin.doClick();
                    if (nat.isSelected())
                        nat.doClick();
                    if (stat.isSelected())
                        stat.doClick();
                    if (syn.isSelected())
                        syn.doClick();
                }
                updateVisibility(visibility);
            });
            fin.addActionListener(e -> {
                if (AccessUtils.isFinal(visibility)) {
                    visibility -= ACC_FINAL;
                } else {
                    visibility |= ACC_FINAL;
                    if (nat.isSelected())
                        nat.doClick();
                    if (abs.isSelected())
                        abs.doClick();
                }
                updateVisibility(visibility);
            });
            nat.addActionListener(e -> {
                if (AccessUtils.isNative(visibility)) {
                    visibility -= ACC_NATIVE;
                } else {
                    visibility |= ACC_NATIVE;
                    if (fin.isSelected())
                        fin.doClick();
                    if (abs.isSelected())
                        abs.doClick();
                }
                updateVisibility(visibility);
            });
            stat.addActionListener(e -> {
                if (AccessUtils.isStatic(visibility)) {
                    visibility -= ACC_STATIC;
                } else {
                    visibility |= ACC_STATIC;
                    if (abs.isSelected())
                        abs.doClick();
                }
                updateVisibility(visibility);
            });
            syn.addActionListener(e -> {
                if (AccessUtils.isSynthetic(visibility)) {
                    visibility -= ACC_SYNTHETIC;
                } else {
                    visibility |= ACC_SYNTHETIC;
                    if (abs.isSelected())
                        abs.doClick();
                }
                updateVisibility(visibility);
            });

            pm.add(fin);
            pm.add(nat);
            pm.add(stat);
            pm.add(syn);
            pm.add(abs);

            return pm;
        }

        public int getVisibility() {
            return visibility;
        }
    }

    public class OtherButton extends JButton {
        private final List<String> alreadyCovered = Arrays.asList("ACC_PUBLIC", "ACC_PRIVATE", "ACC_PROTECTED", "ACC_STATIC", "ACC_FINAL", "ACC_NATIVE",
                "ACC_ABSTRACT", "ACC_SYNTHETIC", "ACC_STATIC_PHASE", "ACC_TRANSITIVE");
        private final HashMap<String, Integer> otherTypes = new HashMap<>();
        private int visibility;

        public OtherButton(int access) {
            try {
                for (Field d : Opcodes.class.getDeclaredFields()) {
                    if (d.getName().startsWith("ACC_") && !alreadyCovered.contains(d.getName())) {
                        int acc = d.getInt(null);
                        otherTypes.put(d.getName().substring(4).toLowerCase(), acc);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            updateVisibility(access);
            this.addMouseListener(new MouseAdapter() {
                public void mouseClicked(MouseEvent e) {
                    JPopupMenu popupMenu = generatePopupMenu();
                    popupMenu.show(OtherButton.this, 0, OtherButton.this.getHeight());
                }
            });
        }

        public void updateVisibility(int access) {
            visibility = 0;
            for (int acc : otherTypes.values()) {
                if ((access & acc) != 0) {
                    visibility |= acc;
                }
            }
            this.setText("...");
        }

        private JPopupMenu generatePopupMenu() {
            JPopupMenu pm = new JPopupMenu();
            for (Entry<String, Integer> acc : otherTypes.entrySet()) {
                JToggleButton jtb = new JToggleButton(
                        acc.getKey().substring(0, 1).toUpperCase() + acc.getKey().substring(1, Math.min(acc.getKey().length(), 7)));
                jtb.setSelected((visibility & acc.getValue()) != 0);
                jtb.addActionListener(e -> {
                    if ((visibility & acc.getValue()) != 0) {
                        visibility -= acc.getValue();
                    } else {
                        visibility |= acc.getValue();
                    }
                });
                jtb.setFont(new Font(jtb.getFont().getName(), Font.PLAIN, 10));
                pm.add(jtb);
            }
            return pm;
        }

        public int getVisibility() {
            return visibility;
        }
    }
}
