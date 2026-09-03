package de.xbrowniecodez.jbytemod.ui;

import com.github.weisj.darklaf.properties.icons.DarkSVGIcon;

import javax.swing.Icon;
import javax.swing.ImageIcon;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class SvgIcons {
    private static final int SIZE = 20;
    private static final Map<String, Icon> ICONS = new ConcurrentHashMap<>();
    private static final Map<String, ImageIcon> IMAGES = new ConcurrentHashMap<>();

    private SvgIcons() {
    }

    public static Icon icon(String name) {
        return ICONS.computeIfAbsent(name, SvgIcons::load);
    }

    public static ImageIcon image(String name) {
        return IMAGES.computeIfAbsent(name, key -> {
            Icon icon = ICONS.computeIfAbsent(key, SvgIcons::load);
            BufferedImage image = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
            Graphics2D graphics = image.createGraphics();
            try {
                icon.paintIcon(null, graphics, 0, 0);
            } finally {
                graphics.dispose();
            }
            return new ImageIcon(image);
        });
    }

    private static Icon load(String name) {
        if (name.isBlank() || name.contains("..") || name.startsWith("/")) {
            throw new IllegalArgumentException("Invalid SVG icon name: " + name);
        }
        URL resource = SvgIcons.class.getResource("/resources/icons/" + name + ".svg");
        if (resource == null) throw new IllegalStateException("Missing SVG icon: " + name);
        try {
            return new DarkSVGIcon(resource.toURI(), SIZE, SIZE);
        } catch (URISyntaxException exception) {
            throw new IllegalStateException("Invalid SVG icon URL: " + resource, exception);
        }
    }
}
