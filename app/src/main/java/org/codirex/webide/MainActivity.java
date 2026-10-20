package org.codirex.webide;

import android.content.pm.ApplicationInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.View;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import org.codirex.webide.databinding.ActivityMainBinding;
import org.codirex.webide.runtime.BootstrapInstaller;
import org.codirex.webide.runtime.CommandRunner;
import org.codirex.webide.runtime.ExecSelfTest;
import org.codirex.webide.runtime.RuntimeAbi;
import org.codirex.webide.runtime.RuntimeEnvironment;
import org.codirex.webide.runtime.RuntimePaths;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Phase 1 screen: environment report, exec self-test, and runtime install and command runner.
 * Replaced by the real workspace screen in a later phase.
 */
public class MainActivity extends AppCompatActivity {

    private interface InstallAction {
        BootstrapInstaller.Result run();
    }

    private ActivityMainBinding binding;
    private RuntimePaths paths;
    private RuntimeEnvironment environment;
    private BootstrapInstaller installer;
    private CommandRunner commandRunner;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private final ActivityResultLauncher<String[]> pickBootstrap = registerForActivityResult(
            new ActivityResultContracts.OpenDocument(), uri -> {
                if (uri != null) {
                    installFromUri(uri);
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        setSupportActionBar(binding.toolbar);

        paths = new RuntimePaths(this);
        environment = new RuntimeEnvironment(paths);
        installer = new BootstrapInstaller(paths);
        commandRunner = new CommandRunner(paths, environment);

        binding.runExecTest.setOnClickListener(v -> runExecTest());
        binding.installFromFile.setOnClickListener(v -> pickBootstrap.launch(new String[]{"*/*"}));
        binding.removeRuntime.setOnClickListener(v -> removeRuntime());
        binding.runCommand.setOnClickListener(v -> runCommand());

        final String bundled = bundledBootstrapAsset();
        if (bundled != null) {
            binding.installBundled.setVisibility(View.VISIBLE);
            binding.installBundled.setOnClickListener(v -> installFromAsset(bundled));
        }

        refreshReport();
        binding.runtimeStatus.setText(environment.isInstalled()
                ? R.string.runtime_installed : R.string.runtime_not_installed);
    }

    @Override
    protected void onDestroy() {
        executor.shutdownNow();
        super.onDestroy();
    }

    // --- exec self-test -------------------------------------------------------------------

    private void runExecTest() {
        binding.runExecTest.setEnabled(false);
        binding.execResult.setText(R.string.exec_test_running);
        executor.execute(() -> {
            ExecSelfTest.Result result = ExecSelfTest.run(paths);
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                binding.execResult.setText(result.describe());
                binding.runExecTest.setEnabled(true);
            });
        });
    }

    // --- runtime install ------------------------------------------------------------------

    private String bundledBootstrapAsset() {
        RuntimeAbi abi = RuntimeAbi.detect();
        if (abi == null) {
            return null;
        }
        String name = "bootstrap-" + abi.packageArch + ".zip";
        try {
            String[] files = getAssets().list("bootstrap");
            if (files != null) {
                for (String file : files) {
                    if (file.equals(name)) {
                        return "bootstrap/" + name;
                    }
                }
            }
        } catch (IOException ignored) {
            // No bundled archive.
        }
        return null;
    }

    private void installFromAsset(String assetPath) {
        runInstall(getString(R.string.reading_file), true, () -> {
            try (InputStream in = getAssets().open(assetPath)) {
                return installer.install(in, this::postProgress);
            } catch (IOException e) {
                return BootstrapInstaller.failed(e.getMessage());
            }
        });
    }

    private void installFromUri(Uri uri) {
        runInstall(getString(R.string.reading_file), true, () -> {
            try (InputStream in = getContentResolver().openInputStream(uri)) {
                if (in == null) {
                    return BootstrapInstaller.failed("Cannot open the selected file");
                }
                return installer.install(in, this::postProgress);
            } catch (IOException | SecurityException e) {
                return BootstrapInstaller.failed(e.getMessage());
            }
        });
    }

    private void removeRuntime() {
        runInstall(getString(R.string.removing_runtime), false, installer::uninstall);
    }

    private void runInstall(String label, boolean finishSetup, InstallAction action) {
        setBusy(true);
        binding.runtimeStatus.setText(label);
        executor.execute(() -> {
            BootstrapInstaller.Result result = action.run();
            String setupReport = "";
            if (result.success && finishSetup) {
                postProgress(getString(R.string.finishing_setup));
                // First login shell: runs the bootstrap's second-stage setup.
                CommandRunner.Result setup = commandRunner.runLoginShell("echo setup-finished", 300);
                setupReport = "\n\nSetup: " + setup.describe();
            }
            final String message = (result.success ? "Done. " : "Failed. ") + result.message + setupReport;
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                setBusy(false);
                refreshReport();
                binding.runtimeStatus.setText(message);
            });
        });
    }

    private void postProgress(String message) {
        runOnUiThread(() -> {
            if (!isFinishing() && !isDestroyed()) {
                binding.runtimeStatus.setText(message);
            }
        });
    }

    // --- command runner -------------------------------------------------------------------

    private void runCommand() {
        final String command = String.valueOf(binding.commandInput.getText()).trim();
        if (command.isEmpty()) {
            return;
        }
        setBusy(true);
        binding.commandOutput.setText(R.string.exec_test_running);
        executor.execute(() -> {
            CommandRunner.Result result = commandRunner.runShell(command, 120);
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                binding.commandOutput.setText(result.describe());
                setBusy(false);
            });
        });
    }

    private void setBusy(boolean busy) {
        binding.installFromFile.setEnabled(!busy);
        binding.installBundled.setEnabled(!busy);
        binding.removeRuntime.setEnabled(!busy);
        binding.runCommand.setEnabled(!busy);
    }

    // --- report ---------------------------------------------------------------------------

    private void refreshReport() {
        binding.environmentInfo.setText(buildEnvironmentReport());
    }

    private String buildEnvironmentReport() {
        ApplicationInfo appInfo = getApplicationInfo();
        RuntimeAbi abi = RuntimeAbi.detect();
        StringBuilder sb = new StringBuilder();
        sb.append("Android ").append(Build.VERSION.RELEASE)
                .append(" (API ").append(Build.VERSION.SDK_INT).append(")\n");
        sb.append("Device: ").append(Build.MANUFACTURER).append(' ').append(Build.MODEL).append('\n');
        sb.append("App targetSdk: ").append(appInfo.targetSdkVersion).append('\n');
        sb.append("Process: ").append(android.os.Process.is64Bit() ? "64-bit" : "32-bit").append('\n');
        sb.append("Device ABIs: ").append(String.join(", ", Build.SUPPORTED_ABIS)).append('\n');
        sb.append("Runtime ABI: ")
                .append(abi == null ? "not supported" : abi.packageArch + " (" + abi.androidAbi + ")")
                .append("\n\n");
        sb.append("Files dir: ").append(paths.filesDir.getAbsolutePath()).append('\n');
        sb.append("Expected prefix: ").append(RuntimePaths.EXPECTED_PREFIX).append('\n');
        sb.append("Matches prebuilt layout: ").append(paths.matchesPrebuiltLayout() ? "yes" : "no").append('\n');
        sb.append("Prefix installed: ").append(paths.isPrefixPresent() ? "yes" : "no");
        return sb.toString();
    }
}
