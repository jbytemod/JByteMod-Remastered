package de.xbrowniecodez.jbytemod.utils.task;

import com.googlecode.d2j.dex.Dex2Asm;
import com.googlecode.d2j.node.DexFileNode;
import com.googlecode.d2j.reader.DexFileReader;
import de.xbrowniecodez.android.asm.Dex2ASMVisitorFactory;
import de.xbrowniecodez.jbytemod.archive.ApkArchive;
import de.xbrowniecodez.jbytemod.Main;
import de.xbrowniecodez.jbytemod.utils.BytecodeUtils;
import de.xbrowniecodez.jbytemod.utils.ClassUtils;
import de.xbrowniecodez.jbytemod.JByteMod;
import me.grax.jbytemod.JarArchive;
import me.grax.jbytemod.ui.PageEndPanel;
import me.grax.jbytemod.utils.ErrorDisplay;
import me.grax.jbytemod.utils.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.tools.zip.ZipEntry;
import org.apache.tools.zip.ZipFile;
import org.objectweb.asm.tree.ClassNode;

import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ExecutionException;

public class LoadTask extends SwingWorker<Void, Integer> {

    private ZipFile input;
    private PageEndPanel jpb;
    private JByteMod jbm;
    private File file;
    private int jarSize; // including directories
    private int loaded;
    private JarArchive ja;
    private long maxMem;
    private boolean memoryWarning;
    private long startTime;
    private int othersFile;
    private int junkClasses;
    private boolean useZipInputStream;

    public LoadTask(JByteMod jbm, File input, JarArchive ja) throws IOException {
        this.othersFile = 0;
        this.startTime = System.currentTimeMillis();
        this.file = input;
        this.jbm = jbm;
        this.jpb = jbm.getPageEndPanel();
        this.ja = ja;

        try {
            this.input = new ZipFile(input, "UTF-8");
            this.jarSize = countFiles(this.input);
            Main.INSTANCE.getLogger().log(jarSize + " files to load!");
        } catch (IOException e) {
            if (e.getMessage() != null && e.getMessage().contains("central directory is empty")) {
                Main.INSTANCE.getLogger().warn(
                        "ZipFile failed to read central directory. Falling back to ZipInputStream stream parsing...");
                this.useZipInputStream = true;
                this.jarSize = 1000; // generic size since we can't count sequentially easily
            } else {
                throw e;
            }
        }

        this.maxMem = Runtime.getRuntime().maxMemory();
        this.memoryWarning = Main.INSTANCE.getJByteMod().getOptions().get("memory_warning").getBoolean();
    }

    @Override
    protected Void doInBackground() throws Exception {
        publish(0);
        if (useZipInputStream) {
            this.loadFilesFallback();
        } else if (input != null) {
            this.loadFiles(input);
        }
        publish(100);
        return null;
    }

    public int countFiles(final ZipFile zipFile) {
        final Enumeration<ZipEntry> entries = zipFile.getEntries();
        int c = 0;
        while (entries.hasMoreElements()) {
            entries.nextElement();
            ++c;
        }
        return c;
    }

    /**
     * loads both classes and other files at the same time
     */
    public void loadFiles(ZipFile jar) throws IOException {
        long mem = Runtime.getRuntime().totalMemory();
        if (mem / (double) maxMem > 0.75) {
            Main.INSTANCE.getLogger().warn("Memory usage is high: " + Math.round((mem / (double) maxMem * 100d)) + "%");
        }
        System.gc();
        Map<String, ClassNode> classes = new HashMap<String, ClassNode>();
        Map<String, byte[]> otherFiles = new HashMap<String, byte[]>();

        final Enumeration<ZipEntry> entries = jar.getEntries();
        while (entries.hasMoreElements()) {
            if (ja instanceof ApkArchive) {
                readApk(jar, entries.nextElement(), classes, otherFiles);
            } else {
                readJar(jar, entries.nextElement(), classes, otherFiles);
            }
        }
        jar.close();
        ja.setClasses(classes);
        ja.setOutput(otherFiles);

        this.othersFile = otherFiles.size();
        for (String name : otherFiles.keySet()) {
            if (name.endsWith(".class") || name.endsWith(".class/"))
                junkClasses++;
        }
    }

    public void loadFilesFallback() throws IOException {
        long mem = Runtime.getRuntime().totalMemory();
        if (mem / (double) maxMem > 0.75) {
            Main.INSTANCE.getLogger().warn("Memory usage is high: " + Math.round((mem / (double) maxMem * 100d)) + "%");
        }
        System.gc();
        Map<String, ClassNode> classes = new HashMap<String, ClassNode>();
        Map<String, byte[]> otherFiles = new HashMap<String, byte[]>();

        try (java.util.zip.ZipInputStream zis = new java.util.zip.ZipInputStream(new java.io.FileInputStream(file))) {
            java.util.zip.ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory())
                    continue;
                String name = entry.getName();
                byte[] bytes = IOUtils.toByteArray(zis);

                if (ja instanceof ApkArchive) {
                    readApkBytes(name, bytes, classes, otherFiles, entry.getMethod());
                } else {
                    readJarBytes(name, bytes, classes, otherFiles);
                }
            }
        }

        ja.setClasses(classes);
        ja.setOutput(otherFiles);

        this.othersFile = otherFiles.size();
        for (String name : otherFiles.keySet()) {
            if (name.endsWith(".class") || name.endsWith(".class/"))
                junkClasses++;
        }
    }

    private void readApk(ZipFile jar, ZipEntry zipEntry, Map<String, ClassNode> classes,
            Map<String, byte[]> otherFiles) {
        String name = zipEntry.getName();
        try (InputStream jis = jar.getInputStream(zipEntry)) {
            byte[] bytes = IOUtils.toByteArray(jis);
            readApkBytes(name, bytes, classes, otherFiles, zipEntry.getMethod());
        } catch (Exception e) {
            e.printStackTrace();
            Main.INSTANCE.getLogger().err("Failed loading file: " + name);
        }
    }

    private void readApkBytes(String name, byte[] bytes, Map<String, ClassNode> classes,
            Map<String, byte[]> otherFiles, int method) {
        Dex2Asm dex2ASM = new Dex2Asm();
        long startTime = System.currentTimeMillis();

        try {
            ApkArchive apkArchive = (ApkArchive) ja;
            apkArchive.recordEntry(name, method);
            if (name.startsWith("classes") && name.endsWith(".dex")) {
                if (bytes.length >= 8) {
                    apkArchive.includeDexVersion(dexMinApi(bytes));
                }
                DexFileReader dexFileReader = new DexFileReader(bytes);

                DexFileNode dexFileNode = new DexFileNode();
                dexFileReader.accept(dexFileNode);
                dexFileNode.clzs.forEach(dexClassNode -> {
                    ClassNode classNode = new ClassNode();

                    Dex2ASMVisitorFactory dex2ASMVisitorFactory = new Dex2ASMVisitorFactory(classNode);
                    dex2ASM.convertClass(dexClassNode, dex2ASMVisitorFactory);

                    classes.put(classNode.name, classNode);
                    apkArchive.recordDexClass(classNode.name, name);

                    updateProgress(dexFileNode.clzs.size());
                });
                //dex2ASM.convertDex(dexFileNode, dex2ASMVisitorFactory);
            } else if (name.equals("META-INF/MANIFEST.MF")) {
                processManifestFile(name, bytes, otherFiles);
            } else {
                processOtherFile(name, bytes, otherFiles);
            }

            handleMemoryWarning(startTime, bytes);
        } catch (Exception e) {
            e.printStackTrace();
            Main.INSTANCE.getLogger().err("Failed loading APK file: " + name);
        }
    }

    private void updateProgress(int num) {
        int progress = (int) (((float) loaded++ / (float) num) * 100f);
        publish(progress);
    }

    private void readJar(ZipFile jar, ZipEntry zipEntry, Map<String, ClassNode> classes,
            Map<String, byte[]> otherFiles) {
        String name = zipEntry.getName();
        try (InputStream jis = jar.getInputStream(zipEntry)) {
            byte[] bytes = IOUtils.toByteArray(jis);
            readJarBytes(name, bytes, classes, otherFiles);
        } catch (Exception e) {
            e.printStackTrace();
            Main.INSTANCE.getLogger().err("Failed loading file: " + name);
        }
    }

    private void readJarBytes(String name, byte[] bytes, Map<String, ClassNode> classes,
            Map<String, byte[]> otherFiles) {
        long startTime = System.currentTimeMillis();
        int progress = (int) (((float) loaded++ / (float) jarSize) * 100f);
        if (progress > 99)
            progress = 99; // cap for fallback stream sizing
        publish(progress);

        try {
            if (ClassUtils.isClassFileExt(name)) {
                processClassFile(name, bytes, classes, otherFiles);
            } else if (name.equals("META-INF/MANIFEST.MF")) {
                processManifestFile(name, bytes, otherFiles);
            } else {
                processOtherFile(name, bytes, otherFiles);
            }

            handleMemoryWarning(startTime, bytes);
        } catch (Exception e) {
            e.printStackTrace();
            Main.INSTANCE.getLogger().err("Failed loading file: " + name);
        }
    }

    private void processClassFile(String name, byte[] bytes, Map<String, ClassNode> classes, Map<String, byte[]> otherFiles) {
        synchronized (classes) {
            try {
                if (ClassUtils.isClassFileFormat(bytes)) {
                    final ClassNode cn = BytecodeUtils.getClassNodeFromBytes(bytes);
                    int rate = Main.INSTANCE.getJByteMod().getOptions().get("bad_class_check").getBoolean() ? FileUtils.isBadClass(cn) : 0;

                    if (rate <= 80) {
                        classes.put(cn.name, cn);
                    } else {
                        synchronized (otherFiles) {
                            otherFiles.put(name, bytes);
                        }
                    }
                }
            } catch (Exception ex) {
                synchronized (otherFiles) {
                    otherFiles.put(name, bytes);
                }
            }
        }
    }

    private void processManifestFile(String name, byte[] bytes, Map<String, byte[]> otherFiles) {
        ja.setJarManifest(bytes);
        synchronized (otherFiles) {
            otherFiles.put(name, bytes);
        }
    }

    private void processOtherFile(String name, byte[] bytes, Map<String, byte[]> otherFiles) {
        synchronized (otherFiles) {
            otherFiles.put(name, bytes);
        }
    }

    private void handleMemoryWarning(long startTime, byte[] bytes) {
        if (memoryWarning) {
            long timeDiff = System.currentTimeMillis() - startTime;
            double memoryUsage = Runtime.getRuntime().totalMemory() / (double) maxMem;

            if (timeDiff > 60 * 3 * 1000 && memoryUsage > 0.95) {
                 Main.INSTANCE.getLogger().logNotification(Main.INSTANCE.getJByteMod().getLanguageRes().getResource("memory_full"));
                publish(100);
                this.cancel(true);
            }
        }
    }


    @Override
    protected void process(List<Integer> chunks) {
        int i = chunks.get(chunks.size() - 1);
        if (jbm.getPluginManager() != null) {
            jbm.getPluginManager().loadProgress(file.getName(), i);
        }
        jpb.setValue(i);
        super.process(chunks);
    }

    @Override
    protected void done() {
        try {
            get();
            Main.INSTANCE.getJByteMod().setLastEditFile(file.getName());
            Main.INSTANCE.getLogger().log("Successfully loaded file!");
            jbm.refreshTree();
            if (jbm.getPluginManager() != null) {
                jbm.getPluginManager().fileLoaded(ja.getClasses());
            }
            Main.INSTANCE.getLogger().log("Tree refreshed.");
            Main.INSTANCE.getLogger().log("Loaded classes in " + (System.currentTimeMillis() - startTime) + "ms"
                    + ", bypassed " + othersFile + " files because I can't load them. (Include "
                    + junkClasses + " junk classes.)");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        } catch (ExecutionException exception) {
            Throwable cause = exception.getCause() == null ? exception : exception.getCause();
            new ErrorDisplay(cause);
        }
    }

    private static int dexMinApi(byte[] bytes) {
        if (bytes.length < 8 || bytes[0] != 'd' || bytes[1] != 'e' || bytes[2] != 'x'
                || bytes[3] != '\n' || bytes[7] != 0) {
            return 13;
        }
        return switch (new String(bytes, 4, 3, StandardCharsets.US_ASCII)) {
            case "037" -> 24;
            case "038" -> 26;
            case "039" -> 28;
            case "040" -> 30;
            case "041" -> 35;
            default -> 13;
        };
    }
}
