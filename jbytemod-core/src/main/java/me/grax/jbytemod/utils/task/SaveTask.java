package me.grax.jbytemod.utils.task;

import de.xbrowniecodez.jbytemod.Main;
import de.xbrowniecodez.jbytemod.JByteMod;
import de.xbrowniecodez.jbytemod.archive.ApkArchive;
import de.xbrowniecodez.jbytemod.utils.apk.ApkCompiler;
import de.xbrowniecodez.jbytemod.utils.apk.ApkSigningConfig;
import me.grax.jbytemod.JarArchive;
import me.grax.jbytemod.ui.PageEndPanel;
import me.grax.jbytemod.utils.ErrorDisplay;
import org.apache.tools.zip.ZipEntry;
import org.apache.tools.zip.ZipOutputStream;
import org.objectweb.asm.tree.ClassNode;

import de.xbrowniecodez.jbytemod.asm.CustomClassWriter;

import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

public class SaveTask extends SwingWorker<Void, Integer> {

    private final File output;
    private final PageEndPanel jpb;
    private final JarArchive file;
    private final ApkSigningConfig apkSigningConfig;

    public SaveTask(JByteMod jbm, File output, JarArchive file) {
        this(jbm, output, file, ApkSigningConfig.debugKey());
    }

    public SaveTask(JByteMod jbm, File output, JarArchive file, ApkSigningConfig apkSigningConfig) {
        this.output = output;
        this.file = file;
        this.jpb = jbm.getPageEndPanel();
        this.apkSigningConfig = apkSigningConfig;
    }

    @Override
    protected Void doInBackground() throws Exception {
        synchronized (this.file) {
            Map<String, ClassNode> classes = this.file.getClasses();
            Map<String, byte[]> outputBytes = new HashMap<>();
            if (this.file.getOutput() != null) {
                outputBytes.putAll(this.file.getOutput());
            }
            int flags = Main.INSTANCE.getJByteMod().getOptions().get("compute_maxs").getBoolean() ? 1 : 0;
            Main.INSTANCE.getLogger().log("Writing..");
            if (this.file.isSingleEntry()) {
                ClassNode node = classes.values().iterator().next();
                CustomClassWriter writer = new CustomClassWriter(flags);
                node.accept(writer);
                publish(50);
                Main.INSTANCE.getLogger().log("Saving..");
                Files.write(this.output.toPath(), writer.toByteArray());
                publish(100);
                Main.INSTANCE.getLogger().log("Saving successful!");
                return null;
            }

            if (this.file instanceof ApkArchive apkArchive) {
                publish(0);
                Main.INSTANCE.getLogger().log("Compiling Android DEX files...");
                ApkCompiler.save(apkArchive, output.toPath(), flags, this::publish, apkSigningConfig);
                Main.INSTANCE.getLogger().log("Saving successful! The APK was aligned, signed, and verified.");
                publish(100);
                return null;
            }

            publish(0);
            double size = classes.keySet().size();
            double i = 0;
            for (String s : classes.keySet()) {
                try {
                    ClassNode node = classes.get(s);
                    CustomClassWriter writer = new CustomClassWriter(flags);
                    node.accept(writer);
                    outputBytes.remove(s);
                    outputBytes.put(s + ".class", writer.toByteArray());
                    publish((int) ((i++ / size) * 50d));
                } catch (StringIndexOutOfBoundsException exception) {
                    Main.INSTANCE.getLogger().println("Failed to save " + classes.get(s).name);
                }
            }
            publish(50);
            Main.INSTANCE.getLogger().log("Saving..");
            this.saveAsJarNew(outputBytes, output.getAbsolutePath());
            Main.INSTANCE.getLogger().log("Saving successful!");
            publish(100);
            return null;
        }
    }

    public void saveAsJarNew(Map<String, byte[]> outBytes, String fileName) throws IOException {
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(Paths.get(fileName)))) {
            out.setEncoding("UTF-8");
            for (String entry : outBytes.keySet()) {
                out.putNextEntry(new ZipEntry(entry));
                if (!entry.endsWith("/") && !entry.endsWith("\\")) {
                    out.write(outBytes.get(entry));
                }
                out.closeEntry();
            }
        }
    }

    @Override
    protected void process(List<Integer> chunks) {
        int i = chunks.get(chunks.size() - 1);
        jpb.setValue(i);
        super.process(chunks);
    }

    @Override
    protected void done() {
        try {
            get();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        } catch (ExecutionException exception) {
            Main.INSTANCE.getLogger().log("Saving failed!");
            Throwable cause = exception.getCause() == null ? exception : exception.getCause();
            new ErrorDisplay(cause);
        } finally {
            apkSigningConfig.close();
        }
    }

}
