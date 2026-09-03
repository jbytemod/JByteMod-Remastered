package de.xbrowniecodez.jbytemod.ui;

import de.xbrowniecodez.jbytemod.JByteMod;
import de.xbrowniecodez.jbytemod.Main;
import de.xbrowniecodez.jbytemod.utils.apk.AndroidBinaryXmlDecoder;
import me.grax.jbytemod.JarArchive;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;
import org.fife.ui.rsyntaxtextarea.Theme;
import org.fife.ui.rtextarea.RTextScrollPane;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.KeyStroke;
import javax.swing.Scrollable;
import javax.swing.JViewport;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Locale;

public final class ResourceEditorPanel extends JPanel {
    private static final int MAX_TEXT_SIZE = 5 * 1024 * 1024;
    private static final int MAX_IMAGE_FILE_SIZE = 50 * 1024 * 1024;
    private static final long MAX_IMAGE_PIXELS = 40_000_000L;
    private static final int MAX_IMAGE_DIMENSION = 32_768;
    private static final String TEXT_CARD = "text";
    private static final String IMAGE_CARD = "image";
    private static final byte[] UTF_8_BOM = {(byte) 0xef, (byte) 0xbb, (byte) 0xbf};

    private final JByteMod jByteMod;
    private final RSyntaxTextArea editor = new RSyntaxTextArea();
    private final CardLayout contentLayout = new CardLayout();
    private final JPanel contentPanel = new JPanel(contentLayout);
    private final ImagePreviewPanel imagePreview = new ImagePreviewPanel();
    private final JLabel pathLabel = new JLabel("No resource selected");
    private final JLabel statusLabel = new JLabel(" ");
    private final JButton saveButton = new JButton("Save resource");
    private String resourcePath;
    private byte[] originalBinaryXml;
    private boolean compiledAndroidXml;
    private boolean preserveUtf8Bom;
    private boolean loading;
    private boolean dirty;

    public ResourceEditorPanel(JByteMod jByteMod) {
        this.jByteMod = jByteMod;
        setLayout(new BorderLayout());

        JPanel header = new JPanel(new BorderLayout(8, 0));
        header.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));
        header.add(pathLabel, BorderLayout.CENTER);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        actions.add(statusLabel);
        actions.add(saveButton);
        header.add(actions, BorderLayout.EAST);
        add(header, BorderLayout.NORTH);

        editor.setAntiAliasingEnabled(true);
        editor.setCodeFoldingEnabled(true);
        editor.setMarkOccurrences(true);
        editor.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        editor.setEditable(false);
        applyTheme();
        RTextScrollPane scrollPane = new RTextScrollPane(editor);
        scrollPane.setLineNumbersEnabled(true);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        contentPanel.add(scrollPane, TEXT_CARD);
        JScrollPane imageScrollPane = new JScrollPane(imagePreview);
        imageScrollPane.getHorizontalScrollBar().setUnitIncrement(16);
        imageScrollPane.getVerticalScrollBar().setUnitIncrement(16);
        contentPanel.add(imageScrollPane, IMAGE_CARD);
        add(contentPanel, BorderLayout.CENTER);

        saveButton.setEnabled(false);
        saveButton.addActionListener(event -> saveResource());
        editor.getInputMap().put(KeyStroke.getKeyStroke(
                KeyEvent.VK_S, InputEvent.CTRL_DOWN_MASK), "save-resource");
        editor.getActionMap().put("save-resource", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent event) {
                if (saveButton.isEnabled()) saveResource();
            }
        });
        editor.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent event) { changed(); }
            @Override public void removeUpdate(DocumentEvent event) { changed(); }
            @Override public void changedUpdate(DocumentEvent event) { changed(); }
        });
    }

    public boolean openResource(String path) {
        if (!confirmResourceChange(path)) {
            return false;
        }
        JarArchive archive = jByteMod.getJarArchive();
        byte[] bytes = archive == null || archive.getOutput() == null
                ? null : archive.getOutput().get(path);
        resourcePath = path;
        originalBinaryXml = null;
        compiledAndroidXml = false;
        pathLabel.setText(path);
        preserveUtf8Bom = startsWith(bytes, UTF_8_BOM);
        editor.setSyntaxEditingStyle(syntaxStyle(path));

        if (bytes == null) {
            showUnavailable("Resource no longer exists in the archive.");
            return true;
        }
        if (isImagePath(path)) {
            showImage(bytes);
            return true;
        }
        if (bytes.length > MAX_TEXT_SIZE) {
            showUnavailable("Preview disabled: resource is larger than 5 MB (" + formatSize(bytes.length) + ").");
            return true;
        }

        byte[] content = preserveUtf8Bom ? Arrays.copyOfRange(bytes, UTF_8_BOM.length, bytes.length) : bytes;
        String text;
        try {
            text = decodeUtf8(content);
        } catch (CharacterCodingException exception) {
            if (!isXmlPath(path) || !AndroidBinaryXmlDecoder.isBinaryXml(content)) {
                showUnavailable("Preview disabled: resource is not valid UTF-8 text.");
                return true;
            }
            try {
                text = AndroidBinaryXmlDecoder.decode(content);
                compiledAndroidXml = true;
                originalBinaryXml = content.clone();
                preserveUtf8Bom = false;
            } catch (IOException | RuntimeException decodeException) {
                Main.INSTANCE.getLogger().warn("Could not decode Android binary XML " + path + ": "
                        + decodeException.getMessage());
                showUnavailable("Preview unavailable: Android binary XML could not be decoded.");
                return true;
            }
        }
        if (!isProbablyText(text)) {
            showUnavailable("Preview disabled: resource appears to be binary data.");
            return true;
        }

        loading = true;
        try {
            imagePreview.setImage(null);
            contentLayout.show(contentPanel, TEXT_CARD);
            editor.setText(text);
            editor.discardAllEdits();
            editor.setCaretPosition(0);
        } finally {
            loading = false;
        }
        dirty = false;
        editor.setEditable(true);
        saveButton.setEnabled(false);
        updateStatus(compiledAndroidXml
                ? "Decoded Android XML - Editable - " + formatSize(bytes.length)
                : formatSize(bytes.length));
        return true;
    }

    private boolean confirmResourceChange(String nextPath) {
        if (!dirty || resourcePath == null || resourcePath.equals(nextPath)) {
            return true;
        }
        Object[] options = {"Save", "Discard", "Cancel"};
        int choice = JOptionPane.showOptionDialog(this,
                "Save changes to " + resourcePath + "?",
                "Unsaved resource",
                JOptionPane.YES_NO_CANCEL_OPTION,
                JOptionPane.WARNING_MESSAGE,
                null,
                options,
                options[0]);
        if (choice == JOptionPane.YES_OPTION) {
            saveResource();
            return !dirty;
        }
        return choice == JOptionPane.NO_OPTION;
    }

    public void clearResource() {
        resourcePath = null;
        originalBinaryXml = null;
        compiledAndroidXml = false;
        preserveUtf8Bom = false;
        dirty = false;
        loading = true;
        try {
            imagePreview.setImage(null);
            contentLayout.show(contentPanel, TEXT_CARD);
            editor.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_NONE);
            editor.setText("");
            editor.discardAllEdits();
        } finally {
            loading = false;
        }
        editor.setEditable(false);
        pathLabel.setText("No resource selected");
        statusLabel.setText(" ");
        saveButton.setEnabled(false);
    }

    public void applyTheme() {
        String themePath = jByteMod.getOptions().get("use_dark_theme").getBoolean()
                ? "/resources/de/brownie/rsyntaxtextarea/themes/custom.xml"
                : "/org/fife/ui/rsyntaxtextarea/themes/idea.xml";
        try (InputStream input = getClass().getResourceAsStream(themePath)) {
            if (input != null) Theme.load(input).apply(editor);
        } catch (IOException exception) {
            Main.INSTANCE.getLogger().warn("Could not apply the resource editor theme: " + exception.getMessage());
        }
    }

    private void changed() {
        if (loading || !editor.isEditable()) return;
        dirty = true;
        saveButton.setEnabled(true);
        updateStatus("Modified");
    }

    private void saveResource() {
        if (!dirty || resourcePath == null || !editor.isEditable()) return;
        JarArchive archive = jByteMod.getJarArchive();
        if (archive == null || archive.getOutput() == null) {
            JOptionPane.showMessageDialog(this, "The archive is no longer open.",
                    "Cannot save resource", JOptionPane.ERROR_MESSAGE);
            return;
        }

        byte[] output;
        try {
            if (compiledAndroidXml) {
                output = AndroidBinaryXmlDecoder.encode(editor.getText(), originalBinaryXml);
            } else {
                byte[] text = editor.getText().getBytes(StandardCharsets.UTF_8);
                if (preserveUtf8Bom) {
                    output = Arrays.copyOf(UTF_8_BOM, UTF_8_BOM.length + text.length);
                    System.arraycopy(text, 0, output, UTF_8_BOM.length, text.length);
                } else {
                    output = text;
                }
            }
        } catch (IOException | RuntimeException exception) {
            Main.INSTANCE.getLogger().warn("Could not encode Android binary XML " + resourcePath + ": "
                    + exception.getMessage());
            JOptionPane.showMessageDialog(this,
                    "The edited Android XML could not be encoded:\n" + exception.getMessage(),
                    "Cannot save resource", JOptionPane.ERROR_MESSAGE);
            return;
        }
        synchronized (archive) {
            archive.getOutput().put(resourcePath, output);
            if ("META-INF/MANIFEST.MF".equalsIgnoreCase(resourcePath)) {
                archive.setJarManifest(output.clone());
            }
        }
        editor.discardAllEdits();
        dirty = false;
        saveButton.setEnabled(false);
        if (compiledAndroidXml) originalBinaryXml = output.clone();
        updateStatus((compiledAndroidXml ? "Saved as Android binary XML - " : "Saved - ")
                + formatSize(output.length));
    }

    private void showUnavailable(String message) {
        loading = true;
        try {
            imagePreview.setImage(null);
            contentLayout.show(contentPanel, TEXT_CARD);
            editor.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_NONE);
            editor.setText(message);
            editor.discardAllEdits();
            editor.setCaretPosition(0);
        } finally {
            loading = false;
        }
        dirty = false;
        editor.setEditable(false);
        saveButton.setEnabled(false);
        updateStatus("Read-only");
    }

    private void showImage(byte[] bytes) {
        if (bytes.length > MAX_IMAGE_FILE_SIZE) {
            showUnavailable("Image preview disabled: resource is larger than 50 MB ("
                    + formatSize(bytes.length) + ").");
            return;
        }
        try {
            DecodedImage decoded = decodeImage(bytes);
            loading = true;
            try {
                editor.setEditable(false);
                imagePreview.setImage(decoded.image());
                contentLayout.show(contentPanel, IMAGE_CARD);
            } finally {
                loading = false;
            }
            preserveUtf8Bom = false;
            dirty = false;
            saveButton.setEnabled(false);
            statusLabel.setText(decoded.width() + " x " + decoded.height() + " - "
                    + decoded.format().toUpperCase(Locale.ROOT) + " - " + formatSize(bytes.length));
        } catch (IOException | RuntimeException exception) {
            String reason = exception.getMessage();
            showUnavailable("Image preview unavailable"
                    + (reason == null || reason.isBlank() ? "." : ": " + reason));
        }
    }

    private static DecodedImage decodeImage(byte[] bytes) throws IOException {
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            if (input == null) throw new IOException("Could not create an image stream.");
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw new IOException("No decoder is available for this image.");
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width <= 0 || height <= 0 || width > MAX_IMAGE_DIMENSION
                        || height > MAX_IMAGE_DIMENSION || (long) width * height > MAX_IMAGE_PIXELS) {
                    throw new IOException("Image dimensions are too large (" + width + " x " + height + ").");
                }
                BufferedImage image = reader.read(0);
                if (image == null) throw new IOException("The image decoder returned no image.");
                return new DecodedImage(image, width, height, reader.getFormatName());
            } finally {
                reader.dispose();
            }
        }
    }

    private void updateStatus(String status) {
        statusLabel.setText(status + " - " + syntaxName(resourcePath));
    }

    private static String decodeUtf8(byte[] bytes) throws CharacterCodingException {
        return StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString();
    }

    private static boolean isProbablyText(String text) {
        if (text.isEmpty()) return true;
        int controls = 0;
        int inspected = Math.min(text.length(), 8192);
        for (int index = 0; index < inspected; index++) {
            char value = text.charAt(index);
            if (value == 0) return false;
            if (Character.isISOControl(value) && value != '\n' && value != '\r' && value != '\t' && value != '\f') {
                controls++;
            }
        }
        return controls <= Math.max(1, inspected / 100);
    }

    private static String syntaxStyle(String path) {
        String file = fileName(path).toLowerCase(Locale.ROOT);
        if (file.equals("dockerfile")) return SyntaxConstants.SYNTAX_STYLE_DOCKERFILE;
        if (file.equals("makefile") || file.startsWith("makefile.")) return SyntaxConstants.SYNTAX_STYLE_MAKEFILE;
        if (file.equals("hosts")) return SyntaxConstants.SYNTAX_STYLE_HOSTS;
        if (file.equals(".htaccess")) return SyntaxConstants.SYNTAX_STYLE_HTACCESS;
        if (file.equals(".env") || file.startsWith(".env.")) return SyntaxConstants.SYNTAX_STYLE_ENV;
        String extension = extension(file);
        return switch (extension) {
            case "java" -> SyntaxConstants.SYNTAX_STYLE_JAVA;
            case "kt", "kts" -> SyntaxConstants.SYNTAX_STYLE_KOTLIN;
            case "groovy", "gradle" -> SyntaxConstants.SYNTAX_STYLE_GROOVY;
            case "xml", "xsd", "xsl", "xslt", "svg" -> SyntaxConstants.SYNTAX_STYLE_XML;
            case "dtd" -> SyntaxConstants.SYNTAX_STYLE_DTD;
            case "html", "htm" -> SyntaxConstants.SYNTAX_STYLE_HTML;
            case "css" -> SyntaxConstants.SYNTAX_STYLE_CSS;
            case "less" -> SyntaxConstants.SYNTAX_STYLE_LESS;
            case "js", "mjs", "cjs" -> SyntaxConstants.SYNTAX_STYLE_JAVASCRIPT;
            case "ts", "tsx" -> SyntaxConstants.SYNTAX_STYLE_TYPESCRIPT;
            case "json" -> SyntaxConstants.SYNTAX_STYLE_JSON;
            case "jsonc" -> SyntaxConstants.SYNTAX_STYLE_JSON_WITH_COMMENTS;
            case "yaml", "yml" -> SyntaxConstants.SYNTAX_STYLE_YAML;
            case "properties", "mf" -> SyntaxConstants.SYNTAX_STYLE_PROPERTIES_FILE;
            case "ini", "cfg", "conf", "toml" -> SyntaxConstants.SYNTAX_STYLE_INI;
            case "md", "markdown" -> SyntaxConstants.SYNTAX_STYLE_MARKDOWN;
            case "sh", "bash", "zsh" -> SyntaxConstants.SYNTAX_STYLE_UNIX_SHELL;
            case "bat", "cmd" -> SyntaxConstants.SYNTAX_STYLE_WINDOWS_BATCH;
            case "ps1", "psm1", "psd1" -> SyntaxConstants.SYNTAX_STYLE_POWERSHELL;
            case "py" -> SyntaxConstants.SYNTAX_STYLE_PYTHON;
            case "rb" -> SyntaxConstants.SYNTAX_STYLE_RUBY;
            case "php" -> SyntaxConstants.SYNTAX_STYLE_PHP;
            case "c", "h" -> SyntaxConstants.SYNTAX_STYLE_C;
            case "cc", "cpp", "cxx", "hpp" -> SyntaxConstants.SYNTAX_STYLE_CPLUSPLUS;
            case "cs" -> SyntaxConstants.SYNTAX_STYLE_CSHARP;
            case "rs" -> SyntaxConstants.SYNTAX_STYLE_RUST;
            case "go" -> SyntaxConstants.SYNTAX_STYLE_GO;
            case "dart" -> SyntaxConstants.SYNTAX_STYLE_DART;
            case "scala", "sc" -> SyntaxConstants.SYNTAX_STYLE_SCALA;
            case "sql" -> SyntaxConstants.SYNTAX_STYLE_SQL;
            case "lua" -> SyntaxConstants.SYNTAX_STYLE_LUA;
            case "pl", "pm" -> SyntaxConstants.SYNTAX_STYLE_PERL;
            case "proto" -> SyntaxConstants.SYNTAX_STYLE_PROTO;
            case "csv", "tsv" -> SyntaxConstants.SYNTAX_STYLE_CSV;
            case "tex" -> SyntaxConstants.SYNTAX_STYLE_LATEX;
            default -> SyntaxConstants.SYNTAX_STYLE_NONE;
        };
    }

    private static boolean isImagePath(String path) {
        String extension = extension(fileName(path).toLowerCase(Locale.ROOT));
        return switch (extension) {
            case "png", "webp", "jpg", "jpeg", "gif", "bmp", "wbmp" -> true;
            default -> false;
        };
    }

    private static boolean isXmlPath(String path) {
        return "xml".equals(extension(fileName(path).toLowerCase(Locale.ROOT)));
    }

    private static String syntaxName(String path) {
        if (path == null) return "Text";
        if (isImagePath(path)) return "Image";
        String style = syntaxStyle(path);
        int slash = style.lastIndexOf('/');
        String name = slash < 0 ? style : style.substring(slash + 1);
        return name.equals("plain") || name.equals("text") ? "Plain text" : name.toUpperCase(Locale.ROOT);
    }

    private static String fileName(String path) {
        String normalized = path == null ? "" : path.replace('\\', '/');
        int slash = normalized.lastIndexOf('/');
        return slash < 0 ? normalized : normalized.substring(slash + 1);
    }

    private static String extension(String file) {
        int dot = file.lastIndexOf('.');
        return dot < 0 ? "" : file.substring(dot + 1);
    }

    private static boolean startsWith(byte[] bytes, byte[] prefix) {
        if (bytes == null || bytes.length < prefix.length) return false;
        for (int index = 0; index < prefix.length; index++) {
            if (bytes[index] != prefix[index]) return false;
        }
        return true;
    }

    private static String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        String[] units = {"KB", "MB", "GB"};
        double value = bytes;
        int unit = -1;
        do {
            value /= 1024d;
            unit++;
        } while (value >= 1024d && unit < units.length - 1);
        return String.format(Locale.ROOT, "%.1f %s", value, units[unit]);
    }

    private record DecodedImage(BufferedImage image, int width, int height, String format) {
    }

    private static final class ImagePreviewPanel extends JPanel implements Scrollable {
        private static final int PADDING = 32;
        private static final int CHECKER_SIZE = 8;
        private BufferedImage image;

        private ImagePreviewPanel() {
            setOpaque(true);
        }

        private void setImage(BufferedImage image) {
            this.image = image;
            revalidate();
            repaint();
        }

        @Override
        public Dimension getPreferredSize() {
            return image == null
                    ? new Dimension(480, 320)
                    : new Dimension(image.getWidth() + PADDING * 2, image.getHeight() + PADDING * 2);
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            if (image == null) return;
            int x = Math.max(PADDING, (getWidth() - image.getWidth()) / 2);
            int y = Math.max(PADDING, (getHeight() - image.getHeight()) / 2);
            Rectangle oldClip = graphics.getClipBounds();
            graphics.clipRect(x, y, image.getWidth(), image.getHeight());
            Color light = new Color(0xE4E7EB);
            Color dark = new Color(0xC8CDD3);
            for (int row = 0; row < image.getHeight(); row += CHECKER_SIZE) {
                for (int column = 0; column < image.getWidth(); column += CHECKER_SIZE) {
                    graphics.setColor(((row / CHECKER_SIZE) + (column / CHECKER_SIZE)) % 2 == 0
                            ? light : dark);
                    graphics.fillRect(x + column, y + row, CHECKER_SIZE, CHECKER_SIZE);
                }
            }
            graphics.setClip(oldClip);
            graphics.drawImage(image, x, y, null);
        }

        @Override
        public Dimension getPreferredScrollableViewportSize() {
            return new Dimension(640, 480);
        }

        @Override
        public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
            return 16;
        }

        @Override
        public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
            return Math.max(16, (orientation == javax.swing.SwingConstants.HORIZONTAL
                    ? visibleRect.width : visibleRect.height) - 16);
        }

        @Override
        public boolean getScrollableTracksViewportWidth() {
            return getParent() instanceof JViewport viewport
                    && viewport.getWidth() > getPreferredSize().width;
        }

        @Override
        public boolean getScrollableTracksViewportHeight() {
            return getParent() instanceof JViewport viewport
                    && viewport.getHeight() > getPreferredSize().height;
        }
    }
}
