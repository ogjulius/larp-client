package dev.larpclient.launcher.ui;

import dev.larpclient.launcher.game.GameFiles;
import dev.larpclient.launcher.game.Installer;

import javax.imageio.ImageIO;
import java.awt.AlphaComposite;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.awt.image.ConvolveOp;
import java.awt.image.Kernel;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Artwork read from the user's own copy of the Minecraft client jar (nothing is redistributed):
 * a blurred panorama strip for the home background and the default skins for avatars.
 */
public final class GameArt {
    private GameArt() {
    }

    private static Path clientJar(GameFiles files) {
        return files.versionDir(Installer.MC_VERSION).resolve(Installer.MC_VERSION + ".jar");
    }

    public static BufferedImage readFromClient(GameFiles files, String path) {
        Path jar = clientJar(files);
        if (!Files.isRegularFile(jar)) {
            return null;
        }
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            ZipEntry entry = zip.getEntry(path);
            if (entry == null) {
                return null;
            }
            try (InputStream in = zip.getInputStream(entry)) {
                return ImageIO.read(in);
            }
        } catch (Exception e) {
            return null;
        }
    }

    /** Front, right, back and left panorama faces side by side, softly blurred. Null if not installed. */
    public static BufferedImage blurredPanorama(GameFiles files) {
        BufferedImage[] faces = new BufferedImage[4];
        for (int i = 0; i < 4; i++) {
            faces[i] = readFromClient(files, "assets/minecraft/textures/gui/title/background/panorama_" + i + ".png");
            if (faces[i] == null) {
                return null;
            }
        }
        int size = faces[0].getHeight();
        // Repeat the first face at the end so the strip can loop seamlessly while panning.
        BufferedImage strip = new BufferedImage(size * 5, size, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = strip.createGraphics();
        for (int i = 0; i < 5; i++) {
            g.drawImage(faces[i % 4], i * size, 0, size, size, null);
        }
        g.dispose();
        return blur(scale(strip, 0.5), 3);
    }

    private static BufferedImage scale(BufferedImage src, double factor) {
        int w = Math.max(1, (int) (src.getWidth() * factor));
        int h = Math.max(1, (int) (src.getHeight() * factor));
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.drawImage(src, 0, 0, w, h, null);
        g.dispose();
        return out;
    }

    private static BufferedImage blur(BufferedImage src, int radius) {
        int size = radius * 2 + 1;
        float[] data = new float[size * size];
        java.util.Arrays.fill(data, 1f / data.length);
        BufferedImage padded = new BufferedImage(src.getWidth() + radius * 2, src.getHeight() + radius * 2, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = padded.createGraphics();
        g.drawImage(src, radius, radius, null);
        // clamp edges
        g.drawImage(src, 0, radius, radius, src.getHeight() + radius, 0, 0, 1, src.getHeight(), null);
        g.drawImage(src, src.getWidth() + radius, radius, src.getWidth() + radius * 2, src.getHeight() + radius,
                src.getWidth() - 1, 0, src.getWidth(), src.getHeight(), null);
        g.dispose();
        BufferedImage blurred = new ConvolveOp(new Kernel(size, size, data), ConvolveOp.EDGE_NO_OP, null).filter(padded, null);
        return blurred.getSubimage(radius, radius, src.getWidth(), src.getHeight());
    }

    /** Subtle monochrome film grain tile. */
    public static BufferedImage grain(int size, int alpha) {
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Random random = new Random(7);
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                int v = random.nextInt(256);
                img.setRGB(x, y, (random.nextInt(alpha + 1) << 24) | (v << 16) | (v << 8) | v);
            }
        }
        return img;
    }

    /** 8×8 face + hat layer from a 64×64 skin, scaled with nearest neighbour. */
    public static BufferedImage face(BufferedImage skin, int size) {
        BufferedImage out = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        if (skin == null) {
            return out;
        }
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g.drawImage(skin, 0, 0, size, size, 8, 8, 16, 16, null);
        if (skin.getHeight() >= 32) {
            g.setComposite(AlphaComposite.SrcOver);
            g.drawImage(skin, 0, 0, size, size, 40, 8, 48, 16, null);
        }
        g.dispose();
        return out;
    }
}
