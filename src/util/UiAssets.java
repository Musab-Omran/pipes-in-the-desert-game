package util;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.imageio.ImageIO;

/**
 * Centralised image loader. Resolves assets from the classpath first
 * (executable JAR), then from the IDE {@code src/assets/} directory.
 */
public final class UiAssets {

    private static final Map<String, BufferedImage> CACHE = new ConcurrentHashMap<>();

    private UiAssets() {}

    public static BufferedImage load(String fileName) {
        return CACHE.computeIfAbsent(fileName, UiAssets::loadInternal);
    }

    public static BufferedImage scaled(String fileName, int w, int h) {
        BufferedImage src = load(fileName);
        if (src.getWidth() == w && src.getHeight() == h) return src;
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = out.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                            RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2.drawImage(src, 0, 0, w, h, null);
        g2.dispose();
        return out;
    }

    private static BufferedImage loadInternal(String name) {
        for (String res : new String[]{"/assets/" + name, "/" + name}) {
            try (InputStream in = UiAssets.class.getResourceAsStream(res)) {
                if (in != null) {
                    BufferedImage img = ImageIO.read(in);
                    if (img != null) return img;
                }
            } catch (IOException ignored) {}
        }
        for (Path p : new Path[]{
                Path.of("src","assets",name), Path.of("assets",name)}) {
            if (Files.exists(p)) {
                try { return ImageIO.read(p.toFile()); }
                catch (IOException ignored) {}
            }
        }
        return new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
    }
}
