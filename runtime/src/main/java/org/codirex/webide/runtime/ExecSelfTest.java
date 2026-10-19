package org.codirex.webide.runtime;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/**
 * Checks the core assumption of this project: the app can execute a file that it wrote into its
 * own data directory. Works only while the app targets API 28 or lower.
 *
 * <p>The test writes a tiny shell script, marks it executable and runs it directly. Call
 * {@link #run(RuntimePaths)} from a background thread.
 */
public final class ExecSelfTest {

    private static final String MARKER = "webide-exec-ok";

    /** Outcome of one test run. */
    public static final class Result {
        public final boolean success;
        public final int exitCode;
        public final String output;
        public final String error;

        private Result(boolean success, int exitCode, String output, String error) {
            this.success = success;
            this.exitCode = exitCode;
            this.output = output;
            this.error = error;
        }

        public String describe() {
            if (success) {
                return "Passed. The app can run files from its data directory.\n"
                        + "Output: " + output.trim();
            }
            StringBuilder sb = new StringBuilder("Failed.");
            if (error != null) {
                sb.append('\n').append(error);
            }
            if (exitCode >= 0) {
                sb.append("\nExit code: ").append(exitCode);
            }
            if (output != null && !output.trim().isEmpty()) {
                sb.append("\nOutput: ").append(output.trim());
            }
            return sb.toString();
        }
    }

    private ExecSelfTest() {
    }

    public static Result run(RuntimePaths paths) {
        File dir = paths.diagnostics;
        if (!dir.isDirectory() && !dir.mkdirs()) {
            return failure(-1, "", "Cannot create " + dir.getAbsolutePath());
        }
        File script = new File(dir, "exec-self-test.sh");
        try {
            try (FileOutputStream out = new FileOutputStream(script)) {
                String body = "#!/system/bin/sh\necho " + MARKER + " uid=$(id -u)\n";
                out.write(body.getBytes(StandardCharsets.UTF_8));
            }
            if (!script.setExecutable(true, true)) {
                return failure(-1, "", "Cannot mark " + script.getName() + " as executable");
            }

            Process process = new ProcessBuilder(script.getAbsolutePath())
                    .redirectErrorStream(true)
                    .start();

            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append('\n');
                }
            }

            if (!process.waitFor(5, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return failure(-1, output.toString(), "Timed out after 5 seconds");
            }
            int exit = process.exitValue();
            boolean ok = exit == 0 && output.toString().contains(MARKER);
            return new Result(ok, exit, output.toString(), ok ? null : "Unexpected result");
        } catch (IOException e) {
            return failure(-1, "", e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return failure(-1, "", "Interrupted");
        } finally {
            //noinspection ResultOfMethodCallIgnored
            script.delete();
        }
    }

    private static Result failure(int exit, String output, String error) {
        return new Result(false, exit, output, error);
    }
}
