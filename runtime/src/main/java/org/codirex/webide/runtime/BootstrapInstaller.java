package org.codirex.webide.runtime;

import android.system.ErrnoException;
import android.system.Os;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Installs a bootstrap archive into the runtime prefix.
 *
 * <p>The archive is extracted into a staging directory first and moved into place only when
 * everything succeeded, so a failed install never leaves a half-written prefix. Call from a
 * background thread.
 */
public final class BootstrapInstaller {

    /** Receives short progress messages. */
    public interface Listener {
        void onProgress(String message);
    }

    /** Outcome of an install or removal. */
    public static final class Result {
        public final boolean success;
        public final String message;

        private Result(boolean success, String message) {
            this.success = success;
            this.message = message;
        }
    }

    private static final int PROGRESS_EVERY_FILES = 250;
    private static final int OWNER_ONLY_EXECUTABLE = 0700;

    private final RuntimePaths paths;

    public BootstrapInstaller(RuntimePaths paths) {
        this.paths = paths;
    }

    /** Replaces any existing runtime with the contents of {@code source}. */
    public Result install(InputStream source, Listener listener) {
        if (!paths.matchesPrebuiltLayout()) {
            return failed("This install is not located at " + RuntimePaths.EXPECTED_PREFIX
                    + ". The runtime only works for the primary user.");
        }

        File staging = new File(paths.filesDir, "usr-staging");
        try {
            deleteRecursively(staging);
            if (!staging.mkdirs()) {
                return failed("Cannot create " + staging.getAbsolutePath());
            }

            List<BootstrapZip.SymlinkEntry> symlinks = new ArrayList<>();
            int files = extract(source, staging, symlinks, listener);
            if (files == 0) {
                throw new IOException("The archive contains no files");
            }

            report(listener, "Creating " + symlinks.size() + " links");
            for (BootstrapZip.SymlinkEntry link : symlinks) {
                if (!BootstrapZip.isSafeRelativePath(link.path)) {
                    throw new IOException("Unsafe link path in archive: " + link.path);
                }
                File linkFile = new File(staging, link.path);
                File parent = linkFile.getParentFile();
                if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
                    throw new IOException("Cannot create " + parent.getAbsolutePath());
                }
                Os.symlink(link.target, linkFile.getAbsolutePath());
            }

            ensureDirectory(new File(staging, "tmp"));
            ensureDirectory(paths.home);

            report(listener, "Moving into place");
            deleteRecursively(paths.prefix);
            if (!staging.renameTo(paths.prefix)) {
                throw new IOException("Cannot move " + staging.getName() + " to " + paths.prefix.getName());
            }
            return new Result(true, "Installed " + files + " files and " + symlinks.size() + " links.");
        } catch (IOException | ErrnoException | IllegalArgumentException e) {
            cleanQuietly(staging);
            return failed(e.getMessage());
        }
    }

    /** Removes the runtime prefix. The home directory is kept. */
    public Result uninstall() {
        try {
            deleteRecursively(new File(paths.filesDir, "usr-staging"));
            deleteRecursively(paths.prefix);
            return new Result(true, "Runtime removed.");
        } catch (IOException e) {
            return failed(e.getMessage());
        }
    }

    private int extract(InputStream source, File staging,
                        List<BootstrapZip.SymlinkEntry> symlinks, Listener listener)
            throws IOException, ErrnoException {
        int files = 0;
        byte[] buffer = new byte[64 * 1024];
        try (ZipInputStream zip = new ZipInputStream(new BufferedInputStream(source))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                String name = entry.getName();

                if (BootstrapZip.SYMLINKS_ENTRY.equals(name)) {
                    readSymlinks(zip, symlinks);
                    continue;
                }
                if (!BootstrapZip.isSafeRelativePath(name)) {
                    throw new IOException("Unsafe path in archive: " + name);
                }

                File target = new File(staging, name);
                if (entry.isDirectory()) {
                    ensureDirectory(target);
                    continue;
                }
                File parent = target.getParentFile();
                if (parent != null) {
                    ensureDirectory(parent);
                }
                try (OutputStream out = new FileOutputStream(target)) {
                    int n;
                    while ((n = zip.read(buffer)) != -1) {
                        out.write(buffer, 0, n);
                    }
                }
                if (BootstrapZip.needsExecutableBit(name)) {
                    Os.chmod(target.getAbsolutePath(), OWNER_ONLY_EXECUTABLE);
                }
                files++;
                if (files % PROGRESS_EVERY_FILES == 0) {
                    report(listener, "Extracted " + files + " files");
                }
            }
        }
        report(listener, "Extracted " + files + " files");
        return files;
    }

    private static void readSymlinks(ZipInputStream zip, List<BootstrapZip.SymlinkEntry> out)
            throws IOException {
        // Not closed on purpose: closing the reader would close the whole zip stream.
        BufferedReader reader = new BufferedReader(new InputStreamReader(zip, StandardCharsets.UTF_8));
        String line;
        while ((line = reader.readLine()) != null) {
            BootstrapZip.SymlinkEntry entry = BootstrapZip.parseSymlinkLine(line);
            if (entry != null) {
                out.add(entry);
            }
        }
    }

    private static void ensureDirectory(File dir) throws IOException {
        if (!dir.isDirectory() && !dir.mkdirs()) {
            throw new IOException("Cannot create " + dir.getAbsolutePath());
        }
    }

    /** Deletes a tree without following symbolic links. */
    static void deleteRecursively(File root) throws IOException {
        Path start = root.toPath();
        if (!Files.exists(start, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        Files.walkFileTree(start, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.delete(file);
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

    private static void cleanQuietly(File dir) {
        try {
            deleteRecursively(dir);
        } catch (IOException ignored) {
            // Best effort; the next install clears the staging directory again.
        }
    }

    private static void report(Listener listener, String message) {
        if (listener != null) {
            listener.onProgress(message);
        }
    }

    public static Result failed(String message) {
        return new Result(false, message == null ? "Unknown error" : message);
    }
}
