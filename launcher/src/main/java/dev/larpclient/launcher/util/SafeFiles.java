package dev.larpclient.launcher.util;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.channels.Channels;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.EnumSet;

/** Crash-safe writes for the launcher's config files. */
public final class SafeFiles {
    private SafeFiles() {
    }

    /**
     * Create a new, empty file readable only by the owner. The permissions are applied atomically at
     * creation (no window in which the file is readable by others), on file systems that support POSIX
     * permissions. Fails if the file already exists.
     */
    public static OutputStream createOwnerOnly(Path file) throws IOException {
        EnumSet<StandardOpenOption> options = EnumSet.of(StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        if (file.getFileSystem().supportedFileAttributeViews().contains("posix")) {
            FileAttribute<?> ownerOnly = PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------"));
            return Channels.newOutputStream(Files.newByteChannel(file, options, ownerOnly));
        }
        return Channels.newOutputStream(Files.newByteChannel(file, options));
    }

    /**
     * Replace {@code file} with {@code content} so that readers see either the old or the new file,
     * never a partial one. With {@code ownerOnly} the file is never readable by other users.
     */
    public static void writeAtomically(Path file, String content, boolean ownerOnly) throws IOException {
        Path parent = file.toAbsolutePath().getParent();
        Files.createDirectories(parent);
        Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
        Files.deleteIfExists(tmp); // a leftover from a crash may have looser permissions
        try {
            try (OutputStream out = ownerOnly ? createOwnerOnly(tmp) : Files.newOutputStream(tmp, StandardOpenOption.CREATE_NEW)) {
                out.write(content.getBytes(StandardCharsets.UTF_8));
            }
            try {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    /** Move an unreadable file to {@code <name>.broken} (replacing an older one) and return its new path. */
    public static Path quarantine(Path file) throws IOException {
        Path broken = file.resolveSibling(file.getFileName() + ".broken");
        Files.move(file, broken, StandardCopyOption.REPLACE_EXISTING);
        return broken;
    }

    /**
     * Quarantine after a failed load and describe what happened, for the user. Never throws: if the
     * file can't even be moved, that's included in the message.
     */
    public static String quarantineAfterLoadFailure(Path file, Exception cause) {
        String reason = cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage();
        try {
            Path broken = quarantine(file);
            return file.getFileName() + " could not be read (" + reason + "). It was moved to "
                    + broken.getFileName() + " and defaults are in use.";
        } catch (IOException e) {
            return file.getFileName() + " could not be read (" + reason + ") or moved aside (" + e.getMessage() + ").";
        }
    }
}
