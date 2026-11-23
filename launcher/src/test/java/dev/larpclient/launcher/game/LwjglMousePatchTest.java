package dev.larpclient.launcher.game;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LwjglMousePatchTest {
    /** A stand-in for LWJGL's LinuxMouse: one method using both window-size natives, one unrelated call. */
    private static byte[] fakeLinuxMouse() {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        cw.visit(Opcodes.V1_5, Opcodes.ACC_FINAL, LwjglMousePatch.TARGET, null, "java/lang/Object", null);
        for (String n : new String[]{"nGetWindowHeight", "nGetWindowWidth", "nGetButtonCount"}) {
            cw.visitMethod(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_NATIVE, n, "(JJ)I", null, null).visitEnd();
        }
        MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC, "size", "(JJ)I", null, null);
        mv.visitCode();
        for (String n : new String[]{"nGetWindowHeight", "nGetWindowWidth", "nGetButtonCount"}) {
            mv.visitVarInsn(Opcodes.LLOAD, 0);
            mv.visitVarInsn(Opcodes.LLOAD, 2);
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, LwjglMousePatch.TARGET, n, "(JJ)I", false);
        }
        mv.visitInsn(Opcodes.IADD);
        mv.visitInsn(Opcodes.IADD);
        mv.visitInsn(Opcodes.IRETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();
        cw.visitEnd();
        return cw.toByteArray();
    }

    private static List<String> calls(byte[] clazz) {
        List<String> calls = new ArrayList<>();
        new ClassReader(clazz).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String desc, String sig, String[] ex) {
                return new MethodVisitor(Opcodes.ASM9) {
                    @Override
                    public void visitMethodInsn(int op, String owner, String method, String d, boolean itf) {
                        calls.add(owner + "." + method);
                    }
                };
            }
        }, 0);
        return calls;
    }

    @Test
    void redirectsWindowSizeQueriesOnly() throws IOException {
        List<String> calls = calls(LwjglMousePatch.patch(fakeLinuxMouse()));
        assertEquals(List.of(
                LwjglMousePatch.HELPER + ".height",
                LwjglMousePatch.HELPER + ".width",
                LwjglMousePatch.TARGET + ".nGetButtonCount"), calls);
    }

    @Test
    void refusesUnknownLwjgl() {
        ClassWriter cw = new ClassWriter(0);
        cw.visit(Opcodes.V1_5, 0, LwjglMousePatch.TARGET, null, "java/lang/Object", null);
        cw.visitEnd();
        assertThrows(IOException.class, () -> LwjglMousePatch.patch(cw.toByteArray()));
    }

    @Test
    void findsLwjgl2OnClasspath() {
        Path lwjgl = Path.of("/x/org/lwjgl/lwjgl/lwjgl/2.9.4-nightly-20150209/lwjgl-2.9.4-nightly-20150209.jar");
        assertEquals(lwjgl, LwjglMousePatch.findLwjgl(List.of(
                Path.of("/x/lwjgl_util-2.9.4-nightly-20150209.jar"), lwjgl, Path.of("/x/forge.jar"))));
        assertNull(LwjglMousePatch.findLwjgl(List.of(Path.of("/x/lwjgl-3.3.1.jar"))));
    }

    private static void writeJar(Path jar, byte[] linuxMouse, String marker) throws IOException {
        try (OutputStream os = Files.newOutputStream(jar); JarOutputStream out = new JarOutputStream(os)) {
            out.putNextEntry(new JarEntry(LwjglMousePatch.TARGET + ".class"));
            out.write(linuxMouse);
            out.closeEntry();
            out.putNextEntry(new JarEntry("marker.txt"));
            out.write(marker.getBytes());
            out.closeEntry();
        }
    }

    @Test
    void preparesPatchedJarWithHelperAndReusesIt(@TempDir Path tmp) throws IOException {
        Path lwjgl = tmp.resolve("lwjgl-2.9.4.jar");
        writeJar(lwjgl, fakeLinuxMouse(), "a");
        Path out = LwjglMousePatch.prepare(lwjgl, tmp.resolve("patches"));
        try (JarFile jar = new JarFile(out.toFile())) {
            assertNotNull(jar.getEntry(LwjglMousePatch.HELPER + ".class"));
            assertNotNull(jar.getEntry("marker.txt"));
            assertTrue(calls(jar.getInputStream(jar.getEntry(LwjglMousePatch.TARGET + ".class")).readAllBytes())
                    .contains(LwjglMousePatch.HELPER + ".height"));
        }
        FileTime built = Files.getLastModifiedTime(out);
        assertEquals(out, LwjglMousePatch.prepare(lwjgl, tmp.resolve("patches")));
        assertEquals(built, Files.getLastModifiedTime(out));
    }

    /** Regression (audit A11): a replaced LWJGL jar with an older mtime must not reuse the old patch. */
    @Test
    void rebuildsWhenSourceJarContentChanges(@TempDir Path tmp) throws IOException {
        Path lwjgl = tmp.resolve("lwjgl-2.9.4.jar");
        writeJar(lwjgl, fakeLinuxMouse(), "a");
        Path first = LwjglMousePatch.prepare(lwjgl, tmp.resolve("patches"));
        writeJar(lwjgl, fakeLinuxMouse(), "b");
        Files.setLastModifiedTime(lwjgl, FileTime.fromMillis(0));
        Path second = LwjglMousePatch.prepare(lwjgl, tmp.resolve("patches"));
        try (JarFile jar = new JarFile(second.toFile())) {
            assertEquals("b", new String(jar.getInputStream(jar.getEntry("marker.txt")).readAllBytes()));
        }
        assertNotEquals(first, second);
    }

    /** Regression (audit A11): a failure while writing the patched jar must not leave a .part file behind. */
    @Test
    void failedPatchLeavesNoPartialFile(@TempDir Path tmp) throws IOException {
        Path lwjgl = tmp.resolve("lwjgl-2.9.4.jar");
        writeJar(lwjgl, new byte[]{1, 2, 3}, "a"); // not a class file
        Path dir = tmp.resolve("patches");
        assertThrows(Exception.class, () -> LwjglMousePatch.prepare(lwjgl, dir));
        try (Stream<Path> files = Files.list(dir)) {
            assertEquals(List.of(), files.toList());
        }
    }
}
