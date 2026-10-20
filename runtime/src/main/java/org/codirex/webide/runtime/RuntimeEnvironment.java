package org.codirex.webide.runtime;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;

/** Builds the environment variables for every process started inside the runtime. */
public final class RuntimeEnvironment {

    /** Android variables that native tools expect to inherit from the app process. */
    private static final String[] INHERITED = {
            "ANDROID_ROOT",
            "ANDROID_DATA",
            "ANDROID_ART_ROOT",
            "ANDROID_I18N_ROOT",
            "ANDROID_RUNTIME_ROOT",
            "ANDROID_TZDATA_ROOT",
            "BOOTCLASSPATH",
    };

    private final RuntimePaths paths;

    public RuntimeEnvironment(RuntimePaths paths) {
        this.paths = paths;
    }

    /** The login shell used for commands. */
    public File shell() {
        return new File(paths.bin, "sh");
    }

    /** Bash, used for login shells (they run the one-time second-stage setup of the bootstrap). */
    public File bash() {
        return new File(paths.bin, "bash");
    }

    public boolean isInstalled() {
        return shell().exists();
    }

    public Map<String, String> build() {
        Map<String, String> env = new LinkedHashMap<>();
        env.put("HOME", paths.home.getAbsolutePath());
        env.put("PREFIX", paths.prefix.getAbsolutePath());
        env.put("PATH", paths.bin.getAbsolutePath() + ":/system/bin");
        env.put("TMPDIR", paths.tmp.getAbsolutePath());
        env.put("LANG", "en_US.UTF-8");
        env.put("TERM", "xterm-256color");
        env.put("COLORTERM", "truecolor");

        for (String name : INHERITED) {
            String value = System.getenv(name);
            if (value != null) {
                env.put(name, value);
            }
        }

        // Rewrites shebangs such as "#!/usr/bin/env node" to paths inside the prefix.
        // Current bootstraps ship libtermux-exec-ld-preload.so; older ones libtermux-exec.so.
        for (String library : new String[]{"libtermux-exec-ld-preload.so", "libtermux-exec.so"}) {
            File candidate = new File(paths.lib, library);
            if (candidate.exists()) {
                env.put("LD_PRELOAD", candidate.getAbsolutePath());
                break;
            }
        }
        return env;
    }
}
