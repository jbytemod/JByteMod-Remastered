package de.xbrowniecodez.jbytemod.ui;

import de.xbrowniecodez.jbytemod.ui.tree.SortedTreeNode;
import de.xbrowniecodez.jbytemod.utils.AccessUtils;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Locale;

public class TreeCellRenderer extends DefaultTreeCellRenderer implements Opcodes {
    public static ImageIcon mpri, mpro, mpub, mdef; //method access
    public static ImageIcon abs, fin, nat, stat, syn; //general access

    static {
        TreeCellRenderer.mpri = SvgIcons.image("tree/method-private");
        TreeCellRenderer.mpro = SvgIcons.image("tree/method-protected");
        TreeCellRenderer.mpub = SvgIcons.image("tree/method-public");
        TreeCellRenderer.mdef = SvgIcons.image("tree/method-default");

        TreeCellRenderer.abs = SvgIcons.image("access/abstract");
        TreeCellRenderer.fin = SvgIcons.image("access/final");
        TreeCellRenderer.nat = SvgIcons.image("access/native");
        TreeCellRenderer.stat = SvgIcons.image("access/static");
        TreeCellRenderer.syn = SvgIcons.image("access/synthetic");
    }

    private final Icon javaArchive = SvgIcons.icon("tree/archive-java");
    private final Icon androidArchive = SvgIcons.icon("tree/archive-android");
    private final Icon classes = SvgIcons.icon("tree/classes");
    private final Icon resources = SvgIcons.icon("tree/resources");
    private final Icon file = SvgIcons.icon("tree/file");
    private final Icon xmlFile = SvgIcons.icon("tree/file-xml");
    private final Icon jsonFile = SvgIcons.icon("tree/file-json");
    private final Icon protoFile = SvgIcons.icon("tree/file-proto");
    private final Icon propertiesFile = SvgIcons.icon("tree/file-properties");
    private final Icon imageFile = SvgIcons.icon("tree/file-image");
    private final Icon packageClosed = SvgIcons.icon("tree/folder-package-closed");
    private final Icon packageOpen = SvgIcons.icon("tree/folder-package-open");
    private final Icon resourceClosed = SvgIcons.icon("tree/folder-resource-closed");
    private final Icon resourceOpen = SvgIcons.icon("tree/folder-resource-open");
    private final Icon clazz = SvgIcons.icon("tree/class");
    private final Icon enu = SvgIcons.icon("tree/enum");
    private final Icon itf = SvgIcons.icon("tree/interface");
    private final HashMap<Integer, Icon> methodIcons = new HashMap<>();

    public TreeCellRenderer() {
    }

    public static ImageIcon combineAccess(ImageIcon icon1, ImageIcon icon2, boolean right) {
        Image img1 = icon1.getImage();
        Image img2 = icon2.getImage();

        int w = icon1.getIconWidth();
        int h = icon1.getIconHeight();
        BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = image.createGraphics();
        g2.drawImage(img1, 0, 0, null);
        g2.drawImage(img2, right ? w / 4 : w / -4, h / -4, null);
        g2.dispose();

        return new ImageIcon(image);
    }

    public static ImageIcon combine(ImageIcon icon1, ImageIcon icon2) {
        Image img1 = icon1.getImage();
        Image img2 = icon2.getImage();

        int w = icon1.getIconWidth();
        int h = icon1.getIconHeight();
        BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = image.createGraphics();
        g2.drawImage(img1, 0, 0, null);
        g2.drawImage(img2, 0, 0, null);
        g2.dispose();

        return new ImageIcon(image);
    }

    @Override
    public Component getTreeCellRendererComponent(final JTree tree, final Object value, final boolean sel, final boolean expanded, final boolean leaf,
                                                  final int row, final boolean hasFocus) {
        super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus);
        final DefaultMutableTreeNode n = (DefaultMutableTreeNode) value;
        SortedTreeNode stn = (SortedTreeNode) n;
        if (stn.getNodeKind() == SortedTreeNode.NodeKind.ARCHIVE) {
            this.setIcon(this.javaArchive);
        } else if (stn.getNodeKind() == SortedTreeNode.NodeKind.ANDROID_ARCHIVE) {
            this.setIcon(this.androidArchive);
        } else if (stn.getNodeKind() == SortedTreeNode.NodeKind.CLASSES) {
            this.setIcon(this.classes);
        } else if (stn.getNodeKind() == SortedTreeNode.NodeKind.RESOURCES) {
            this.setIcon(this.resources);
        } else if (stn.getNodeKind() == SortedTreeNode.NodeKind.PACKAGE) {
            this.setIcon(expanded ? this.packageOpen : this.packageClosed);
        } else if (stn.getNodeKind() == SortedTreeNode.NodeKind.RESOURCE_DIRECTORY) {
            this.setIcon(expanded ? this.resourceOpen : this.resourceClosed);
        } else if (stn.getNodeKind() == SortedTreeNode.NodeKind.RESOURCE) {
            this.setIcon(resourceIcon(stn.toString()));
        } else if (stn.getNodeKind() == SortedTreeNode.NodeKind.CLASS) {
            ClassNode cn = stn.getClassNode();
            if (cn != null) {
                if (AccessUtils.isInterface(cn.access)) {
                    this.setIcon(this.itf);
                } else if (AccessUtils.isEnum(cn.access)) {
                    this.setIcon(this.enu);
                } else {
                    this.setIcon(this.clazz);
                }
            }
        } else if (stn.getNodeKind() == SortedTreeNode.NodeKind.METHOD) {
            MethodNode mn = stn.getMethodNode();
            if (mn != null) {
                this.setIcon(methodIcons.computeIfAbsent(mn.access, TreeCellRenderer::methodIcon));
            } else {
                throw new IllegalArgumentException();
            }
        } else {
            this.setIcon(this.file);
        }
        return this;
    }

    private Icon resourceIcon(String name) {
        String lowerName = name.toLowerCase(Locale.ROOT);
        if (lowerName.endsWith(".xml")) {
            return xmlFile;
        }
        if (lowerName.endsWith(".json") || lowerName.endsWith(".json5")) {
            return jsonFile;
        }
        if (lowerName.endsWith(".proto")) {
            return protoFile;
        }
        if (lowerName.endsWith(".properties") || lowerName.endsWith(".mf")) {
            return propertiesFile;
        }
        if (lowerName.endsWith(".png") || lowerName.endsWith(".webp")
                || lowerName.endsWith(".jpg") || lowerName.endsWith(".jpeg")
                || lowerName.endsWith(".gif") || lowerName.endsWith(".bmp")
                || lowerName.endsWith(".wbmp")) {
            return imageFile;
        }
        return file;
    }

    private static Icon methodIcon(int access) {
        String visibility;
        if ((access & ACC_PUBLIC) != 0) visibility = "public";
        else if ((access & ACC_PROTECTED) != 0) visibility = "protected";
        else if ((access & ACC_PRIVATE) != 0) visibility = "private";
        else visibility = "default";
        return SvgIcons.icon("tree/method-" + visibility);
    }

    public String getFileName(final DefaultMutableTreeNode node) {
        return node.toString();
    }
}
