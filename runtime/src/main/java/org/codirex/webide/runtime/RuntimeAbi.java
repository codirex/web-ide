package org.codirex.webide.runtime;

import android.os.Build;
import android.os.Process;

/**
 * CPU architectures the runtime (bootstrap, Node.js, Git) is built for.
 *
 * <p>The architecture is chosen from the bitness of the running process, not only from the
 * device: a 64-bit device can run a 32-bit app process, and then needs the 32-bit bootstrap.
 */
public enum RuntimeAbi {

    AARCH64("aarch64", "arm64-v8a", true),
    ARM("arm", "armeabi-v7a", false),
    X86_64("x86_64", "x86_64", true),
    I686("i686", "x86", false);

    /** Architecture name used by the package build system. */
    public final String packageArch;

    /** ABI name used by Android. */
    public final String androidAbi;

    public final boolean is64Bit;

    RuntimeAbi(String packageArch, String androidAbi, boolean is64Bit) {
        this.packageArch = packageArch;
        this.androidAbi = androidAbi;
        this.is64Bit = is64Bit;
    }

    /**
     * Returns the ABI matching the current process, or {@code null} if the device reports
     * no ABI this project supports.
     */
    public static RuntimeAbi detect() {
        final boolean processIs64Bit = Process.is64Bit();
        for (String supported : Build.SUPPORTED_ABIS) {
            for (RuntimeAbi candidate : values()) {
                if (candidate.is64Bit == processIs64Bit && candidate.androidAbi.equals(supported)) {
                    return candidate;
                }
            }
        }
        return null;
    }
}
