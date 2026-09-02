package de.xbrowniecodez.jbytemod.utils.apk;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.CompilationMode;
import com.android.tools.r8.D8;
import com.android.tools.r8.D8Command;
import com.android.tools.r8.Diagnostic;
import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.OutputMode;
import de.xbrowniecodez.jbytemod.archive.AabArchive;
import de.xbrowniecodez.jbytemod.archive.AndroidArchive;
import de.xbrowniecodez.jbytemod.archive.ApkArchive;
import de.xbrowniecodez.jbytemod.asm.CustomClassWriter;
import org.objectweb.asm.tree.ClassNode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.IntConsumer;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public final class ApkCompiler {
    private ApkCompiler() {
    }

    public static void save(ApkArchive archive, Path output, int classWriterFlags, IntConsumer progress)
            throws IOException {
        save((AndroidArchive) archive, output, classWriterFlags, progress);
    }

    public static void save(ApkArchive archive, Path output, int classWriterFlags, IntConsumer progress,
            ApkSigningConfig signingConfig) throws IOException {
        save((AndroidArchive) archive, output, classWriterFlags, progress, signingConfig);
    }

    public static void save(AndroidArchive archive, Path output, int classWriterFlags, IntConsumer progress)
            throws IOException {
        save(archive, output, classWriterFlags, progress, ApkSigningConfig.debugKey());
    }

    public static void save(AndroidArchive archive, Path output, int classWriterFlags, IntConsumer progress,
            ApkSigningConfig signingConfig) throws IOException {
        boolean appBundle = archive instanceof AabArchive;
        Path temporaryDirectory = Files.createTempDirectory(appBundle ? "jbytemod-aab-" : "jbytemod-apk-");
        try {
            Path dexDirectory = temporaryDirectory.resolve("dex");
            Files.createDirectories(dexDirectory);

            List<DexGroup> classArchives = writeClasses(
                    archive, temporaryDirectory, classWriterFlags, progress);
            Map<String, byte[]> compiledDex = compileDex(
                    classArchives, dexDirectory, archive.getMinSdkVersion(), appBundle);
            progress.accept(55);

            Map<String, byte[]> entries = new HashMap<>();
            if (archive.getOutput() != null) {
                entries.putAll(archive.getOutput());
            }
            entries.keySet().removeIf(name -> isGeneratedOrSignatureEntry(archive, name));
            entries.putAll(compiledDex);
            if (compiledDex.isEmpty()) {
                throw new IOException("Android compiler did not produce any DEX files");
            }

            String extension = appBundle ? ".aab" : ".apk";
            Path unsignedArchive = temporaryDirectory.resolve("unsigned" + extension);
            Path signedArchive = temporaryDirectory.resolve("signed" + extension);
            writeArchive(unsignedArchive, entries, archive.getEntryMethods(), archive, progress);
            progress.accept(92);
            if (appBundle) {
                AabSigningService.sign(unsignedArchive, signedArchive, signingConfig);
            } else {
                ApkSigningService.sign(unsignedArchive, signedArchive,
                        archive.getMinSdkVersion(), signingConfig);
            }
            progress.accept(98);
            Files.move(signedArchive, output, StandardCopyOption.REPLACE_EXISTING);
            progress.accept(100);
        } finally {
            signingConfig.close();
            deleteRecursively(temporaryDirectory);
        }
    }

    private static List<DexGroup> writeClasses(AndroidArchive archive, Path temporaryDirectory, int flags,
            IntConsumer progress) throws IOException {
        List<Map.Entry<String, ClassNode>> classes = new ArrayList<>(archive.getClasses().entrySet());
        classes.sort(Map.Entry.comparingByKey());

        List<String> originalDexEntries = archive.getDexEntries().values().stream()
                .filter(name -> isDexEntry(archive, name))
                .distinct()
                .sorted(ApkCompiler::compareDexEntries)
                .toList();
        String defaultDex = archive instanceof AabArchive
                ? originalDexEntries.stream().filter("base/dex/classes.dex"::equals).findFirst()
                        .orElseGet(() -> originalDexEntries.stream().findFirst()
                                .orElse("base/dex/classes.dex"))
                : originalDexEntries.stream().max(ApkCompiler::compareDexNames).orElse("classes.dex");
        Map<String, List<Map.Entry<String, ClassNode>>> groups = new LinkedHashMap<>();
        originalDexEntries.forEach(name -> groups.put(name, new ArrayList<>()));
        for (Map.Entry<String, ClassNode> entry : classes) {
            String dexName = archive.getDexEntries().getOrDefault(entry.getKey(), defaultDex);
            groups.computeIfAbsent(dexName, ignored -> new ArrayList<>()).add(entry);
        }
        groups.values().removeIf(List::isEmpty);

        List<DexGroup> classArchives = new ArrayList<>();
        int written = 0;
        for (Map.Entry<String, List<Map.Entry<String, ClassNode>>> dexGroup : groups.entrySet()) {
            List<Map.Entry<String, ClassNode>> group = dexGroup.getValue();
            Path classesJar = temporaryDirectory.resolve("classes-" + (classArchives.size() + 1) + ".jar");
            classArchives.add(new DexGroup(dexGroup.getKey(), classesJar));
            try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(classesJar))) {
                for (Map.Entry<String, ClassNode> entry : group) {
                    ClassNode classNode = entry.getValue();
                    CustomClassWriter writer = new CustomClassWriter(flags);
                    classNode.accept(writer);

                    output.putNextEntry(new ZipEntry(classNode.name + ".class"));
                    output.write(writer.toByteArray());
                    output.closeEntry();
                    written++;
                    progress.accept(classes.isEmpty() ? 45 : (int) ((written / (double) classes.size()) * 45d));
                }
            }
        }
        return classArchives;
    }

    private static Map<String, byte[]> compileDex(List<DexGroup> classArchives, Path dexDirectory,
            int minSdkVersion, boolean appBundle)
            throws IOException {
        if (classArchives.isEmpty()) {
            throw new IOException("Android archive contains no classes to compile");
        }

        Map<String, byte[]> compiledDex = new LinkedHashMap<>();
        Map<String, Integer> nextBundleDexIndex = new HashMap<>();
        int outputIndex = 1;
        for (int groupIndex = 0; groupIndex < classArchives.size(); groupIndex++) {
            DexGroup dexGroup = classArchives.get(groupIndex);
            Path groupOutput = dexDirectory.resolve("group-" + (groupIndex + 1));
            Files.createDirectories(groupOutput);
            compileDexGroup(dexGroup.classesJar(), groupOutput, minSdkVersion);

            try (var dexFiles = Files.list(groupOutput)) {
                for (Path dex : dexFiles
                        .filter(Files::isRegularFile)
                        .filter(path -> isDexFileName(path.getFileName().toString()))
                        .sorted(Comparator.comparing(path -> path.getFileName().toString(),
                                ApkCompiler::compareDexNames))
                        .toList()) {
                    String entryName;
                    if (appBundle) {
                        String directory = dexDirectory(dexGroup.dexEntry());
                        int bundleIndex = nextBundleDexIndex.getOrDefault(directory, 1);
                        entryName = directory + dexName(bundleIndex);
                        nextBundleDexIndex.put(directory, bundleIndex + 1);
                    } else {
                        entryName = dexName(outputIndex++);
                    }
                    compiledDex.put(entryName, Files.readAllBytes(dex));
                }
            }
        }
        return compiledDex;
    }

    private static void compileDexGroup(Path classesJar, Path output, int minSdkVersion)
            throws IOException {
        List<String> diagnostics = new ArrayList<>();
        DiagnosticsHandler handler = new DiagnosticsHandler() {
            @Override
            public void error(Diagnostic diagnostic) {
                diagnostics.add(diagnostic.getDiagnosticMessage());
            }
        };
        D8Command.Builder command = D8Command.builder(handler)
                .addProgramFiles(classesJar)
                .setOutput(output, OutputMode.DexIndexed)
                .setMode(CompilationMode.RELEASE)
                .setMinApiLevel(Math.max(13, minSdkVersion));
        try {
            D8.run(command.build());
        } catch (CompilationFailedException e) {
            String diagnostic = String.join(System.lineSeparator(), diagnostics).trim();
            throw new IOException("Android DEX compilation failed"
                    + (diagnostic.isEmpty() ? "" : ": " + diagnostic), e);
        }
    }

    private static void writeArchive(Path output, Map<String, byte[]> entries,
            Map<String, Integer> originalMethods, AndroidArchive archive,
            IntConsumer progress) throws IOException {
        List<String> names = new ArrayList<>(entries.keySet());
        names.sort(String::compareTo);

        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(output))) {
            for (int index = 0; index < names.size(); index++) {
                String name = names.get(index);
                byte[] bytes = entries.get(name);
                ZipEntry entry = new ZipEntry(name);
                int method = originalMethods.getOrDefault(name,
                        originalMethods.getOrDefault(defaultDexEntry(archive, name), ZipEntry.DEFLATED));
                if (method == ZipEntry.STORED) {
                    CRC32 crc = new CRC32();
                    crc.update(bytes);
                    entry.setMethod(ZipEntry.STORED);
                    entry.setSize(bytes.length);
                    entry.setCompressedSize(bytes.length);
                    entry.setCrc(crc.getValue());
                } else {
                    entry.setMethod(ZipEntry.DEFLATED);
                }
                zip.putNextEntry(entry);
                if (!name.endsWith("/") && !name.endsWith("\\")) {
                    zip.write(bytes);
                }
                zip.closeEntry();
                progress.accept(55 + (int) (((index + 1d) / names.size()) * 35d));
            }
        }
    }

    private static boolean isGeneratedOrSignatureEntry(AndroidArchive archive, String name) {
        if (isDexEntry(archive, name)) {
            return true;
        }
        String normalized = name.replace('\\', '/').toUpperCase(Locale.ROOT);
        if (!normalized.startsWith("META-INF/")) {
            return false;
        }
        return normalized.equals("META-INF/MANIFEST.MF")
                || normalized.endsWith(".SF")
                || normalized.endsWith(".RSA")
                || normalized.endsWith(".DSA")
                || normalized.endsWith(".EC");
    }

    private static boolean isDexEntry(AndroidArchive archive, String name) {
        String normalized = name.replace('\\', '/');
        int slash = normalized.lastIndexOf('/');
        String fileName = slash < 0 ? normalized : normalized.substring(slash + 1);
        if (!isDexFileName(fileName)) {
            return false;
        }
        if (archive instanceof AabArchive) {
            return slash > 0 && normalized.substring(0, slash).endsWith("/dex");
        }
        return slash < 0;
    }

    private static boolean isDexFileName(String name) {
        if (!name.startsWith("classes") || !name.endsWith(".dex")) {
            return false;
        }
        String index = name.substring("classes".length(), name.length() - ".dex".length());
        return index.isEmpty() || index.chars().allMatch(Character::isDigit);
    }

    private static int compareDexEntries(String left, String right) {
        int directoryComparison = dexDirectory(left).compareTo(dexDirectory(right));
        return directoryComparison != 0
                ? directoryComparison : compareDexNames(dexFileName(left), dexFileName(right));
    }

    private static String defaultDexEntry(AndroidArchive archive, String entryName) {
        return archive instanceof AabArchive ? dexDirectory(entryName) + "classes.dex" : "classes.dex";
    }

    private static String dexDirectory(String name) {
        String normalized = name.replace('\\', '/');
        int slash = normalized.lastIndexOf('/');
        return slash < 0 ? "" : normalized.substring(0, slash + 1);
    }

    private static String dexFileName(String name) {
        String normalized = name.replace('\\', '/');
        int slash = normalized.lastIndexOf('/');
        return slash < 0 ? normalized : normalized.substring(slash + 1);
    }

    private static int compareDexNames(String left, String right) {
        return Integer.compare(dexIndex(left), dexIndex(right));
    }

    private static String dexName(int index) {
        return index == 1 ? "classes.dex" : "classes" + index + ".dex";
    }

    private static int dexIndex(String name) {
        if ("classes.dex".equals(name)) {
            return 1;
        }
        try {
            return Integer.parseInt(name.substring("classes".length(), name.length() - ".dex".length()));
        } catch (RuntimeException ignored) {
            return Integer.MAX_VALUE;
        }
    }

    private record DexGroup(String dexEntry, Path classesJar) {
    }

    private static void deleteRecursively(Path directory) {
        if (directory == null || !Files.exists(directory)) {
            return;
        }
        try (var paths = Files.walk(directory)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                }
            });
        } catch (IOException ignored) {
        }
    }
}
