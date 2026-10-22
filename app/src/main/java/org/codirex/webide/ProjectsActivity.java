package org.codirex.webide;

import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.PopupMenu;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.snackbar.Snackbar;

import org.codirex.webide.databinding.ActivityProjectsBinding;
import org.codirex.webide.runtime.RuntimePaths;
import org.codirex.webide.workspace.FileOps;
import org.codirex.webide.workspace.Project;
import org.codirex.webide.workspace.ProjectRepository;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;

/** Home screen: the projects of the workspace. */
public class ProjectsActivity extends AppCompatActivity {

    private ActivityProjectsBinding binding;
    private ProjectRepository repository;
    private Project exportTarget;

    private final ActivityResultLauncher<String[]> importLauncher = registerForActivityResult(
            new ActivityResultContracts.OpenDocument(), uri -> {
                if (uri != null) {
                    importZip(uri);
                }
            });

    private final ActivityResultLauncher<String> exportLauncher = registerForActivityResult(
            new ActivityResultContracts.CreateDocument("application/zip"), uri -> {
                if (uri != null && exportTarget != null) {
                    exportZip(exportTarget, uri);
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityProjectsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        setSupportActionBar(binding.toolbar);

        File root = new File(new RuntimePaths(this).home, "projects");
        repository = ProjectRepository.get(this, root);

        ProjectAdapter adapter = new ProjectAdapter(new ProjectAdapter.Listener() {
            @Override
            public void onOpen(Project project) {
                startActivity(new Intent(ProjectsActivity.this, ProjectActivity.class)
                        .putExtra(ProjectActivity.EXTRA_PROJECT_ID, project.id));
            }

            @Override
            public void onMenu(Project project, View anchor) {
                showProjectMenu(project, anchor);
            }
        });
        binding.projectList.setLayoutManager(new LinearLayoutManager(this));
        binding.projectList.setAdapter(adapter);

        repository.observeAll().observe(this, (List<Project> projects) -> {
            adapter.submitList(projects);
            binding.emptyView.setVisibility(projects == null || projects.isEmpty() ? View.VISIBLE : View.GONE);
        });

        binding.newProject.setOnClickListener(v -> askNewProject());
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Picks up folders created outside the app screens, for example with git clone.
        repository.sync();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.projects_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_import_zip) {
            importLauncher.launch(new String[]{"*/*"});
        } else if (id == R.id.action_runtime_setup) {
            startActivity(new Intent(this, MainActivity.class));
        } else {
            return super.onOptionsItemSelected(item);
        }
        return true;
    }

    // --- actions ---------------------------------------------------------------------------

    private void askNewProject() {
        Dialogs.promptText(this, R.string.new_project, "", R.string.create, FileOps::validateName, name ->
                repository.create(name, new ProjectRepository.Callback<Project>() {
                    @Override
                    public void onSuccess(Project project) {
                        startActivity(new Intent(ProjectsActivity.this, ProjectActivity.class)
                                .putExtra(ProjectActivity.EXTRA_PROJECT_ID, project.id));
                    }

                    @Override
                    public void onError(String message) {
                        show(message);
                    }
                }));
    }

    private void showProjectMenu(Project project, View anchor) {
        PopupMenu menu = new PopupMenu(this, anchor);
        menu.getMenuInflater().inflate(R.menu.project_item_menu, menu.getMenu());
        menu.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == R.id.action_open_terminal) {
                startActivity(new Intent(this, TerminalActivity.class)
                        .putExtra(TerminalActivity.EXTRA_CWD, project.path));
            } else if (id == R.id.action_rename) {
                askRename(project);
            } else if (id == R.id.action_export) {
                exportTarget = project;
                exportLauncher.launch(project.name + ".zip");
            } else if (id == R.id.action_delete) {
                Dialogs.confirm(this, getString(R.string.delete_project_title),
                        getString(R.string.delete_project_message, project.name), R.string.delete,
                        () -> repository.delete(project, new ProjectRepository.Callback<Void>() {
                            @Override
                            public void onSuccess(Void value) {
                            }

                            @Override
                            public void onError(String message) {
                                show(message);
                            }
                        }));
            } else {
                return false;
            }
            return true;
        });
        menu.show();
    }

    private void askRename(Project project) {
        Dialogs.promptText(this, R.string.rename, project.name, R.string.rename, FileOps::validateName, name ->
                repository.rename(project, name, new ProjectRepository.Callback<Project>() {
                    @Override
                    public void onSuccess(Project value) {
                        // The list updates itself from the database.
                    }

                    @Override
                    public void onError(String message) {
                        show(message);
                    }
                }));
    }

    private void importZip(Uri uri) {
        InputStream in;
        try {
            in = getContentResolver().openInputStream(uri);
        } catch (IOException | SecurityException e) {
            show(getString(R.string.import_failed, e.getMessage()));
            return;
        }
        if (in == null) {
            show(getString(R.string.import_failed, uri.toString()));
            return;
        }
        final InputStream stream = in;
        repository.importZip(displayName(uri), stream, new ProjectRepository.Callback<Project>() {
            @Override
            public void onSuccess(Project project) {
                close(stream);
                show(getString(R.string.imported_project, project.name));
            }

            @Override
            public void onError(String message) {
                close(stream);
                show(getString(R.string.import_failed, message));
            }
        });
    }

    private void exportZip(Project project, Uri uri) {
        OutputStream out;
        try {
            out = getContentResolver().openOutputStream(uri);
        } catch (IOException | SecurityException e) {
            show(e.getMessage());
            return;
        }
        if (out == null) {
            return;
        }
        final OutputStream stream = out;
        repository.exportZip(project, stream, new ProjectRepository.Callback<Integer>() {
            @Override
            public void onSuccess(Integer files) {
                close(stream);
                show(getString(R.string.exported_files, files));
            }

            @Override
            public void onError(String message) {
                close(stream);
                show(message);
            }
        });
    }

    /** The name of a picked file without ".zip", used as the project name. */
    private String displayName(Uri uri) {
        String name = null;
        try (Cursor cursor = getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME},
                null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                name = cursor.getString(0);
            }
        } catch (RuntimeException ignored) {
            // Fall back to the default name.
        }
        if (name != null && name.toLowerCase().endsWith(".zip")) {
            name = name.substring(0, name.length() - 4);
        }
        if (name == null || FileOps.validateName(name) != null) {
            name = getString(R.string.imported_default_name);
        }
        return name;
    }

    private static void close(Closeable closeable) {
        try {
            closeable.close();
        } catch (IOException ignored) {
            // Nothing more to do.
        }
    }

    private void show(String message) {
        Snackbar.make(binding.getRoot(), message, Snackbar.LENGTH_LONG).show();
    }
}
