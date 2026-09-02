package de.xbrowniecodez.jbytemod.utils.apk;

import java.nio.file.Path;
import java.util.Arrays;

/** Signing credentials for one APK or Android App Bundle save operation. */
public final class ApkSigningConfig implements AutoCloseable {
    private final Path keystore;
    private final String alias;
    private final char[] storePassword;
    private final char[] keyPassword;

    private ApkSigningConfig(Path keystore, String alias, char[] storePassword, char[] keyPassword) {
        this.keystore = keystore;
        this.alias = alias;
        this.storePassword = copy(storePassword);
        this.keyPassword = copy(keyPassword);
    }

    public static ApkSigningConfig debugKey() {
        return new ApkSigningConfig(null, null, null, null);
    }

    public static ApkSigningConfig customKey(Path keystore, String alias,
            char[] storePassword, char[] keyPassword) {
        if (keystore == null) {
            throw new IllegalArgumentException("Keystore is required");
        }
        return new ApkSigningConfig(keystore, alias == null ? "" : alias.trim(),
                storePassword, keyPassword);
    }

    public boolean usesDebugKey() {
        return keystore == null;
    }

    public Path getKeystore() {
        return keystore;
    }

    public String getAlias() {
        return alias;
    }

    public char[] getStorePassword() {
        return copy(storePassword);
    }

    public char[] getKeyPassword() {
        return copy(keyPassword);
    }

    @Override
    public void close() {
        clear(storePassword);
        clear(keyPassword);
    }

    private static char[] copy(char[] value) {
        return value == null ? new char[0] : value.clone();
    }

    private static void clear(char[] value) {
        if (value != null) {
            Arrays.fill(value, '\0');
        }
    }
}
