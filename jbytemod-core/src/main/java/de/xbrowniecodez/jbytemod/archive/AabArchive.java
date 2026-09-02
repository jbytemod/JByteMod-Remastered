package de.xbrowniecodez.jbytemod.archive;

import org.objectweb.asm.tree.ClassNode;

import java.util.Map;

public final class AabArchive extends AndroidArchive {
    public AabArchive(Map<String, ClassNode> classes, Map<String, byte[]> output) {
        super(classes, output);
    }
}
