package de.xbrowniecodez.jbytemod.ui;

import javax.swing.TransferHandler;
import java.awt.datatransfer.DataFlavor;
import java.io.File;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

public final class FileDropHandler extends TransferHandler {
    private final Consumer<File> fileConsumer;

    public FileDropHandler(Consumer<File> fileConsumer) {
        this.fileConsumer = fileConsumer;
    }

    @Override
    public boolean canImport(TransferSupport support) {
        support.setShowDropLocation(false);
        return support.isDrop() && support.isDataFlavorSupported(DataFlavor.javaFileListFlavor);
    }

    @Override
    public boolean importData(TransferSupport support) {
        if (!canImport(support)) {
            return false;
        }
        try {
            @SuppressWarnings("unchecked")
            List<File> files = (List<File>) support.getTransferable()
                    .getTransferData(DataFlavor.javaFileListFlavor);
            return files.stream()
                    .filter(FileDropHandler::isSupported)
                    .findFirst()
                    .map(file -> {
                        fileConsumer.accept(file);
                        return true;
                    })
                    .orElse(false);
        } catch (Exception ignored) {
            return false;
        }
    }

    private static boolean isSupported(File file) {
        String name = file.getName().toLowerCase(Locale.ROOT);
        return name.endsWith(".jar") || name.endsWith(".class")
                || name.endsWith(".apk") || name.endsWith(".aab");
    }
}
