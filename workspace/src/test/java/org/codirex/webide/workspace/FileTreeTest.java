package org.codirex.webide.workspace;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

public class FileTreeTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private static List<String> names(List<FileTree.Row> rows) {
        List<String> result = new ArrayList<>();
        for (FileTree.Row row : rows) {
            result.add(row.depth + ":" + row.name());
        }
        return result;
    }

    private File project() throws IOException {
        File root = tmp.newFolder("project");
        File src = FileOps.createDirectory(root, "src");
        FileOps.createDirectory(src, "components");
        FileOps.createFile(src, "main.js");
        FileOps.createFile(root, "package.json");
        return root;
    }

    @Test
    public void showsOnlyTheTopLevelUntilFoldersAreOpened() throws IOException {
        FileTree tree = new FileTree(project());
        assertEquals(List.of("0:src", "0:package.json"), names(tree.rows()));
    }

    @Test
    public void openFoldersShowTheirChildrenIndented() throws IOException {
        File root = project();
        FileTree tree = new FileTree(root);
        assertTrue(tree.toggle(new File(root, "src")));
        assertEquals(List.of("0:src", "1:components", "1:main.js", "0:package.json"), names(tree.rows()));
        assertTrue(tree.rows().get(0).expanded);
    }

    @Test
    public void closingAFolderKeepsTheStateOfItsChildren() throws IOException {
        File root = project();
        FileTree tree = new FileTree(root);
        File src = new File(root, "src");
        File components = new File(src, "components");
        FileOps.createFile(components, "Button.js");
        tree.expand(src);
        tree.expand(components);
        assertEquals(5, tree.rows().size());

        assertFalse(tree.toggle(src));
        assertEquals(2, tree.rows().size());

        tree.toggle(src);
        assertEquals(5, tree.rows().size());
    }

    @Test
    public void revealOpensAllParents() throws IOException {
        File root = project();
        File deep = new File(new File(root, "src"), "components");
        File file = FileOps.createFile(deep, "Button.js");
        FileTree tree = new FileTree(root);
        tree.reveal(file);
        assertTrue(tree.isExpanded(new File(root, "src")));
        assertTrue(tree.isExpanded(deep));
        assertTrue(names(tree.rows()).contains("2:Button.js"));
    }

    @Test
    public void forgetClosesAFolderAndItsSubfolders() throws IOException {
        File root = project();
        FileTree tree = new FileTree(root);
        File src = new File(root, "src");
        tree.expand(src);
        tree.expand(new File(src, "components"));
        tree.forget(src);
        assertFalse(tree.isExpanded(src));
        assertFalse(tree.isExpanded(new File(src, "components")));
    }

    @Test
    public void linkedFoldersAreShownButNeverOpened() throws IOException {
        File root = project();
        File outside = tmp.newFolder("outside");
        FileOps.createFile(outside, "secret.txt");
        File link = new File(root, "link");
        Files.createSymbolicLink(link.toPath(), outside.toPath());

        FileTree tree = new FileTree(root);
        tree.expand(link);
        List<String> rows = names(tree.rows());
        assertTrue(rows.contains("0:link"));
        assertFalse(rows.contains("1:secret.txt"));
    }

    @Test
    public void collapseAllClosesEverything() throws IOException {
        File root = project();
        FileTree tree = new FileTree(root);
        tree.expand(new File(root, "src"));
        tree.collapseAll();
        assertEquals(2, tree.rows().size());
    }
}
