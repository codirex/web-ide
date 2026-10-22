package org.codirex.webide;

import android.app.Activity;
import android.view.View;
import android.widget.TextView;

import com.google.android.material.textfield.TextInputEditText;

import org.codirex.webide.runtime.CommandRunner;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

/**
 * Build results: runs a shell command inside the project folder and shows what it printed.
 * The output appears when the command ends; live streaming comes with the build system.
 */
final class BuildPanel {

    private static final long TIMEOUT_SECONDS = 900;

    private final Activity activity;
    private final TextInputEditText commandInput;
    private final View runButton;
    private final TextView output;
    private final Supplier<File> workingDir;
    private final CommandRunner runner;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    BuildPanel(Activity activity, TextInputEditText commandInput, View runButton, TextView output,
               Supplier<File> workingDir, CommandRunner runner) {
        this.activity = activity;
        this.commandInput = commandInput;
        this.runButton = runButton;
        this.output = output;
        this.workingDir = workingDir;
        this.runner = runner;
        runButton.setOnClickListener(v -> run());
    }

    void shutdown() {
        executor.shutdownNow();
    }

    private void run() {
        final String command = String.valueOf(commandInput.getText()).trim();
        final File dir = workingDir.get();
        if (command.isEmpty() || dir == null) {
            return;
        }
        runButton.setEnabled(false);
        output.setText(R.string.exec_test_running);
        executor.execute(() -> {
            CommandRunner.Result result = runner.runShell(command, dir, TIMEOUT_SECONDS);
            activity.runOnUiThread(() -> {
                if (activity.isFinishing() || activity.isDestroyed()) {
                    return;
                }
                output.setText(result.describe());
                runButton.setEnabled(true);
            });
        });
    }
}
