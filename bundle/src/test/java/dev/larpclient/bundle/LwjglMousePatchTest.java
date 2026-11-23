package dev.larpclient.bundle;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;


import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
}
