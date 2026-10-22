package org.codirex.webide.editor;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Set;

/**
 * Reading and writing text files for the editor. Pure Java.
 *
 * <p>Only valid UTF-8 text is opened, so that saving can never change bytes the user did not edit.
 * Line endings are normalized to "\n" while editing and restored on save, and a UTF-8 byte order
 * mark is kept if the file had one.
 */
public final class TextFile {

    /** Largest file the editor opens. */
    public static final long MAX_BYTES = 5L * 1024 * 1024;

    /** A file read from disk. */
    public static final class Loaded {
        public final String text;
        public final boolean crlf;
        public final boolean bom;
        public final long lastModified;

        Loaded(String text, boolean crlf, boolean bom, long lastModified) {
            this.text = text;
            this.crlf = crlf;
            this.bom = bom;
            this.lastModified = lastModified;
        }
    }

    private TextFile() {
    }

    public static Loaded load(File file) throws IOException {
        if (file.length() > MAX_BYTES) {
            throw new IOException("The file is larger than 5 MB");
        }
        byte[] bytes = Files.readAllBytes(file.toPath());
        if (looksBinary(bytes)) {
            throw new IOException("This looks like a binary file");
        }
        boolean bom = bytes.length >= 3 && bytes[0] == (byte) 0xEF && bytes[1] == (byte) 0xBB
                && bytes[2] == (byte) 0xBF;
        int offset = bom ? 3 : 0;
        String text;
        try {
            text = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes, offset, bytes.length - offset))
                    .toString();
        } catch (CharacterCodingException e) {
            throw new IOException("The file is not valid UTF-8, so it is not opened to avoid damaging it");
        }
        boolean crlf = text.contains("\r\n");
        if (crlf) {
            text = text.replace("\r\n", "\n");
        }
        return new Loaded(text, crlf, bom, file.lastModified());
    }

    /**
     * Writes the text through a temporary file in the same folder, so a failure never leaves a
     * half-written file. File permissions (for example an executable script) are kept, and a
     * symbolic link is followed rather than replaced.
     */
    public static void save(File file, String text, boolean crlf, boolean bom) throws IOException {
        String content = crlf ? text.replace("\n", "\r\n") : text;
        byte[] body = content.getBytes(StandardCharsets.UTF_8);
        byte[] data = body;
        if (bom) {
            data = new byte[body.length + 3];
            data[0] = (byte) 0xEF;
            data[1] = (byte) 0xBB;
            data[2] = (byte) 0xBF;
            System.arraycopy(body, 0, data, 3, body.length);
        }

        Path target = file.toPath();
        if (Files.isSymbolicLink(target)) {
            target = target.toRealPath();
        }
        Path parent = target.toAbsolutePath().getParent();
        Path temp = Files.createTempFile(parent, ".webide-save-", ".tmp");
        try {
            Files.write(temp, data);
            copyPermissions(target, temp);
            try {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    static boolean looksBinary(byte[] bytes) {
        int n = Math.min(bytes.length, 8000);
        for (int i = 0; i < n; i++) {
            if (bytes[i] == 0) {
                return true;
            }
        }
        return false;
    }

    private static void copyPermissions(Path from, Path to) {
        try {
            if (Files.exists(from)) {
                Set<PosixFilePermission> permissions = Files.getPosixFilePermissions(from);
                Files.setPosixFilePermissions(to, permissions);
            }
        } catch (UnsupportedOperationException | IOException ignored) {
            // The file system has no POSIX permissions; the new file keeps its defaults.
        }
    }
}
