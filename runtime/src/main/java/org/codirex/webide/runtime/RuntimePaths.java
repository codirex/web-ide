package org.codirex.webide.runtime;

import android.content.Context;
import android.system.ErrnoException;
import android.system.Os;
import android.system.StructStat;

import org.codirex.webide.core.WebIdeConstants;

import java.io.File;

/**
 * Locations of the runtime environment inside the app's private storage.
 *
 * <p>Prebuilt packages contain the absolute prefix path, so they only work when the app data
 * directory is {@code /data/data/org.codirex.webide} (the primary user).
 */
public final class RuntimePaths {

    /** App files directory as written inside the prebuilt packages. */
    public static final String EXPECTED_FILES =
            "/data/data/" + WebIdeConstants.APPLICATION_ID + "/files";

    /** Absolute prefix path baked into the prebuilt packages. */
    public static final String EXPECTED_PREFIX = EXPECTED_FILES + "/usr";

    public final File filesDir;
    public final File prefix;
    public final File bin;
    public final File lib;
    public final File etc;
    public final File tmp;
    public final File home;

    /** Scratch directory used by diagnostics; never touched by the bootstrap installer. */
    public final File diagnostics;

    private final boolean prebuiltLayout;

    public RuntimePaths(Context context) {
        File actual = context.getFilesDir();
        prebuiltLayout = sameDirectory(new File(EXPECTED_FILES), actual);
        // Use the path the packages were built for (/data/data/...) whenever it reaches the same
        // directory as the one Android reports (/data/user/0/...), so tools see consistent paths.
        filesDir = prebuiltLayout ? new File(EXPECTED_FILES) : actual;
        prefix = new File(filesDir, "usr");
        bin = new File(prefix, "bin");
        lib = new File(prefix, "lib");
        etc = new File(prefix, "etc");
        tmp = new File(prefix, "tmp");
        home = new File(filesDir, "home");
        diagnostics = new File(filesDir, "diagnostics");
    }

    /**
     * True when this install lives at the path the prebuilt packages expect. False for secondary
     * users and work profiles, where prebuilt packages cannot run.
     */
    public boolean matchesPrebuiltLayout() {
        return prebuiltLayout;
    }

    /**
     * Compares the device and inode of both paths instead of comparing path strings, because
     * {@code /data/data} is a link to {@code /data/user/0} and the app is not always allowed to
     * resolve that link itself.
     */
    private static boolean sameDirectory(File a, File b) {
        try {
            StructStat first = Os.stat(a.getPath());
            StructStat second = Os.stat(b.getAbsolutePath());
            return first.st_dev == second.st_dev && first.st_ino == second.st_ino;
        } catch (ErrnoException e) {
            return false;
        }
    }

    public boolean isPrefixPresent() {
        return prefix.isDirectory();
    }
}
