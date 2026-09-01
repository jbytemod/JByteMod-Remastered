package de.xbrowniecodez.jbytemod.archive;

import me.grax.jbytemod.JarArchive;
import org.objectweb.asm.tree.ClassNode;

import java.util.HashMap;
import java.util.Map;

public final class ApkArchive extends JarArchive {
    private final Map<String, Integer> entryMethods = new HashMap<>();
    private final Map<String, String> dexEntries = new HashMap<>();
    private int minSdkVersion = 13;

    public ApkArchive(Map<String, ClassNode> classes, Map<String, byte[]> output) {
        super(classes, output);
    }

    public Map<String, Integer> getEntryMethods() {
        return entryMethods;
    }

    public Map<String, String> getDexEntries() {
        return dexEntries;
    }

    public int getMinSdkVersion() {
        return minSdkVersion;
    }

    public void recordEntry(String name, int method) {
        entryMethods.put(name, method);
    }

    public void recordDexClass(String className, String dexEntry) {
        dexEntries.put(className, dexEntry);
    }

    public void includeDexVersion(int apiLevel) {
        if (apiLevel > minSdkVersion) {
            minSdkVersion = apiLevel;
        }
    }
}
