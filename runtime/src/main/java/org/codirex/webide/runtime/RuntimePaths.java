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

    /** Absolute prefix path baked into the prebuilt packages. */
    public static final String EXPECTED_PREFIX =
            "/data/data/" + WebIdeConstants.APPLICATION_ID + "/files/usr";

    public final File filesDir;
    public final File prefix;
    public final File bin;
    public final File lib;
    public final File etc;
    public final File tmp;
    public final File home;

    /** Scratch directory used by diagnostics; never touched by the bootstrap installer. */
    public final File diagnostics;

    public RuntimePaths(Context context) {
        filesDir = context.getFilesDir();
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
     *
     * <p>Compares the device and inode of both paths instead of comparing path strings, because
     * {@code /data/data} is a link to {@code /data/user/0} and the app is not always allowed to
     * resolve that link itself.
     */
    public boolean matchesPrebuiltLayout() {
        try {
            File expectedFiles = new File(EXPECTED_PREFIX).getParentFile();
            if (expectedFiles == null) {
                return false;
            }
            StructStat expected = Os.stat(expectedFiles.getPath());
            StructStat actual = Os.stat(filesDir.getAbsolutePath());
            return expected.st_dev == actual.st_dev && expected.st_ino == actual.st_ino;
        } catch (ErrnoException e) {
            return false;
        }
    }

    public boolean isPrefixPresent() {
        return prefix.isDirectory();
    }
}
