package org.codirex.webide.workspace;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * File operations used by the project browser. Pure Java so they can be unit-tested on the JVM.
 * Deleting and copying never follow symbolic links.
 */
public final class FileOps {

    private static final int MAX_NAME_BYTES = 255;

    /** One row of a directory listing. */
    public static final class Entry {
        public final File file;
        public final boolean directory;
        public final boolean symlink;
        public final long size;
        public final long modified;

        Entry(File file, boolean directory, boolean symlink, long size, long modified) {
            this.file = file;
            this.directory = directory;
            this.symlink = symlink;
            this.size = size;
            this.modified = modified;
        }

        public String name() {
            return file.getName();
        }
    }

    private FileOps() {
    }

    /** Returns a message describing why {@code name} cannot be a file name, or {@code null} if it can. */
    public static String validateName(String name) {
        if (name == null || name.trim().isEmpty()) {
            return "Name cannot be empty";
        }
        if (name.equals(".") || name.equals("..")) {
            return "Name cannot be . or ..";
        }
        if (name.indexOf('/') >= 0 || name.indexOf('\0') >= 0) {
            return "Name cannot contain / or null characters";
        }
        if (name.getBytes(StandardCharsets.UTF_8).length > MAX_NAME_BYTES) {
            return "Name is too long";
        }
        return null;
    }

    /** True if {@code file} is {@code root} or lies below it, comparing normalized paths. */
    public static boolean isInside(File root, File file) {
        Path base = root.toPath().toAbsolutePath().normalize();
        Path other = file.toPath().toAbsolutePath().normalize();
        return other.startsWith(base);
    }

    /** Lists a directory: folders first, then files, each sorted by name ignoring case. */
    public static List<Entry> list(File dir) throws IOException {
        File[] children = dir.listFiles();
        if (children == null) {
            throw new IOException("Cannot read " + dir.getName());
        }
        List<Entry> entries = new ArrayList<>(children.length);
        for (File child : children) {
            boolean link = Files.isSymbolicLink(child.toPath());
            entries.add(new Entry(child, child.isDirectory(), link, child.length(), child.lastModified()));
        }
        Collections.sort(entries, (a, b) -> {
            if (a.directory != b.directory) {
                return a.directory ? -1 : 1;
            }
            return a.name().toLowerCase(Locale.ROOT).compareTo(b.name().toLowerCase(Locale.ROOT));
        });
        return entries;
    }

    public static File createFile(File dir, String name) throws IOException {
        File target = newChild(dir, name);
        if (!target.createNewFile()) {
            throw new IOException("Cannot create " + name);
        }
        return target;
    }

    public static File createDirectory(File dir, String name) throws IOException {
        File target = newChild(dir, name);
        if (!target.mkdir()) {
            throw new IOException("Cannot create " + name);
        }
        return target;
    }

    public static File rename(File file, String newName) throws IOException {
        String problem = validateName(newName);
        if (problem != null) {
            throw new IOException(problem);
        }
        File parent = file.getParentFile();
        if (parent == null) {
            throw new IOException("Cannot rename this item");
        }
        File target = new File(parent, newName);
        if (target.equals(file)) {
            return file;
        }
        if (exists(target)) {
            throw new IOException("\"" + newName + "\" already exists");
        }
        Files.move(file.toPath(), target.toPath());
        return target;
    }

    /** Deletes a file or a whole folder. Links are removed, never followed. */
    public static void delete(File file) throws IOException {
        Path start = file.toPath();
        if (!Files.exists(start, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        Files.walkFileTree(start, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path path, BasicFileAttributes attrs) throws IOException {
                Files.delete(path);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                if (exc != null) {
                    throw exc;
                }
                Files.delete(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    /** Copies {@code source} into {@code targetDir}, adding " copy" to the name if it is taken. */
    public static File copy(File source, File targetDir) throws IOException {
        if (source.isDirectory() && isInside(source, targetDir)) {
            throw new IOException("Cannot copy a folder into itself");
        }
        File target = uniqueChild(targetDir, source.getName());
        final Path from = source.toPath();
        final Path to = target.toPath();
        Files.walkFileTree(from, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                Files.createDirectories(to.resolve(from.relativize(dir).toString()));
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path path, BasicFileAttributes attrs) throws IOException {
                Files.copy(path, to.resolve(from.relativize(path).toString()),
                        LinkOption.NOFOLLOW_LINKS, StandardCopyOption.COPY_ATTRIBUTES);
                return FileVisitResult.CONTINUE;
            }
        });
        return target;
    }

    /** A child of {@code dir} that does not exist yet: {@code name}, then "name copy", "name copy 2", ... */
    public static File uniqueChild(File dir, String name) {
        File candidate = new File(dir, name);
        if (!exists(candidate)) {
            return candidate;
        }
        String base = name;
        String extension = "";
        int dot = name.lastIndexOf('.');
        if (dot > 0) {
            base = name.substring(0, dot);
            extension = name.substring(dot);
        }
        candidate = new File(dir, base + " copy" + extension);
        for (int i = 2; exists(candidate); i++) {
            candidate = new File(dir, base + " copy " + i + extension);
        }
        return candidate;
    }

    private static File newChild(File dir, String name) throws IOException {
        String problem = validateName(name);
        if (problem != null) {
            throw new IOException(problem);
        }
        File target = new File(dir, name);
        if (exists(target)) {
            throw new IOException("\"" + name + "\" already exists");
        }
        return target;
    }

    private static boolean exists(File file) {
        return Files.exists(file.toPath(), LinkOption.NOFOLLOW_LINKS);
    }
}
