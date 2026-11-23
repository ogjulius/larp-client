package dev.larpclient.bundle;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.IOException;

/**
 * Fixes the game freezing when the mouse moves under XWayland (Linux).
 * <p>
 * LWJGL 2's {@code LinuxMouse} calls {@code XGetGeometry} (via {@code nGetWindowHeight}/{@code nGetWindowWidth})
 * for every pointer-motion event. Each call is a blocking round trip to the X server; under XWayland
 * those take milliseconds, so a 1000 Hz mouse produces more work than the game can get through and the
 * client thread falls seconds behind ("not responding"). The patch rewrites those calls to go through
 * {@code org.lwjgl.opengl.LarpWindowSizeCache}, which caches the size briefly. The launcher splices the
 * patched class and the helper into a copy of the LWJGL jar (LWJGL's packages are sealed, and its classes
 * load from the system class loader, so a Mixin cannot do this).
 */
final class LwjglMousePatch {
    static final String TARGET = "org/lwjgl/opengl/LinuxMouse";
    static final String HELPER = "org/lwjgl/opengl/LarpWindowSizeCache";

    private LwjglMousePatch() {
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
