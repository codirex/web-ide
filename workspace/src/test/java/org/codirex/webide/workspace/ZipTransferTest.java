package org.codirex.webide.workspace;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class ZipTransferTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private static byte[] zip(String... namesAndContents) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream out = new ZipOutputStream(bytes)) {
            for (int i = 0; i < namesAndContents.length; i += 2) {
                out.putNextEntry(new ZipEntry(namesAndContents[i]));
                out.write(namesAndContents[i + 1].getBytes(StandardCharsets.UTF_8));
                out.closeEntry();
            }
        }
        return bytes.toByteArray();
    }

    @Test
    public void roundTripKeepsFilesAndSkipsNodeModules() throws IOException {
        File project = tmp.newFolder("project");
        File src = new File(project, "src");
        assertTrue(src.mkdir());
        Files.write(new File(src, "index.js").toPath(), "console.log(1)".getBytes());
        File modules = new File(project, "node_modules/left-pad");
        assertTrue(modules.mkdirs());
        Files.write(new File(modules, "index.js").toPath(), "x".getBytes());

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int written = ZipTransfer.zipDirectory(project, out, true, "project");
        assertEquals(1, written);

        File target = tmp.newFolder("copy");
        int read = ZipTransfer.unzip(new ByteArrayInputStream(out.toByteArray()), target, ZipTransfer.DEFAULT_MAX_BYTES);
        assertEquals(1, read);
        assertEquals("console.log(1)", new String(Files.readAllBytes(new File(target, "src/index.js").toPath())));
        assertFalse(new File(target, "node_modules").exists());
        assertFalse("temporary folder must be gone", new File(target, ".import-tmp").exists());
    }

    @Test
    public void aProjectWithOnlyOneFolderSurvivesTheRoundTrip() throws IOException {
        File project = tmp.newFolder("only-src");
        File src = new File(project, "src");
        assertTrue(src.mkdir());
        Files.write(new File(src, "a.js").toPath(), "x".getBytes());

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ZipTransfer.zipDirectory(project, out, true, "only-src");
        File target = tmp.newFolder("copy");
        ZipTransfer.unzip(new ByteArrayInputStream(out.toByteArray()), target, ZipTransfer.DEFAULT_MAX_BYTES);
        assertTrue(new File(target, "src/a.js").exists());
    }

    @Test
    public void singleTopLevelFolderIsUnwrapped() throws IOException {
        File target = tmp.newFolder("t");
        ZipTransfer.unzip(new ByteArrayInputStream(zip("repo-main/a.txt", "a", "repo-main/src/b.txt", "b")),
                target, ZipTransfer.DEFAULT_MAX_BYTES);
        assertTrue(new File(target, "a.txt").exists());
        assertTrue(new File(target, "src/b.txt").exists());
        assertFalse(new File(target, "repo-main").exists());
    }

    @Test
    public void severalTopLevelItemsStayAsTheyAre() throws IOException {
        File target = tmp.newFolder("t");
        ZipTransfer.unzip(new ByteArrayInputStream(zip("a.txt", "a", "src/b.txt", "b")),
                target, ZipTransfer.DEFAULT_MAX_BYTES);
        assertTrue(new File(target, "a.txt").exists());
        assertTrue(new File(target, "src/b.txt").exists());
    }

    @Test
    public void rejectsPathsThatEscapeTheTarget() throws IOException {
        File target = tmp.newFolder("t");
        try {
            ZipTransfer.unzip(new ByteArrayInputStream(zip("../evil.txt", "x")), target, ZipTransfer.DEFAULT_MAX_BYTES);
            fail("expected IOException");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("Unsafe"));
        }
        assertFalse(new File(target.getParentFile(), "evil.txt").exists());
        assertFalse(new File(target, ".import-tmp").exists());
    }

    @Test
    public void enforcesTheSizeLimit() throws IOException {
        File target = tmp.newFolder("t");
        try {
            ZipTransfer.unzip(new ByteArrayInputStream(zip("big.txt", "0123456789")), target, 5);
            fail("expected IOException");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("larger"));
        }
    }
}
