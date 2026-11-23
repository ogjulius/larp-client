package dev.larpclient.launcher.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class SafeFilesTest {
    private static final Set<PosixFilePermission> OWNER_ONLY = PosixFilePermissions.fromString("rw-------");

    private static boolean posix(Path dir) {
        return dir.getFileSystem().supportedFileAttributeViews().contains("posix");
    }

    /** Regression (audit A1): the temp file must be owner-only from the moment it exists, before any content. */
    @Test
    void ownerOnlyFileIsCreatedPrivate(@TempDir Path dir) throws IOException {
        assumeTrue(posix(dir));
        Path f = dir.resolve("secret.tmp");
        SafeFiles.createOwnerOnly(f).close();
        assertEquals(0, Files.size(f));
        assertEquals(OWNER_ONLY, Files.getPosixFilePermissions(f));
    }

    /** Regression (audit A1): a stale world-readable temp file from a crash must not be reused. */
    @Test
    void staleReadableTempFileIsReplaced(@TempDir Path dir) throws IOException {
        assumeTrue(posix(dir));
        Path file = dir.resolve("accounts.json");
        Path tmp = dir.resolve("accounts.json.tmp");
        Files.writeString(tmp, "stale");
        Files.setPosixFilePermissions(tmp, PosixFilePermissions.fromString("rw-r--r--"));
        SafeFiles.writeAtomically(file, "{\"ok\":true}", true);
        assertEquals("{\"ok\":true}", Files.readString(file));
        assertEquals(OWNER_ONLY, Files.getPosixFilePermissions(file));
        assertFalse(Files.exists(tmp));
    }

    @Test
    void quarantineMovesFileAsideAndKeepsContent(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("launcher.json");
        Files.writeString(file, "{broken");
        Path moved = SafeFiles.quarantine(file);
        assertEquals(dir.resolve("launcher.json.broken"), moved);
        assertEquals("{broken", Files.readString(moved));
        assertFalse(Files.exists(file));
    }
}
