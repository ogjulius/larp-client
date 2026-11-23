package dev.larpclient.launcher.game;

import dev.larpclient.launcher.util.Http;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collections;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;

/**
 * Fixes the game freezing when the mouse moves under XWayland (Linux).
 * <p>
 * LWJGL 2's {@code LinuxMouse} calls {@code XGetGeometry} (via {@code nGetWindowHeight}/{@code nGetWindowWidth})
 * for every pointer-motion event. Each call is a blocking round trip to the X server; under XWayland
 * those take milliseconds, so a 1000 Hz mouse produces more work than the game can get through and the
 * client thread falls seconds behind ("not responding"). We rewrite those calls to go through
 * {@code org.lwjgl.opengl.LarpWindowSizeCache}, which caches the size briefly, and launch with a patched
 * copy of the LWJGL jar. A copy rather than an extra jar in front: LWJGL's packages are sealed. LWJGL
 * classes are loaded by the system class loader, so this cannot be done with a Mixin.
 */
final class LwjglMousePatch {
    static final String TARGET = "org/lwjgl/opengl/LinuxMouse";
    static final String HELPER = "org/lwjgl/opengl/LarpWindowSizeCache";
    private static final String HELPER_RESOURCE = "/larp/gamepatch/LarpWindowSizeCache.class.bin";
    /** Bump when the patch changes, so existing patch jars are rebuilt. */
    private static final int VERSION = 3;

    private LwjglMousePatch() {
    }

    /** The LWJGL 2 jar on the classpath, if any. */
    static Path findLwjgl(List<Path> classpath) {
        for (Path p : classpath) {
            String name = p.getFileName().toString();
            if (name.startsWith("lwjgl-2.") && name.endsWith(".jar")) {
                return p;
            }
        }
        return null;
    }

    /**
     * Build (or reuse) a patched copy of {@code lwjglJar} inside {@code dir}; returns its path.
     * The output name includes the source jar's SHA-1, so a changed LWJGL jar always gets a fresh patch.
     */
    static Path prepare(Path lwjglJar, Path dir) throws IOException {
        String base = lwjglJar.getFileName().toString().replace(".jar", "");
        String prefix = base + "-xwayland-mouse-";
        Path out = dir.resolve(prefix + "v" + VERSION + "-" + Http.sha1(lwjglJar).substring(0, 12) + ".jar");
        if (Files.isRegularFile(out)) {
            return out;
        }
        byte[] helper;
        try (InputStream in = LwjglMousePatch.class.getResourceAsStream(HELPER_RESOURCE)) {
            if (in == null) {
                throw new IOException("Launcher build is missing " + HELPER_RESOURCE);
            }
            helper = in.readAllBytes();
        }
        Files.createDirectories(dir);
        Path tmp = dir.resolve(out.getFileName() + ".part");
        try {
            writePatched(lwjglJar, tmp, helper);
            Files.move(tmp, out, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(tmp);
        }
        deleteStale(dir, prefix, out);
        return out;
    }

    private static void writePatched(Path lwjglJar, Path target, byte[] helper) throws IOException {
        boolean found = false;
        try (JarFile in = new JarFile(lwjglJar.toFile());
             OutputStream os = Files.newOutputStream(target);
             JarOutputStream jar = new JarOutputStream(os)) {
            for (JarEntry entry : Collections.list(in.entries())) {
                byte[] data;
                try (InputStream is = in.getInputStream(entry)) {
                    data = is.readAllBytes();
                }
                if (entry.getName().equals(TARGET + ".class")) {
                    data = patch(data);
                    found = true;
                }
                jar.putNextEntry(new JarEntry(entry.getName()));
                jar.write(data);
                jar.closeEntry();
            }
            jar.putNextEntry(new JarEntry(HELPER + ".class"));
            jar.write(helper);
            jar.closeEntry();
        }
        if (!found) {
            throw new IOException(TARGET + " not found in " + lwjglJar);
        }
    }

    /** Remove patches built from earlier LWJGL jars or patch versions (best effort). */
    private static void deleteStale(Path dir, String prefix, Path keep) {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, prefix + "*.jar")) {
            for (Path p : stream) {
                if (!p.equals(keep)) {
                    Files.deleteIfExists(p);
                }
            }
        } catch (IOException e) {
            System.err.println("[larp] could not clean old LWJGL patches: " + e);
        }
    }

    /** Redirect every window-size query in {@code LinuxMouse} to the caching helper. */
    static byte[] patch(byte[] linuxMouse) throws IOException {
        ClassReader reader;
        try {
            reader = new ClassReader(linuxMouse);
        } catch (RuntimeException e) {
            throw new IOException("LinuxMouse is not a valid class file", e);
        }
        ClassWriter writer = new ClassWriter(0); // same stack shape, no new locals: nothing to recompute
        int[] rewritten = {0};
        reader.accept(new ClassVisitor(Opcodes.ASM9, writer) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
                MethodVisitor mv = super.visitMethod(access, name, desc, signature, exceptions);
                return new MethodVisitor(Opcodes.ASM9, mv) {
                    @Override
                    public void visitMethodInsn(int opcode, String owner, String method, String mdesc, boolean itf) {
                        if (opcode == Opcodes.INVOKESTATIC && owner.equals(TARGET) && mdesc.equals("(JJ)I")
                                && (method.equals("nGetWindowHeight") || method.equals("nGetWindowWidth"))) {
                            rewritten[0]++;
                            super.visitMethodInsn(opcode, HELPER, method.equals("nGetWindowHeight") ? "height" : "width", mdesc, false);
                        } else {
                            super.visitMethodInsn(opcode, owner, method, mdesc, itf);
                        }
                    }
                };
            }
        }, 0);
        if (rewritten[0] == 0) {
            throw new IOException("Unexpected LWJGL version: no window-size calls found in LinuxMouse");
        }
        return writer.toByteArray();
    }
}
