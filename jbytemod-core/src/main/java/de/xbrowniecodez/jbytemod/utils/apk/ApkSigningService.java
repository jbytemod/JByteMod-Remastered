package de.xbrowniecodez.jbytemod.utils.apk;

import com.android.apksig.ApkSigner;
import com.android.apksig.ApkVerifier;
import com.android.apksig.apk.ApkFormatException;
import de.xbrowniecodez.jbytemod.utils.Utils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;

/** Aligns, signs, and verifies rebuilt APKs. */
public final class ApkSigningService {
    private static final String DEBUG_KEYSTORE_FILE = "jbytemod-debug.p12";
    private static final String DEBUG_KEY_ALIAS = "jbytemoddebugkey";
    private static final char[] DEBUG_PASSWORD = "android".toCharArray();
    private static final Object DEBUG_KEYSTORE_LOCK = new Object();

    private ApkSigningService() {
    }

    public static void sign(Path input, Path output, int minSdkVersion, ApkSigningConfig config)
            throws IOException {
        try {
            SigningKey signingKey = loadSigningKey(config);

            ApkSigner.SignerConfig signerConfig = new ApkSigner.SignerConfig.Builder(
                    "JBYTEMOD", signingKey.privateKey(), signingKey.certificates()).build();
            new ApkSigner.Builder(List.of(signerConfig))
                    .setInputApk(input.toFile())
                    .setOutputApk(output.toFile())
                    .setMinSdkVersion(Math.max(1, minSdkVersion))
                    .setAlignmentPreserved(false)
                    .setV4SigningEnabled(false)
                    .setCreatedBy("JByteMod Remastered")
                    .build()
                    .sign();

            ApkVerifier.Result result = new ApkVerifier.Builder(output.toFile())
                    .setMinCheckedPlatformVersion(Math.max(1, minSdkVersion))
                    .build()
                    .verify();
            if (!result.isVerified()) {
                throw new IOException("APK signature verification failed: " + result.getErrors());
            }
        } catch (GeneralSecurityException | ApkFormatException | RuntimeException e) {
            throw new IOException("Could not sign rebuilt APK", e);
        }
    }

    static SigningKey loadSigningKey(ApkSigningConfig config)
            throws IOException, GeneralSecurityException {
        if (config.usesDebugKey()) {
            return loadDebugSigningKey();
        }
        char[] storePassword = config.getStorePassword();
        char[] keyPassword = config.getKeyPassword();
        try {
            return loadSigningKey(config.getKeystore(), config.getAlias(),
                    storePassword, keyPassword);
        } finally {
            clear(storePassword);
            clear(keyPassword);
        }
    }

    private static SigningKey loadDebugSigningKey() throws IOException, GeneralSecurityException {
        synchronized (DEBUG_KEYSTORE_LOCK) {
            Path keystorePath = Utils.getWorkingDirectory().toPath().resolve(DEBUG_KEYSTORE_FILE);
            if (Files.notExists(keystorePath)) {
                createDebugKeystore(keystorePath);
            }
            return loadSigningKey(keystorePath, DEBUG_KEY_ALIAS, DEBUG_PASSWORD, DEBUG_PASSWORD);
        }
    }

    private static SigningKey loadSigningKey(Path keystorePath, String requestedAlias,
            char[] storePassword, char[] keyPassword) throws IOException, GeneralSecurityException {
        if (!Files.isRegularFile(keystorePath)) {
            throw new IOException("Keystore does not exist: " + keystorePath);
        }

        KeyStore keyStore = KeyStore.getInstance("JKS");
        try (InputStream input = Files.newInputStream(keystorePath)) {
            keyStore.load(input, storePassword);
        }
        String alias = resolveAlias(keyStore, requestedAlias);
        char[] effectiveKeyPassword = keyPassword.length == 0 ? storePassword : keyPassword;
        if (!(keyStore.getKey(alias, effectiveKeyPassword) instanceof PrivateKey privateKey)) {
            throw new GeneralSecurityException("Keystore alias is not a private key: " + alias);
        }

        Certificate[] chain = keyStore.getCertificateChain(alias);
        if (chain == null || chain.length == 0) {
            throw new GeneralSecurityException("Keystore alias has no certificate chain: " + alias);
        }
        List<X509Certificate> certificates = new ArrayList<>(chain.length);
        for (Certificate certificate : chain) {
            if (!(certificate instanceof X509Certificate x509Certificate)) {
                throw new GeneralSecurityException("Keystore contains a non-X.509 certificate: " + alias);
            }
            certificates.add(x509Certificate);
        }
        return new SigningKey(privateKey, certificates);
    }

    private static String resolveAlias(KeyStore keyStore, String requestedAlias)
            throws GeneralSecurityException {
        if (requestedAlias != null && !requestedAlias.isBlank()) {
            if (!keyStore.isKeyEntry(requestedAlias)) {
                throw new GeneralSecurityException("Private-key alias not found: " + requestedAlias);
            }
            return requestedAlias;
        }

        String onlyKeyAlias = null;
        Enumeration<String> aliases = keyStore.aliases();
        while (aliases.hasMoreElements()) {
            String alias = aliases.nextElement();
            if (!keyStore.isKeyEntry(alias)) {
                continue;
            }
            if (onlyKeyAlias != null) {
                throw new GeneralSecurityException(
                        "Keystore contains multiple private keys; enter the alias to use");
            }
            onlyKeyAlias = alias;
        }
        if (onlyKeyAlias == null) {
            throw new GeneralSecurityException("Keystore contains no private keys");
        }
        return onlyKeyAlias;
    }

    private static void createDebugKeystore(Path keystorePath) throws IOException {
        Path parent = keystorePath.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Path keytool = Path.of(System.getProperty("java.home"), "bin",
                System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win")
                        ? "keytool.exe" : "keytool");
        if (!Files.isRegularFile(keytool)) {
            throw new IOException("Cannot create APK debug key because keytool was not found at " + keytool);
        }

        Process process = new ProcessBuilder(
                keytool.toString(),
                "-genkeypair",
                "-keystore", keystorePath.toAbsolutePath().toString(),
                "-storetype", "PKCS12",
                "-storepass", String.valueOf(DEBUG_PASSWORD),
                "-keypass", String.valueOf(DEBUG_PASSWORD),
                "-alias", DEBUG_KEY_ALIAS,
                "-keyalg", "RSA",
                "-keysize", "2048",
                "-validity", "10000",
                "-dname", "CN=JByteMod Debug,O=JByteMod Remastered,C=US",
                "-noprompt")
                .redirectErrorStream(true)
                .start();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (InputStream processOutput = process.getInputStream()) {
            processOutput.transferTo(output);
        }
        try {
            int exitCode = process.waitFor();
            if (exitCode != 0 || Files.notExists(keystorePath)) {
                throw new IOException("keytool failed to create the APK debug key (exit " + exitCode + "): "
                        + output.toString(StandardCharsets.UTF_8).trim());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while creating the APK debug key", e);
        }
    }

    private static void clear(char[] value) {
        if (value != null) {
            Arrays.fill(value, '\0');
        }
    }

    record SigningKey(PrivateKey privateKey, List<X509Certificate> certificates) {
    }
}
