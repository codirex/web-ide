package org.codirex.webide.runtime;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/**
 * Runs a shell command inside the runtime and collects its output. Call from a background thread.
 *
 * <p>This is for short non-interactive commands. Interactive sessions arrive with the terminal
 * module.
 */
public final class CommandRunner {

    private static final int MAX_OUTPUT_CHARS = 200_000;

    /** Outcome of one command. */
    public static final class Result {
        public final int exitCode;
        public final String output;
        public final boolean timedOut;
        public final boolean truncated;
        /** Set when the command could not be started at all. */
        public final String error;

        Result(int exitCode, String output, boolean timedOut, boolean truncated, String error) {
            this.exitCode = exitCode;
            this.output = output;
            this.timedOut = timedOut;
            this.truncated = truncated;
            this.error = error;
        }

        public String describe() {
            StringBuilder sb = new StringBuilder();
            if (error != null) {
                return "Could not start: " + error;
            }
            sb.append(timedOut ? "Timed out" : "Exit code " + exitCode);
            if (truncated) {
                sb.append(" (output truncated)");
            }
            if (!output.isEmpty()) {
                sb.append("\n\n").append(output.trim());
            }
            return sb.toString();
        }
    }

    private final RuntimePaths paths;
    private final RuntimeEnvironment environment;

    public CommandRunner(RuntimePaths paths, RuntimeEnvironment environment) {
        this.paths = paths;
        this.environment = environment;
    }

    public Result runShell(String command, long timeoutSeconds) {
        if (!environment.isInstalled()) {
            return new Result(-1, "", false, false, "The runtime is not installed.");
        }
        final StringBuilder output = new StringBuilder();
        final boolean[] truncated = {false};
        try {
            ProcessBuilder builder = new ProcessBuilder(
                    environment.shell().getAbsolutePath(), "-c", command);
            builder.directory(paths.home);
            builder.environment().clear();
            builder.environment().putAll(environment.build());
            builder.redirectErrorStream(true);
            final Process process = builder.start();

            Thread reader = new Thread(() -> {
                try (InputStream in = process.getInputStream();
                     Reader r = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                    char[] buffer = new char[4096];
                    int n;
                    while ((n = r.read(buffer)) != -1) {
                        synchronized (output) {
                            int room = MAX_OUTPUT_CHARS - output.length();
                            if (room > 0) {
                                output.append(buffer, 0, Math.min(n, room));
                            }
                            if (n > room) {
                                truncated[0] = true;
                            }
                        }
                    }
                } catch (IOException ignored) {
                    // The process ended or was killed; whatever was read is kept.
                }
            }, "command-output");
            reader.start();

            boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
            }
            reader.join(2000);
            synchronized (output) {
                return new Result(finished ? process.exitValue() : -1, output.toString(),
                        !finished, truncated[0], null);
            }
        } catch (IOException e) {
            return new Result(-1, "", false, false, e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new Result(-1, "", false, false, "Interrupted");
        }
    }
}
