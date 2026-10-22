package org.codirex.webide.workspace;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/** Exporting a project folder to a zip file and importing one. Pure Java. */
public final class ZipTransfer {

    /** Default limit on the unpacked size of an imported archive. */
    public static final long DEFAULT_MAX_BYTES = 2L * 1024 * 1024 * 1024;
    private static final int MAX_ENTRIES = 200_000;
    private static final String TEMP_DIR = ".import-tmp";

    private ZipTransfer() {
    }

    /**
     * Writes the folder into {@code out} as a zip, with everything inside one top-level folder
     * named {@code rootName} (like a repository download). {@link #unzip} removes that wrapper
     * again. Symbolic links are skipped. The stream is flushed but not closed.
     *
     * @param skipNodeModules leave out folders named node_modules, which can be huge and can be
     *                        restored with npm install
     * @return the number of files written
     */
    public static int zipDirectory(File dir, OutputStream out, boolean skipNodeModules, String rootName)
            throws IOException {
        final Path root = dir.toPath();
        final int[] count = {0};
        ZipOutputStream zip = new ZipOutputStream(new BufferedOutputStream(out));
        final byte[] buffer = new byte[64 * 1024];
        Files.walkFileTree(root, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path path, BasicFileAttributes attrs) throws IOException {
                if (path.equals(root)) {
                    zip.putNextEntry(new ZipEntry(rootName + "/"));
                    zip.closeEntry();
                    return FileVisitResult.CONTINUE;
                }
                if (skipNodeModules && path.getFileName().toString().equals("node_modules")) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                zip.putNextEntry(new ZipEntry(rootName + "/" + entryName(root, path) + "/"));
                zip.closeEntry();
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path path, BasicFileAttributes attrs) throws IOException {
                if (!attrs.isRegularFile()) {
                    return FileVisitResult.CONTINUE;
                }
                ZipEntry entry = new ZipEntry(rootName + "/" + entryName(root, path));
                entry.setTime(attrs.lastModifiedTime().toMillis());
                zip.putNextEntry(entry);
                try (InputStream in = new FileInputStream(path.toFile())) {
                    int n;
                    while ((n = in.read(buffer)) != -1) {
                        zip.write(buffer, 0, n);
                    }
                }
                zip.closeEntry();
                count[0]++;
                return FileVisitResult.CONTINUE;
            }
        });
        zip.finish();
        zip.flush();
        return count[0];
    }

    /**
     * Unpacks a zip into {@code targetDir}, which must exist and be empty. If the archive consists
     * of a single top-level folder (the usual shape of a downloaded repository), that folder's
     * contents are placed directly in {@code targetDir}.
     *
     * @return the number of files written
     * @throws IOException for unsafe entry names, too many entries, or more than {@code maxBytes}
     */
    public static int unzip(InputStream source, File targetDir, long maxBytes) throws IOException {
        File temp = new File(targetDir, TEMP_DIR);
        if (!temp.mkdir()) {
            throw new IOException("Cannot create a temporary folder");
        }
        int files = 0;
        try {
            files = extract(source, temp, maxBytes);
            File[] top = temp.listFiles();
            if (top == null || top.length == 0) {
                throw new IOException("The archive is empty");
            }
            File from = temp;
            if (top.length == 1 && top[0].isDirectory()) {
                from = top[0];
            }
            File[] children = from.listFiles();
            if (children != null) {
                for (File child : children) {
                    Files.move(child.toPath(), new File(targetDir, child.getName()).toPath());
                }
            }
        } finally {
            FileOps.delete(temp);
        }
        return files;
    }

    private static int extract(InputStream source, File into, long maxBytes) throws IOException {
        int files = 0;
        int entries = 0;
        long written = 0;
        byte[] buffer = new byte[64 * 1024];
        try (ZipInputStream zip = new ZipInputStream(new BufferedInputStream(source))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (++entries > MAX_ENTRIES) {
                    throw new IOException("The archive has too many entries");
                }
                String name = entry.getName();
                if (!isSafe(name)) {
                    throw new IOException("Unsafe path in archive: " + name);
                }
                File target = new File(into, name);
                if (entry.isDirectory()) {
                    if (!target.isDirectory() && !target.mkdirs()) {
                        throw new IOException("Cannot create " + name);
                    }
                    continue;
                }
                File parent = target.getParentFile();
                if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
                    throw new IOException("Cannot create " + parent.getName());
                }
                try (OutputStream out = new FileOutputStream(target)) {
                    int n;
                    while ((n = zip.read(buffer)) != -1) {
                        written += n;
                        if (written > maxBytes) {
                            throw new IOException("The archive is larger than the allowed size");
                        }
                        out.write(buffer, 0, n);
                    }
                }
                files++;
            }
        }
        return files;
    }

    /** True if an entry name stays inside the extraction folder. */
    static boolean isSafe(String name) {
        if (name == null || name.isEmpty() || name.startsWith("/")) {
            return false;
        }
        if (name.indexOf('\\') >= 0 || name.indexOf('\0') >= 0) {
            return false;
        }
        for (String segment : name.split("/")) {
            if (segment.equals("..")) {
                return false;
            }
        }
        return true;
    }

    private static String entryName(Path root, Path path) {
        return root.relativize(path).toString().replace(File.separatorChar, '/');
    }
}
