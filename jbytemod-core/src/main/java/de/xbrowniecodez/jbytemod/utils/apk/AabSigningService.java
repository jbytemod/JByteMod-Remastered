package de.xbrowniecodez.jbytemod.utils.apk;

import jdk.security.jarsigner.JarSigner;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.cert.CertificateFactory;
import java.util.Enumeration;
import java.util.Locale;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.zip.ZipFile;

/** Signs and verifies rebuilt Android App Bundles using standard JAR signatures. */
public final class AabSigningService {
    private AabSigningService() {
    }

    public static void sign(Path input, Path output, ApkSigningConfig config) throws IOException {
        try {
            ApkSigningService.SigningKey signingKey = ApkSigningService.loadSigningKey(config);
            var certificatePath = CertificateFactory.getInstance("X.509")
                    .generateCertPath(signingKey.certificates());
            JarSigner signer = new JarSigner.Builder(signingKey.privateKey(), certificatePath)
                    .signerName("JBYTEMOD")
                    .build();
            try (ZipFile archive = new ZipFile(input.toFile());
                    OutputStream signedOutput = Files.newOutputStream(output)) {
                signer.sign(archive, signedOutput);
            }
            verify(output);
        } catch (GeneralSecurityException | RuntimeException e) {
            throw new IOException("Could not sign rebuilt Android App Bundle", e);
        }
    }

    private static void verify(Path bundle) throws IOException {
        boolean signedContent = false;
        byte[] buffer = new byte[8192];
        try (JarFile jar = new JarFile(bundle.toFile(), true)) {
            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if (entry.isDirectory() || entry.getName().toUpperCase(Locale.ROOT).startsWith("META-INF/")) {
                    continue;
                }
                try (InputStream input = jar.getInputStream(entry)) {
                    while (input.read(buffer) != -1) {
                        // Reading the entire entry makes JarFile verify its signature.
                    }
                } catch (SecurityException exception) {
                    throw new IOException("Android App Bundle signature verification failed", exception);
                }
                if (entry.getCodeSigners() == null || entry.getCodeSigners().length == 0) {
                    throw new IOException("Android App Bundle contains an unsigned entry: " + entry.getName());
                }
                signedContent = true;
            }
        }
        if (!signedContent) {
            throw new IOException("Android App Bundle contains no signed content");
        }
    }
}
