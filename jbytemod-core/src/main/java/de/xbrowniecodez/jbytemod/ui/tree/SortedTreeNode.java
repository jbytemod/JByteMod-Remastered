package de.xbrowniecodez.jbytemod.ui.tree;

import lombok.Getter;
import lombok.Setter;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import javax.swing.tree.DefaultMutableTreeNode;
import java.util.Collections;
import java.util.Comparator;
import java.util.Vector;

@Getter
@Setter
public class SortedTreeNode extends DefaultMutableTreeNode {

    public enum NodeKind {
        DEFAULT,
        ARCHIVE,
        ANDROID_ARCHIVE,
        CLASSES,
        RESOURCES,
        PACKAGE,
        RESOURCE_DIRECTORY,
        RESOURCE,
        CLASS,
        METHOD
    }

    private ClassNode classNode;
    private MethodNode methodNode;
    private String className;
    private NodeKind nodeKind = NodeKind.DEFAULT;
    private String treePathKey;

    public SortedTreeNode(ClassNode classNode, MethodNode methodNode) {
        this.classNode = classNode;
        this.methodNode = methodNode;
        this.nodeKind = NodeKind.METHOD;
        this.treePathKey = "method:" + classNode.name + ":" + methodNode.name + methodNode.desc;
        setClassName();
    }

    public SortedTreeNode(ClassNode classNode) {
        this.classNode = classNode;
        this.nodeKind = NodeKind.CLASS;
        this.treePathKey = "class:" + classNode.name;
        setClassName();
    }

    public SortedTreeNode(Object userObject) {
        super(userObject);
    }

    public SortedTreeNode(Object userObject, NodeKind nodeKind, String treePathKey) {
        super(userObject);
        this.nodeKind = nodeKind;
        this.treePathKey = treePathKey;
    }

    public boolean isStructural() {
        return nodeKind == NodeKind.ARCHIVE
                || nodeKind == NodeKind.ANDROID_ARCHIVE
                || nodeKind == NodeKind.CLASSES
                || nodeKind == NodeKind.RESOURCES;
    }

    public boolean isResourceNode() {
        return nodeKind == NodeKind.RESOURCES
                || nodeKind == NodeKind.RESOURCE_DIRECTORY
                || nodeKind == NodeKind.RESOURCE;
    }

    private void setClassName() {
        String[] split = classNode.name.split("/");
        this.className = split[split.length - 1] + ".class";
    }

    @SuppressWarnings("unchecked")
    public void sort() {
        if (children != null) {
            ((Vector<DefaultMutableTreeNode>) (Vector<?>) children).sort(compare());
        }
    }

    private Comparator<DefaultMutableTreeNode> compare() {
        return (o1, o2) -> {
            int kindComparison = Integer.compare(sortRank(o1), sortRank(o2));
            if (kindComparison != 0) {
                return kindComparison;
            }
            int insensitiveComparison = o1.toString().compareToIgnoreCase(o2.toString());
            return insensitiveComparison != 0
                    ? insensitiveComparison : o1.toString().compareTo(o2.toString());
        };
    }

    private static int sortRank(DefaultMutableTreeNode node) {
        if (node instanceof SortedTreeNode sortedNode) {
            return switch (sortedNode.nodeKind) {
                case ARCHIVE, ANDROID_ARCHIVE, CLASSES, RESOURCES, PACKAGE, RESOURCE_DIRECTORY -> 0;
                case DEFAULT -> node.getChildCount() > 0 ? 0 : 1;
                case RESOURCE, CLASS, METHOD -> 1;
            };
        }
        return node.getChildCount() > 0 ? 0 : 1;
    }

    @Override
    public String toString() {
        if (methodNode != null) {
            return methodNode.name;
        }
        if (classNode != null) {
            return className;
        }
        return userObject != null ? userObject.toString() : "";
    }
}
