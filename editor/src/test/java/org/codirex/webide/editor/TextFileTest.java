package org.codirex.webide.editor;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Set;

public class TextFileTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private File write(String name, byte[] bytes) throws IOException {
        File f = tmp.newFile(name);
        Files.write(f.toPath(), bytes);
        return f;
    }

    @Test
    public void loadsPlainUtf8() throws IOException {
        File f = write("a.txt", "héllo\nworld\n".getBytes("UTF-8"));
        TextFile.Loaded loaded = TextFile.load(f);
        assertEquals("héllo\nworld\n", loaded.text);
        assertFalse(loaded.crlf);
        assertFalse(loaded.bom);
    }

    @Test
    public void remembersCrlfAndBomAndWritesThemBack() throws IOException {
        byte[] original = new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF, 'a', '\r', '\n', 'b'};
        File f = write("a.txt", original);
        TextFile.Loaded loaded = TextFile.load(f);
        assertEquals("a\nb", loaded.text);
        assertTrue(loaded.crlf);
        assertTrue(loaded.bom);
        TextFile.save(f, loaded.text, loaded.crlf, loaded.bom);
        assertArrayEquals(original, Files.readAllBytes(f.toPath()));
    }

    @Test
    public void refusesBinaryAndInvalidUtf8() throws IOException {
        File binary = write("b.bin", new byte[]{1, 2, 0, 3});
        File latin1 = write("l.txt", new byte[]{'c', 'a', 'f', (byte) 0xE9});
        for (File f : new File[]{binary, latin1}) {
            try {
                TextFile.load(f);
                fail("expected IOException for " + f.getName());
            } catch (IOException expected) {
                // fine
            }
        }
    }

    @Test
    public void refusesHugeFiles() throws IOException {
        File f = tmp.newFile("big.txt");
        try (java.io.RandomAccessFile raf = new java.io.RandomAccessFile(f, "rw")) {
            raf.setLength(TextFile.MAX_BYTES + 1);
        }
        try {
            TextFile.load(f);
            fail("expected IOException");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("5 MB"));
        }
    }

    @Test
    public void saveKeepsPermissionsAndLeavesNoTempFiles() throws IOException {
        File f = write("run.sh", "echo 1\n".getBytes("UTF-8"));
        Files.setPosixFilePermissions(f.toPath(), java.util.EnumSet.of(
                PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE, PosixFilePermission.OWNER_EXECUTE));
        TextFile.save(f, "echo 2\n", false, false);
        Set<PosixFilePermission> permissions = Files.getPosixFilePermissions(f.toPath());
        assertTrue(permissions.contains(PosixFilePermission.OWNER_EXECUTE));
        assertEquals("echo 2\n", new String(Files.readAllBytes(f.toPath()), "UTF-8"));
        String[] left = f.getParentFile().list((dir, name) -> name.startsWith(".webide-save-"));
        assertEquals(0, left.length);
    }

    @Test
    public void saveFollowsSymbolicLinks() throws IOException {
        File real = write("real.txt", "old".getBytes("UTF-8"));
        File link = new File(tmp.getRoot(), "link.txt");
        Files.createSymbolicLink(link.toPath(), real.toPath());
        TextFile.save(link, "new", false, false);
        assertTrue(Files.isSymbolicLink(link.toPath()));
        assertEquals("new", new String(Files.readAllBytes(real.toPath()), "UTF-8"));
    }
}
