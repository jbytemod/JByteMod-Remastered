package de.xbrowniecodez.jbytemod.utils.apk;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.CompilationMode;
import com.android.tools.r8.D8;
import com.android.tools.r8.D8Command;
import com.android.tools.r8.Diagnostic;
import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.OutputMode;
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
        save(archive, output, classWriterFlags, progress, ApkSigningConfig.debugKey());
    }

    public static void save(ApkArchive archive, Path output, int classWriterFlags, IntConsumer progress,
            ApkSigningConfig signingConfig) throws IOException {
        Path temporaryDirectory = Files.createTempDirectory("jbytemod-apk-");
        try {
            Path dexDirectory = temporaryDirectory.resolve("dex");
            Files.createDirectories(dexDirectory);

            List<Path> classArchives = writeClasses(
                    archive, temporaryDirectory, classWriterFlags, progress);
            compileDex(classArchives, dexDirectory, archive.getMinSdkVersion());
            progress.accept(55);

            Map<String, byte[]> entries = new HashMap<>();
            if (archive.getOutput() != null) {
                entries.putAll(archive.getOutput());
            }
            entries.keySet().removeIf(ApkCompiler::isGeneratedOrSignatureEntry);

            try (var dexFiles = Files.list(dexDirectory)) {
                for (Path dex : dexFiles
                        .filter(Files::isRegularFile)
                        .filter(path -> isDexEntry(path.getFileName().toString()))
                        .sorted(Comparator.comparing(path -> path.getFileName().toString(),
                                ApkCompiler::compareDexNames))
                        .toList()) {
                    entries.put(dex.getFileName().toString(), Files.readAllBytes(dex));
                }
            }
            if (!entries.containsKey("classes.dex")) {
                throw new IOException("Android compiler did not produce classes.dex");
            }

            Path unsignedApk = temporaryDirectory.resolve("unsigned.apk");
            Path signedApk = temporaryDirectory.resolve("signed.apk");
            writeApk(unsignedApk, entries, archive.getEntryMethods(), progress);
            progress.accept(92);
            ApkSigningService.sign(unsignedApk, signedApk, archive.getMinSdkVersion(), signingConfig);
            progress.accept(98);
            Files.move(signedApk, output, StandardCopyOption.REPLACE_EXISTING);
            progress.accept(100);
        } finally {
            signingConfig.close();
            deleteRecursively(temporaryDirectory);
        }
    }

    private static List<Path> writeClasses(ApkArchive archive, Path temporaryDirectory, int flags,
            IntConsumer progress) throws IOException {
        List<Map.Entry<String, ClassNode>> classes = new ArrayList<>(archive.getClasses().entrySet());
        classes.sort(Map.Entry.comparingByKey());

        String defaultDex = archive.getDexEntries().values().stream()
                .filter(ApkCompiler::isDexEntry)
                .max(ApkCompiler::compareDexNames)
                .orElse("classes.dex");
        Map<String, List<Map.Entry<String, ClassNode>>> groups = new LinkedHashMap<>();
        archive.getDexEntries().values().stream()
                .filter(ApkCompiler::isDexEntry)
                .distinct()
                .sorted(ApkCompiler::compareDexNames)
                .forEach(name -> groups.put(name, new ArrayList<>()));
        for (Map.Entry<String, ClassNode> entry : classes) {
            String dexName = archive.getDexEntries().getOrDefault(entry.getKey(), defaultDex);
            groups.computeIfAbsent(dexName, ignored -> new ArrayList<>()).add(entry);
        }
        groups.values().removeIf(List::isEmpty);

        List<Path> classArchives = new ArrayList<>();
        int written = 0;
        for (List<Map.Entry<String, ClassNode>> group : groups.values()) {
            Path classesJar = temporaryDirectory.resolve("classes-" + (classArchives.size() + 1) + ".jar");
            classArchives.add(classesJar);
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

    private static void compileDex(List<Path> classArchives, Path dexDirectory, int minSdkVersion)
            throws IOException {
        if (classArchives.isEmpty()) {
            throw new IOException("APK contains no classes to compile");
        }

        int outputIndex = 1;
        for (int groupIndex = 0; groupIndex < classArchives.size(); groupIndex++) {
            Path groupOutput = dexDirectory.resolve("group-" + (groupIndex + 1));
            Files.createDirectories(groupOutput);
            compileDexGroup(classArchives.get(groupIndex), groupOutput, minSdkVersion);

            try (var dexFiles = Files.list(groupOutput)) {
                for (Path dex : dexFiles
                        .filter(Files::isRegularFile)
                        .filter(path -> isDexEntry(path.getFileName().toString()))
                        .sorted(Comparator.comparing(path -> path.getFileName().toString(),
                                ApkCompiler::compareDexNames))
                        .toList()) {
                    Files.move(dex, dexDirectory.resolve(dexName(outputIndex++)));
                }
            }
        }
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

    private static void writeApk(Path output, Map<String, byte[]> entries,
            Map<String, Integer> originalMethods, IntConsumer progress) throws IOException {
        List<String> names = new ArrayList<>(entries.keySet());
        names.sort(String::compareTo);

        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(output))) {
            for (int index = 0; index < names.size(); index++) {
                String name = names.get(index);
                byte[] bytes = entries.get(name);
                ZipEntry entry = new ZipEntry(name);
                int method = originalMethods.getOrDefault(name,
                        originalMethods.getOrDefault("classes.dex", ZipEntry.DEFLATED));
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

    private static boolean isGeneratedOrSignatureEntry(String name) {
        if (isDexEntry(name)) {
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

    private static boolean isDexEntry(String name) {
        String normalized = name.replace('\\', '/');
        return normalized.indexOf('/') < 0
                && normalized.startsWith("classes")
                && normalized.endsWith(".dex");
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
