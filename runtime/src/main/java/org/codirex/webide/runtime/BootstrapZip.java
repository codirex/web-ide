package org.codirex.webide.runtime;

/**
 * Rules for reading a bootstrap archive. Pure Java so it can be unit-tested on the JVM.
 *
 * <p>The archive is a zip file. Symbolic links are not stored as zip entries; instead the
 * entry {@value #SYMLINKS_ENTRY} lists them, one per line, as {@code target←path}.
 */
public final class BootstrapZip {

    public static final String SYMLINKS_ENTRY = "SYMLINKS.txt";
    public static final char SYMLINK_SEPARATOR = '\u2190';

    /** One symbolic link to create: {@code path} (relative to the prefix) points to {@code target}. */
    public static final class SymlinkEntry {
        public final String target;
        public final String path;

        SymlinkEntry(String target, String path) {
            this.target = target;
            this.path = path;
        }
    }

    private BootstrapZip() {
    }

    /**
     * Parses one line of the symlink list.
     *
     * @return the entry, or {@code null} for a blank line
     * @throws IllegalArgumentException if the line is malformed
     */
    public static SymlinkEntry parseSymlinkLine(String line) {
        if (line == null || line.trim().isEmpty()) {
            return null;
        }
        int index = line.indexOf(SYMLINK_SEPARATOR);
        if (index <= 0 || index == line.length() - 1) {
            throw new IllegalArgumentException("Malformed symlink line: " + line);
        }
        return new SymlinkEntry(line.substring(0, index), line.substring(index + 1));
    }

    /**
     * True if {@code name} stays inside the extraction directory: not empty, not absolute, no
     * {@code ..} segment, no backslash and no NUL character.
     */
    public static boolean isSafeRelativePath(String name) {
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

    /** True for files that must be executable after extraction (zip files carry no mode bits). */
    public static boolean needsExecutableBit(String name) {
        String normalized = name.startsWith("./") ? name.substring(2) : name;
        return normalized.startsWith("bin/")
                || normalized.startsWith("libexec/")
                || normalized.startsWith("lib/apt/apt-helper")
                || normalized.startsWith("lib/apt/methods/");
    }
}
