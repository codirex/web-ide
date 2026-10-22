package org.codirex.webide.workspace;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The expandable file tree of a project, as a flat list of rows. It remembers which folders are
 * open. Pure Java. Not thread safe: use it from one thread (the screen uses a single background
 * thread for it).
 */
public final class FileTree {

    /** One visible row of the tree. */
    public static final class Row {
        public final File file;
        public final int depth;
        public final boolean directory;
        public final boolean expanded;
        public final boolean symlink;

        Row(File file, int depth, boolean directory, boolean expanded, boolean symlink) {
            this.file = file;
            this.depth = depth;
            this.directory = directory;
            this.expanded = expanded;
            this.symlink = symlink;
        }

        public String name() {
            return file.getName();
        }
    }

    private static final int MAX_ROWS = 20_000;
    private static final int MAX_DEPTH = 64;

    private final File root;
    private final Set<String> expanded = new HashSet<>();

    public FileTree(File root) {
        this.root = root;
    }

    public File root() {
        return root;
    }

    public boolean isExpanded(File dir) {
        return expanded.contains(dir.getPath());
    }

    public void expand(File dir) {
        expanded.add(dir.getPath());
    }

    public void collapse(File dir) {
        expanded.remove(dir.getPath());
    }

    /** Opens a closed folder or closes an open one. Returns whether it is open afterwards. */
    public boolean toggle(File dir) {
        if (!expanded.remove(dir.getPath())) {
            expanded.add(dir.getPath());
            return true;
        }
        return false;
    }

    public void collapseAll() {
        expanded.clear();
    }

    /** Opens every folder between the root and {@code file}, so that the file becomes visible. */
    public void reveal(File file) {
        File parent = file.getParentFile();
        while (parent != null && FileOps.isInside(root, parent) && !parent.equals(root)) {
            expanded.add(parent.getPath());
            parent = parent.getParentFile();
        }
    }

    /** Forgets the open state of a deleted or renamed folder and everything below it. */
    public void forget(File dir) {
        String prefix = dir.getPath() + File.separator;
        expanded.removeIf(path -> path.equals(dir.getPath()) || path.startsWith(prefix));
    }

    /**
     * Reads the folders from disk and returns the visible rows: the entries of the root, and below
     * each open folder its own entries. Folders that cannot be read count as empty. Symbolic links to
     * folders are shown but never opened, which avoids endless loops.
     */
    public List<Row> rows() {
        List<Row> rows = new ArrayList<>();
        collect(root, 0, rows);
        return rows;
    }

    private void collect(File dir, int depth, List<Row> out) {
        if (depth > MAX_DEPTH) {
            return;
        }
        List<FileOps.Entry> entries;
        try {
            entries = FileOps.list(dir);
        } catch (IOException e) {
            return;
        }
        for (FileOps.Entry entry : entries) {
            if (out.size() >= MAX_ROWS) {
                return;
            }
            boolean open = entry.directory && !entry.symlink && expanded.contains(entry.file.getPath());
            out.add(new Row(entry.file, depth, entry.directory, open, entry.symlink));
            if (open) {
                collect(entry.file, depth + 1, out);
            }
        }
    }
}
