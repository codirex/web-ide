package org.codirex.webide.workspace;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;

public class FileOpsTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    @Test
    public void validatesNames() {
        assertNull(FileOps.validateName("index.html"));
        assertNull(FileOps.validateName(".gitignore"));
        assertNotNull(FileOps.validateName(""));
        assertNotNull(FileOps.validateName("   "));
        assertNotNull(FileOps.validateName("."));
        assertNotNull(FileOps.validateName(".."));
        assertNotNull(FileOps.validateName("a/b"));
        assertNotNull(FileOps.validateName("a\0b"));
        assertNotNull(FileOps.validateName(new String(new char[300]).replace('\0', 'x')));
    }

    @Test
    public void insideChecksNormalizedPaths() throws IOException {
        File root = tmp.newFolder("root");
        assertTrue(FileOps.isInside(root, root));
        assertTrue(FileOps.isInside(root, new File(root, "a/b")));
        assertFalse(FileOps.isInside(root, new File(root, "../other")));
        assertFalse(FileOps.isInside(root, new File(root.getParentFile(), "rootless")));
    }

    @Test
    public void listsFoldersFirstThenByNameIgnoringCase() throws IOException {
        File dir = tmp.newFolder("p");
        FileOps.createFile(dir, "b.txt");
        FileOps.createFile(dir, "A.txt");
        FileOps.createDirectory(dir, "zeta");
        FileOps.createDirectory(dir, "alpha");
        List<FileOps.Entry> entries = FileOps.list(dir);
        assertEquals("alpha", entries.get(0).name());
        assertEquals("zeta", entries.get(1).name());
        assertEquals("A.txt", entries.get(2).name());
        assertEquals("b.txt", entries.get(3).name());
    }

    @Test
    public void createRefusesExistingNames() throws IOException {
        File dir = tmp.newFolder("p");
        FileOps.createFile(dir, "a.txt");
        try {
            FileOps.createFile(dir, "a.txt");
            fail("expected IOException");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("already exists"));
        }
    }

    @Test
    public void renameMovesAndRefusesExistingTargets() throws IOException {
        File dir = tmp.newFolder("p");
        File a = FileOps.createFile(dir, "a.txt");
        FileOps.createFile(dir, "b.txt");
        File renamed = FileOps.rename(a, "c.txt");
        assertTrue(renamed.exists());
        assertFalse(a.exists());
        try {
            FileOps.rename(renamed, "b.txt");
            fail("expected IOException");
        } catch (IOException expected) {
            // fine
        }
    }

    @Test
    public void deleteRemovesTreesAndDoesNotFollowLinks() throws IOException {
        File outside = tmp.newFolder("outside");
        File keep = new File(outside, "keep.txt");
        assertTrue(keep.createNewFile());
        File dir = tmp.newFolder("p");
        File sub = FileOps.createDirectory(dir, "sub");
        FileOps.createFile(sub, "x.txt");
        Files.createSymbolicLink(new File(dir, "link").toPath(), outside.toPath());

        FileOps.delete(dir);

        assertFalse(dir.exists());
        assertTrue("the link target must survive", keep.exists());
    }

    @Test
    public void copyUsesUniqueNamesAndCopiesTrees() throws IOException {
        File dir = tmp.newFolder("p");
        File folder = FileOps.createDirectory(dir, "src");
        File inner = FileOps.createFile(folder, "a.js");
        Files.write(inner.toPath(), "x".getBytes());

        File copy1 = FileOps.copy(folder, dir);
        File copy2 = FileOps.copy(folder, dir);
        assertEquals("src copy", copy1.getName());
        assertEquals("src copy 2", copy2.getName());
        assertTrue(new File(copy1, "a.js").exists());

        File file = FileOps.createFile(dir, "notes.md");
        assertEquals("notes copy.md", FileOps.copy(file, dir).getName());
    }

    @Test
    public void refusesToCopyAFolderIntoItself() throws IOException {
        File dir = tmp.newFolder("p");
        File folder = FileOps.createDirectory(dir, "src");
        try {
            FileOps.copy(folder, folder);
            fail("expected IOException");
        } catch (IOException expected) {
            // fine
        }
    }
}
