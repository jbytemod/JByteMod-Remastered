package de.xbrowniecodez.jbytemod.plugin;

import com.sun.tools.attach.VirtualMachine;
import com.sun.tools.attach.VirtualMachineDescriptor;
import de.xbrowniecodez.jbytemod.JByteMod;
import de.xbrowniecodez.jbytemod.Main;
import de.xbrowniecodez.jbytemod.archive.ApkArchive;
import de.xbrowniecodez.jbytemod.decompiler.ASMifierDecompiler;
import de.xbrowniecodez.jbytemod.decompiler.JDCoreDecompiler;
import de.xbrowniecodez.jbytemod.decompiler.VineflowerDecompiler;
import de.xbrowniecodez.jbytemod.utils.BytecodeUtils;
import de.xbrowniecodez.jbytemod.utils.apk.ApkSigningConfig;
import de.xbrowniecodez.jbytemod.utils.attach.RemoteJarArchive;
import de.xbrowniecodez.jbytemod.utils.attach.RuntimeJarArchive;
import de.xbrowniecodez.jbytemod.utils.task.AttachTask;
import de.xbrowniecodez.jbytemod.utils.task.LoadTask;
import me.grax.jbytemod.JarArchive;
import me.grax.jbytemod.decompiler.CFRDecompiler;
import me.grax.jbytemod.decompiler.Decompiler;
import me.grax.jbytemod.decompiler.KoffeeDecompiler;
import me.grax.jbytemod.decompiler.ProcyonDecompiler;
import me.grax.jbytemod.utils.task.SaveTask;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import javax.swing.JMenuBar;
import javax.swing.JTree;
import javax.swing.SwingUtilities;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicReference;

public final class JByteModPluginContext implements PluginContext {
    private final JByteMod jByteMod;

    public JByteModPluginContext(JByteMod jByteMod) {
        this.jByteMod = Objects.requireNonNull(jByteMod, "jByteMod");
    }

    @Override
    public String getApplicationVersion() {
        return jByteMod.getVersion().toString();
    }

    @Override
    public ArchiveInfo getArchiveInfo() {
        JarArchive archive = jByteMod.getJarArchive();
        if (archive == null || archive.getClasses() == null) {
            return new ArchiveInfo(ArchiveType.NONE, 0, jByteMod.getLastEditFile());
        }

        ArchiveType type = archive instanceof RemoteJarArchive ? ArchiveType.REMOTE_JVM
                : archive instanceof RuntimeJarArchive ? ArchiveType.CURRENT_JVM
                : archive instanceof ApkArchive ? ArchiveType.APK
                : archive.isSingleEntry() ? ArchiveType.CLASS : ArchiveType.ARCHIVE;
        int resourceCount = (type == ArchiveType.ARCHIVE || type == ArchiveType.APK)
                && archive.getOutput() != null
                ? (int) archive.getOutput().keySet().stream().filter(path -> !isClassResourcePath(path)).count()
                : 0;
        return new ArchiveInfo(type, resourceCount, jByteMod.getLastEditFile());
    }

    @Override
    public Map<String, ClassNode> getCurrentFile() {
        JarArchive archive = jByteMod.getJarArchive();
        return archive == null || archive.getClasses() == null
                ? Collections.emptyMap() : archive.getClasses();
    }

    @Override
    public List<String> getResourceNames() {
        JarArchive archive = editableResourceArchive();
        synchronized (archive) {
            return archive.getOutput().keySet().stream()
                    .filter(path -> !isClassResourcePath(path))
                    .sorted()
                    .toList();
        }
    }

    @Override
    public byte[] getResource(String path) {
        JarArchive archive = editableResourceArchive();
        String normalizedPath = normalizeResourcePath(path);
        synchronized (archive) {
            byte[] bytes = archive.getOutput().get(normalizedPath);
            return bytes == null ? null : Arrays.copyOf(bytes, bytes.length);
        }
    }

    @Override
    public void putResource(String path, byte[] bytes) {
        JarArchive archive = editableResourceArchive();
        String normalizedPath = normalizeResourcePath(path);
        byte[] copy = Arrays.copyOf(Objects.requireNonNull(bytes, "bytes"), bytes.length);
        synchronized (archive) {
            archive.getOutput().put(normalizedPath, copy);
            if ("META-INF/MANIFEST.MF".equalsIgnoreCase(normalizedPath)) {
                archive.setJarManifest(Arrays.copyOf(copy, copy.length));
            }
        }
    }

    @Override
    public boolean removeResource(String path) {
        JarArchive archive = editableResourceArchive();
        String normalizedPath = normalizeResourcePath(path);
        synchronized (archive) {
            boolean removed = archive.getOutput().remove(normalizedPath) != null;
            if (removed && "META-INF/MANIFEST.MF".equalsIgnoreCase(normalizedPath)) {
                archive.setJarManifest(null);
            }
            return removed;
        }
    }

    @Override
    public byte[] getClassBytes(ClassNode classNode) {
        return BytecodeUtils.getClassNodeBytes(classNode);
    }

    @Override
    public ClassNode readClass(byte[] bytes) {
        return BytecodeUtils.getClassNodeFromBytes(bytes);
    }

    @Override
    public List<String> getDecompilerIds() {
        return List.of("cfr", "procyon", "vineflower", "jd-core", "koffee", "asmifier");
    }

    @Override
    public void openFile(String path) throws Exception {
        Path filePath = Path.of(Objects.requireNonNull(path, "path")).toAbsolutePath().normalize();
        if (!Files.isRegularFile(filePath)) {
            throw new IllegalArgumentException("File does not exist: " + filePath);
        }
        if (!Files.isReadable(filePath)) {
            throw new IllegalArgumentException("File is not readable: " + filePath);
        }

        String fileName = filePath.getFileName().toString().toLowerCase(Locale.ROOT);
        if (!fileName.endsWith(".jar") && !fileName.endsWith(".class") && !fileName.endsWith(".apk")) {
            throw new IllegalArgumentException("Unsupported file type: " + filePath.getFileName());
        }

        AtomicReference<LoadTask> task = new AtomicReference<>();
        AtomicReference<Exception> failure = new AtomicReference<>();
        File file = filePath.toFile();
        runOnEdt(() -> {
            try {
                task.set(jByteMod.loadFileChecked(file));
            } catch (Exception exception) {
                failure.set(exception);
            }
        });
        if (failure.get() != null) {
            throw failure.get();
        }
        if (task.get() != null) {
            try {
                task.get().get();
            } catch (ExecutionException exception) {
                Throwable cause = exception.getCause() == null ? exception : exception.getCause();
                throw new IOException("Could not open " + filePath, cause);
            }
            runOnEdt(() -> jByteMod.setLastEditFile(file.getName()));
        }
    }

    @Override
    public String saveFile(String path) throws Exception {
        try (ApkSigningOptions signingOptions = ApkSigningOptions.debugKey()) {
            return saveFile(path, signingOptions);
        }
    }

    @Override
    public String saveFile(String path, ApkSigningOptions signingOptions) throws Exception {
        JarArchive archive = jByteMod.getJarArchive();
        if (archive == null || archive.getClasses() == null) {
            throw new IllegalStateException("No archive is open in JByteMod");
        }
        Objects.requireNonNull(signingOptions, "signingOptions");
        if (!(archive instanceof ApkArchive) && !signingOptions.usesDebugKey()) {
            throw new IllegalArgumentException("Custom signing options can only be used when saving an APK");
        }

        Path outputPath = Path.of(Objects.requireNonNull(path, "path")).toAbsolutePath().normalize();
        if (Files.isDirectory(outputPath)) {
            throw new IllegalArgumentException("Output path is a directory: " + outputPath);
        }
        String extension = archive.isSingleEntry() ? ".class"
                : archive instanceof ApkArchive ? ".apk" : ".jar";
        if (!outputPath.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(extension)) {
            outputPath = Path.of(outputPath + extension);
        }
        Path parent = outputPath.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        AtomicReference<SaveTask> task = new AtomicReference<>();
        Path finalOutputPath = outputPath;
        char[] storePassword = signingOptions.getStorePassword();
        char[] keyPassword = signingOptions.getKeyPassword();
        ApkSigningConfig signingConfig;
        try {
            signingConfig = signingOptions.usesDebugKey()
                    ? ApkSigningConfig.debugKey()
                    : ApkSigningConfig.customKey(signingOptions.getKeystore(), signingOptions.getAlias(),
                            storePassword, keyPassword);
        } finally {
            Arrays.fill(storePassword, '\0');
            Arrays.fill(keyPassword, '\0');
        }
        runOnEdt(() -> task.set(jByteMod.saveFileChecked(finalOutputPath.toFile(), signingConfig)));
        try {
            task.get().get();
        } catch (ExecutionException exception) {
            Throwable cause = exception.getCause() == null ? exception : exception.getCause();
            throw new IOException("Could not save " + outputPath, cause);
        }
        return outputPath.toString();
    }

    @Override
    public List<JvmProcess> listJvmProcesses() {
        String currentPid = Long.toString(ProcessHandle.current().pid());
        List<JvmProcess> processes = new ArrayList<>();
        for (VirtualMachineDescriptor descriptor : VirtualMachine.list()) {
            if (!descriptor.id().equals(currentPid)) {
                processes.add(new JvmProcess(descriptor.id(), descriptor.displayName()));
            }
        }
        return processes;
    }

    @Override
    public void attachToJvm(String pid) throws Exception {
        String normalizedPid = Objects.requireNonNull(pid, "pid").trim();
        if (normalizedPid.isEmpty()) {
            throw new IllegalArgumentException("PID must not be empty");
        }
        if (normalizedPid.equals(Long.toString(ProcessHandle.current().pid()))) {
            throw new IllegalArgumentException("Cannot attach JByteMod to itself");
        }

        RemoteJarArchive archive = AttachTask.attach(VirtualMachine.attach(normalizedPid), this::setProgress);
        boolean connected = false;
        try {
            runOnEdt(() -> jByteMod.connectToAgent(archive));
            connected = true;
        } finally {
            if (!connected) {
                archive.close();
            }
        }
    }

    @Override
    public void refreshAttachedJvm() throws Exception {
        RemoteJarArchive archive = attachedArchive();
        setProgress(0);
        archive.refresh(value -> setProgress(Math.min(value, 99)));
        Decompiler.clearCache();
        runOnEdt(jByteMod::refreshTree);
        setProgress(100);
    }

    @Override
    public int applyChangesToAttachedJvm() throws Exception {
        RemoteJarArchive archive = attachedArchive();
        int writerFlags = jByteMod.getOptions().get("compute_maxs").getBoolean()
                ? ClassWriter.COMPUTE_MAXS : 0;
        Map<String, byte[]> replacements = new HashMap<>();
        synchronized (archive) {
            Map<String, byte[]> original = archive.getOutput();
            int processed = 0;
            int size = archive.getClasses().size();
            setProgress(0);
            for (Map.Entry<String, ClassNode> entry : archive.getClasses().entrySet()) {
                byte[] bytes = BytecodeUtils.getClassNodeBytes(entry.getValue(), writerFlags);
                if (!Arrays.equals(bytes, original.get(entry.getKey()))) {
                    replacements.put(entry.getKey(), bytes);
                }
                processed++;
                setProgress(size == 0 ? 80 : processed * 80 / size);
            }
            if (!replacements.isEmpty()) {
                archive.redefine(replacements);
                original.putAll(replacements);
            }
        }
        setProgress(100);
        Main.INSTANCE.getLogger().log("Successfully retransformed " + replacements.size() + " classes");
        return replacements.size();
    }

    @Override
    public void setAttachedJvmFrozen(boolean frozen) throws Exception {
        attachedArchive().setFrozen(frozen);
        runOnEdt(() -> jByteMod.getToolBar().showAttachedJvmFrozen(frozen));
    }

    @Override
    public void detachFromAttachedJvm() throws Exception {
        RemoteJarArchive archive = attachedArchive();
        archive.close();
        runOnEdt(() -> jByteMod.completeAttachedJvmDetachment(archive));
    }

    @Override
    public void terminateAttachedJvm() throws Exception {
        RemoteJarArchive archive = attachedArchive();
        archive.terminate();
        runOnEdt(() -> jByteMod.completeAttachedJvmTermination(archive));
    }

    @Override
    public JvmRuntimeInfo getAttachedJvmRuntimeInfo() throws Exception {
        return attachedArchive().getRuntimeInfo();
    }

    @Override
    public List<JvmThreadInfo> getAttachedJvmThreads(int maxStackDepth) throws Exception {
        return attachedArchive().getThreads(maxStackDepth);
    }

    @Override
    public List<JvmClassLoaderInfo> getAttachedJvmClassLoaders() throws Exception {
        return attachedArchive().getClassLoaders();
    }

    @Override
    public Map<String, String> getAttachedJvmSystemProperties() throws Exception {
        return attachedArchive().getSystemProperties();
    }

    @Override
    public byte[] invokeAttachedJvmAgentExtension(String extensionId, String entryClassName,
                                                   Map<String, byte[]> classFiles, byte[] request) throws Exception {
        return attachedArchive().invokeAgentExtension(extensionId, entryClassName, classFiles, request);
    }

    @Override
    public String decompile(ClassNode classNode, MethodNode method, String decompilerId) {
        String id = Objects.requireNonNull(decompilerId, "decompilerId").trim().toLowerCase(Locale.ROOT);
        Decompiler decompiler = switch (id) {
            case "cfr" -> new CFRDecompiler(jByteMod, null);
            case "procyon" -> new ProcyonDecompiler(jByteMod, null);
            case "vineflower" -> new VineflowerDecompiler(jByteMod, null);
            case "jd-core", "jdcore" -> new JDCoreDecompiler(jByteMod, null);
            case "koffee" -> new KoffeeDecompiler(jByteMod, null);
            case "asmifier" -> new ASMifierDecompiler(jByteMod, null);
            default -> throw new IllegalArgumentException("Unknown decompiler: " + decompilerId);
        };
        decompiler.setNode(classNode, method);
        synchronized (Decompiler.class) {
            return decompiler.decompile(BytecodeUtils.getClassNodeBytes(classNode), method);
        }
    }

    @Override
    public void selectClass(ClassNode classNode) {
        runOnEdt(() -> jByteMod.treeSelection(classNode));
    }

    @Override
    public void selectMethod(ClassNode classNode, MethodNode method) {
        runOnEdt(() -> jByteMod.treeSelection(classNode, method));
    }

    @Override
    public void methodModified(ClassNode classNode, MethodNode method) {
        Decompiler.clearCache();
        if (jByteMod.getJarTree() == null) {
            return;
        }
        runOnEdt(() -> {
            ClassNode selectedClass = jByteMod.getCurrentNode();
            MethodNode selectedMethod = jByteMod.getCurrentMethod();
            if (selectedClass != null && selectedMethod != null
                    && selectedClass.name.equals(classNode.name)
                    && selectedMethod.name.equals(method.name)
                    && selectedMethod.desc.equals(method.desc)) {
                ClassNode currentClass = getCurrentFile().get(classNode.name);
                MethodNode currentMethod = findMethod(currentClass, method.name, method.desc);
                if (currentMethod != null) {
                    jByteMod.selectMethod(currentClass, currentMethod);
                    return;
                }
            }
            if (jByteMod.getJarTree() != null) {
                jByteMod.getJarTree().repaint();
            }
        });
    }

    @Override
    public void replaceClass(ClassNode previous, ClassNode replacement) {
        Map<String, ClassNode> classes = getCurrentFile();
        if (classes.isEmpty() && getArchiveInfo().type() == ArchiveType.NONE) {
            throw new IllegalStateException("No archive is open in JByteMod");
        }

        classes.remove(previous.name);
        classes.put(replacement.name, replacement);
        Decompiler.clearCache();
        if (jByteMod.getJarTree() == null) {
            return;
        }

        boolean wasSelected = jByteMod.getCurrentNode() == previous;
        MethodNode selectedMethod = jByteMod.getCurrentMethod();
        MethodNode replacementMethod = selectedMethod == null ? null : replacement.methods.stream()
                .filter(method -> method.name.equals(selectedMethod.name) && method.desc.equals(selectedMethod.desc))
                .findFirst().orElse(null);
        SwingUtilities.invokeLater(() -> {
            jByteMod.refreshTree();
            if (!wasSelected) {
                return;
            }
            if (replacementMethod == null) {
                jByteMod.treeSelection(replacement);
            } else {
                jByteMod.treeSelection(replacement, replacementMethod);
            }
        });
    }

    @Override
    public void log(String message) {
        Main.INSTANCE.getLogger().log(message);
    }

    @Override
    public void logError(String message, Throwable error) {
        Main.INSTANCE.getLogger().err(message);
        if (error != null) {
            error.printStackTrace();
        }
    }

    @Override
    public void updateTree() {
        ClassNode selectedClass = jByteMod.getCurrentNode();
        MethodNode selectedMethod = jByteMod.getCurrentMethod();
        String className = selectedClass == null ? null : selectedClass.name;
        String methodName = selectedMethod == null ? null : selectedMethod.name;
        String methodDescriptor = selectedMethod == null ? null : selectedMethod.desc;

        runOnEdt(() -> {
            jByteMod.refreshTree();
            if (className == null) {
                return;
            }
            ClassNode currentClass = getCurrentFile().get(className);
            if (currentClass == null) {
                return;
            }
            if (methodName == null) {
                jByteMod.selectClass(currentClass);
                jByteMod.treeSelection(currentClass);
                return;
            }
            MethodNode currentMethod = findMethod(currentClass, methodName, methodDescriptor);
            if (currentMethod != null) {
                jByteMod.selectMethod(currentClass, currentMethod);
                jByteMod.treeSelection(currentClass, currentMethod);
            }
        });
    }

    @Override
    public JMenuBar getMenu() {
        return jByteMod.getMyMenuBar();
    }

    @Override
    public JTree getTree() {
        return jByteMod.getJarTree();
    }

    @Override
    public ClassNode getSelectedNode() {
        return jByteMod.getCurrentNode();
    }

    @Override
    public MethodNode getSelectedMethod() {
        return jByteMod.getCurrentMethod();
    }

    private RemoteJarArchive attachedArchive() {
        if (jByteMod.getJarArchive() instanceof RemoteJarArchive archive) {
            return archive;
        }
        throw new IllegalStateException("JByteMod is not attached to a remote JVM");
    }

    private JarArchive editableResourceArchive() {
        JarArchive archive = jByteMod.getJarArchive();
        if (archive == null || archive.getClasses() == null) {
            throw new IllegalStateException("No archive is open in JByteMod");
        }
        if (archive instanceof RemoteJarArchive || archive instanceof RuntimeJarArchive || archive.isSingleEntry()) {
            throw new IllegalStateException("The active view does not contain editable archive resources");
        }
        if (archive.getOutput() == null) {
            archive.setOutput(new HashMap<>());
        }
        return archive;
    }

    private static String normalizeResourcePath(String path) {
        String normalized = Objects.requireNonNull(path, "path").replace('\\', '/');
        if (normalized.isBlank() || normalized.startsWith("/") || normalized.endsWith("/")
                || normalized.indexOf('\0') >= 0
                || (normalized.length() > 1 && Character.isLetter(normalized.charAt(0))
                && normalized.charAt(1) == ':')) {
            throw new IllegalArgumentException("Invalid archive resource path: " + path);
        }
        for (String segment : normalized.split("/", -1)) {
            if (segment.isEmpty() || ".".equals(segment) || "..".equals(segment)) {
                throw new IllegalArgumentException("Invalid archive resource path: " + path);
            }
        }
        if (isClassResourcePath(normalized)) {
            throw new IllegalArgumentException("Class entries must be edited through the class API");
        }
        return normalized;
    }

    private static boolean isClassResourcePath(String path) {
        return path.toLowerCase(Locale.ROOT).endsWith(".class");
    }

    private static MethodNode findMethod(ClassNode classNode, String name, String descriptor) {
        if (classNode == null) {
            return null;
        }
        for (MethodNode method : classNode.methods) {
            if (method.name.equals(name) && method.desc.equals(descriptor)) {
                return method;
            }
        }
        return null;
    }

    private void setProgress(int value) {
        SwingUtilities.invokeLater(() -> jByteMod.getPageEndPanel().setValue(value));
    }

    private static void runOnEdt(Runnable runnable) {
        if (SwingUtilities.isEventDispatchThread()) {
            runnable.run();
            return;
        }
        try {
            SwingUtilities.invokeAndWait(runnable);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while updating the JByteMod UI", exception);
        } catch (InvocationTargetException exception) {
            throw new IllegalStateException("Could not update the JByteMod UI", exception.getCause());
        }
    }
}
