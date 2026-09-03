package de.xbrowniecodez.jbytemod.decompiler;

import de.xbrowniecodez.jbytemod.JByteMod;
import me.grax.jbytemod.decompiler.Decompiler;
import me.grax.jbytemod.ui.DecompilerPanel;

import org.jetbrains.java.decompiler.api.Decompiler.Builder;
import org.jetbrains.java.decompiler.main.extern.IContextSource;
import org.jetbrains.java.decompiler.main.extern.IFernflowerLogger;
import org.jetbrains.java.decompiler.main.extern.IFernflowerPreferences;
import org.jetbrains.java.decompiler.main.extern.IResultSaver;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.MethodNode;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.List;
import java.util.jar.Manifest;

public class VineflowerDecompiler extends Decompiler implements IContextSource, IResultSaver {
    private String content;
    private byte[] byteArray;
    private String className;

    public VineflowerDecompiler(JByteMod jbm, DecompilerPanel dp) {
        super(jbm, dp);
    }

    @Override
    public String decompile(byte[] b, MethodNode mn) {
        this.content = null;
        this.byteArray = b;
        this.className = new ClassReader(b).getClassName();

        Builder builder = new Builder()
                .inputs(this)
                .output(this)
                .logger(new FernFlowerLogger());
        IFernflowerPreferences.getDefaults().forEach(builder::option);
        if (mn != null) {
            builder.option(IFernflowerPreferences.METHOD_TO_DECOMPILE,
                    className + "." + mn.name + mn.desc);
        }
        builder.build().decompile();
        return content == null ? "Unable to decompile class." : content.trim();
    }

    @Override
    public String getName() {
        return className + CLASS_SUFFIX;
    }

    @Override
    public Entries getEntries() {
        return new Entries(List.of(Entry.atBase(className)), List.of(), List.of());
    }

    @Override
    public InputStream getInputStream(String resource) {
        if ((className + CLASS_SUFFIX).equals(resource)) {
            return new ByteArrayInputStream(byteArray);
        }
        return null;
    }

    @Override
    public IOutputSink createOutputSink(IResultSaver saver) {
        return new IOutputSink() {
            @Override public void begin() {}

            @Override
            public void acceptClass(String qualifiedName, String fileName, String classContent, int[] mapping) {
                content = classContent;
            }

            @Override public void acceptDirectory(String directory) {}
            @Override public void acceptOther(String path) {}
            @Override public void close() {}
        };
    }

    @Override
    public void saveClassFile(String path, String qualifiedName, String entryName, String content, int[] mapping) {
        this.content = content;
    }

    @Override public void closeArchive(String path, String archiveName) {}
    @Override public void copyEntry(String source, String path, String archiveName, String entry) {}
    @Override public void copyFile(String source, String path, String entryName) {}
    @Override public void createArchive(String path, String archiveName, Manifest manifest) {}
    @Override public void saveClassEntry(String path, String archiveName, String qualifiedName, String entryName, String content) {}
    @Override public void saveDirEntry(String path, String archiveName, String entryName) {}
    @Override public void saveFolder(String path) {}

    public static class FernFlowerLogger extends IFernflowerLogger {
        @Override
        public void writeMessage(String message, Severity severity) {}

        @Override
        public void writeMessage(String message, Severity severity, Throwable t) {}
    }
}
