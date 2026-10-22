package org.codirex.webide;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Rect;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.LinearLayout;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.PopupMenu;
import androidx.core.view.GravityCompat;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.tabs.TabLayout;

import org.codirex.webide.databinding.ActivityProjectBinding;
import org.codirex.webide.editor.TextMateSetup;
import org.codirex.webide.runtime.CommandRunner;
import org.codirex.webide.runtime.RuntimeEnvironment;
import org.codirex.webide.runtime.RuntimePaths;
import org.codirex.webide.workspace.FileOps;
import org.codirex.webide.workspace.FileTree;
import org.codirex.webide.workspace.Project;
import org.codirex.webide.workspace.ProjectRepository;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * A project: the menu button opens a drawer with a navigation rail (files, terminal, close) and the
 * file tree; a bottom sheet holds build results, live view and problems.
 */
public class ProjectActivity extends AppCompatActivity {

    public static final String EXTRA_PROJECT_ID = "org.codirex.webide.extra.PROJECT_ID";

    private static final int TAB_BUILD = 0;
    private static final int TAB_LIVE = 1;
    private static final int TAB_PROBLEMS = 2;

    private interface IoWork {
        void run() throws IOException;
    }

    private ActivityProjectBinding binding;
    private ProjectRepository repository;
    private TreeAdapter adapter;
    private BottomSheetBehavior<LinearLayout> sheet;
    private LiveViewPanel livePanel;
    private BuildPanel buildPanel;
    private EditorTabs editorTabs;
    private boolean keyboardVisible;

    private Project project;
    private FileTree tree;
    private File selectedDir;
    private File selectedFile;

    /** Runs everything that touches the tree, one job at a time. */
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityProjectBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        RuntimePaths paths = new RuntimePaths(this);
        repository = ProjectRepository.get(this, new File(paths.home, "projects"));

        setUpToolbar();
        setUpDrawer();
        setUpSheet(paths);
        setUpEditor();
        setUpKeyboardWatcher();

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (binding.drawerLayout.isDrawerOpen(GravityCompat.START)) {
                    binding.drawerLayout.closeDrawer(GravityCompat.START);
                } else if (sheet.getState() != BottomSheetBehavior.STATE_COLLAPSED) {
                    sheet.setState(BottomSheetBehavior.STATE_COLLAPSED);
                } else {
                    setEnabled(false);
                    getOnBackPressedDispatcher().onBackPressed();
                }
            }
        });

        long id = getIntent().getLongExtra(EXTRA_PROJECT_ID, -1);
        repository.findById(id, new ProjectRepository.Callback<Project>() {
            @Override
            public void onSuccess(Project value) {
                project = value;
                tree = new FileTree(value.dir());
                selectedDir = value.dir();
                binding.toolbar.setTitle(value.name);
                binding.treeTitle.setText(value.name);
                updateTarget();
                repository.touch(value.id);
                publishRowsAsync();
            }

            @Override
            public void onError(String message) {
                show(message);
                finish();
            }
        });
    }

    @Override
    protected void onPause() {
        // Unsaved work is written when the screen leaves the foreground, so it survives the app being killed.
        editorTabs.saveAll();
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        editorTabs.checkDiskChanges();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        TextMateSetup.applyTheme(isNight());
    }

    @Override
    protected void onDestroy() {
        editorTabs.release();
        executor.shutdownNow();
        if (buildPanel != null) {
            buildPanel.shutdown();
        }
        if (livePanel != null) {
            livePanel.destroy();
        }
        super.onDestroy();
    }

    // --- set up ----------------------------------------------------------------------------

    private void setUpToolbar() {
        binding.toolbar.setNavigationOnClickListener(v -> binding.drawerLayout.openDrawer(GravityCompat.START));
        binding.toolbar.inflateMenu(R.menu.project_menu);
        binding.toolbar.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (project == null) {
                return false;
            }
            if (id == R.id.action_save) {
                editorTabs.saveCurrent();
            } else if (id == R.id.action_undo) {
                editorTabs.undo();
            } else if (id == R.id.action_redo) {
                editorTabs.redo();
            } else if (id == R.id.action_word_wrap) {
                editorTabs.toggleWordWrap();
                item.setChecked(editorTabs.isWordWrap());
            } else if (id == R.id.action_close_tab) {
                editorTabs.closeCurrent();
            } else if (id == R.id.action_new_file) {
                askNewFile(selectedDir);
            } else if (id == R.id.action_new_folder) {
                askNewFolder(selectedDir);
            } else if (id == R.id.action_open_terminal) {
                openTerminal(selectedDir);
            } else if (id == R.id.action_refresh) {
                publishRowsAsync();
            } else {
                return false;
            }
            return true;
        });
    }

    private void setUpEditor() {
        editorTabs = new EditorTabs(this, new EditorTabs.Host() {
            @Override
            public void onStateChanged() {
                updateEditorUi();
            }

            @Override
            public void show(String message) {
                ProjectActivity.this.show(message);
            }

            @Override
            public boolean isNightMode() {
                return isNight();
            }
        }, binding.fileTabs, binding.editorHost, binding.editorPlaceholder, binding.symbolBar, binding.symbolKeys);
        updateEditorUi();
    }

    /** Enables the editor actions that apply, and shows the open file's path under the title. */
    private void updateEditorUi() {
        android.view.Menu menu = binding.toolbar.getMenu();
        boolean has = editorTabs.hasCurrent();
        setEnabled(menu.findItem(R.id.action_save), has && editorTabs.isCurrentModified());
        setEnabled(menu.findItem(R.id.action_undo), has && editorTabs.canUndo());
        setEnabled(menu.findItem(R.id.action_redo), has && editorTabs.canRedo());
        setEnabled(menu.findItem(R.id.action_close_tab), has);
        File file = editorTabs.currentFile();
        binding.toolbar.setSubtitle(file == null || project == null ? null : relative(file));
        adapter.setSelectedPath(file == null ? null : file.getPath());
    }

    private static void setEnabled(android.view.MenuItem item, boolean enabled) {
        if (item != null) {
            item.setEnabled(enabled);
            if (item.getIcon() != null) {
                item.getIcon().setAlpha(enabled ? 255 : 90);
            }
        }
    }

    private boolean isNight() {
        int mode = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return mode == Configuration.UI_MODE_NIGHT_YES;
    }

    /**
     * With the keyboard open there is little room, so the bottom sheet is hidden while it is up.
     */
    private void setUpKeyboardWatcher() {
        final View root = binding.getRoot();
        root.getViewTreeObserver().addOnGlobalLayoutListener(() -> {
            Rect visible = new Rect();
            root.getWindowVisibleDisplayFrame(visible);
            int screenHeight = root.getRootView().getHeight();
            boolean shown = screenHeight - visible.bottom > screenHeight * 0.15f;
            if (shown != keyboardVisible) {
                keyboardVisible = shown;
                binding.bottomSheet.setVisibility(shown ? View.GONE : View.VISIBLE);
                int peek = shown ? 0 : getResources().getDimensionPixelSize(R.dimen.sheet_peek_height);
                binding.mainContent.setPadding(0, 0, 0, peek);
            }
        });
    }

    private void setUpDrawer() {
        adapter = new TreeAdapter(new TreeAdapter.Listener() {
            @Override
            public void onClick(FileTree.Row row) {
                onRowClick(row);
            }

            @Override
            public void onLongClick(FileTree.Row row, View anchor) {
                showRowMenu(row, anchor);
            }
        });
        binding.treeList.setLayoutManager(new LinearLayoutManager(this));
        binding.treeList.setAdapter(adapter);
        binding.treeList.setItemAnimator(null);

        binding.treeNewFile.setOnClickListener(v -> askNewFile(selectedDir));
        binding.treeNewFolder.setOnClickListener(v -> askNewFolder(selectedDir));
        binding.treeRefresh.setOnClickListener(v -> publishRowsAsync());
        binding.treeCollapse.setOnClickListener(v -> onTree(() -> tree.collapseAll()));

        binding.rail.setSelectedItemId(R.id.nav_file_tree);
        binding.rail.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_file_tree) {
                return true;
            }
            if (id == R.id.nav_terminal) {
                openTerminal(selectedDir);
                binding.drawerLayout.closeDrawer(GravityCompat.START);
            } else if (id == R.id.nav_close_project) {
                finish();
            }
            // Terminal and Close are actions, not places: the rail keeps showing the file tree.
            return false;
        });
    }

    private void setUpSheet(RuntimePaths paths) {
        sheet = BottomSheetBehavior.from(binding.bottomSheet);
        sheet.setState(BottomSheetBehavior.STATE_COLLAPSED);

        RuntimeEnvironment environment = new RuntimeEnvironment(paths);
        buildPanel = new BuildPanel(this, binding.buildCommand, binding.buildRun, binding.buildOutput,
                () -> project == null ? null : project.dir(), new CommandRunner(paths, environment));
        livePanel = new LiveViewPanel(this, binding.webContainer, binding.liveUrl, binding.liveReload,
                binding.liveHint);

        TabLayout tabs = binding.sheetTabs;
        tabs.addTab(tabs.newTab().setText(R.string.sheet_build));
        tabs.addTab(tabs.newTab().setText(R.string.sheet_live));
        tabs.addTab(tabs.newTab().setText(R.string.sheet_problems));
        tabs.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                showPanel(tab.getPosition());
                if (sheet.getState() == BottomSheetBehavior.STATE_COLLAPSED) {
                    sheet.setState(BottomSheetBehavior.STATE_HALF_EXPANDED);
                }
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {
            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {
                sheet.setState(sheet.getState() == BottomSheetBehavior.STATE_COLLAPSED
                        ? BottomSheetBehavior.STATE_HALF_EXPANDED : BottomSheetBehavior.STATE_COLLAPSED);
            }
        });
        showPanel(TAB_BUILD);
    }

    private void showPanel(int position) {
        binding.buildPanel.setVisibility(position == TAB_BUILD ? View.VISIBLE : View.GONE);
        binding.livePanel.setVisibility(position == TAB_LIVE ? View.VISIBLE : View.GONE);
        binding.problemsPanel.setVisibility(position == TAB_PROBLEMS ? View.VISIBLE : View.GONE);
        if (position == TAB_LIVE) {
            livePanel.onShown();
        }
    }

    // --- tree ------------------------------------------------------------------------------

    private void onRowClick(FileTree.Row row) {
        if (row.directory) {
            selectedDir = row.file;
            updateTarget();
            if (!row.symlink) {
                onTree(() -> tree.toggle(row.file));
            }
        } else {
            selectedFile = row.file;
            selectedDir = row.file.getParentFile();
            updateTarget();
            editorTabs.open(row.file);
            binding.drawerLayout.closeDrawer(GravityCompat.START);
        }
    }

    private void showRowMenu(FileTree.Row row, View anchor) {
        PopupMenu menu = new PopupMenu(this, anchor);
        menu.getMenuInflater().inflate(R.menu.tree_item_menu, menu.getMenu());
        if (!row.directory) {
            menu.getMenu().findItem(R.id.action_new_file).setVisible(false);
            menu.getMenu().findItem(R.id.action_new_folder).setVisible(false);
            menu.getMenu().findItem(R.id.action_open_terminal).setVisible(false);
        }
        menu.setOnMenuItemClickListener(item -> onRowMenu(item, row));
        menu.show();
    }

    private boolean onRowMenu(MenuItem item, FileTree.Row row) {
        int id = item.getItemId();
        File parent = row.file.getParentFile();
        if (id == R.id.action_new_file) {
            askNewFile(row.file);
        } else if (id == R.id.action_new_folder) {
            askNewFolder(row.file);
        } else if (id == R.id.action_open_terminal) {
            openTerminal(row.file);
        } else if (id == R.id.action_rename) {
            if (editorTabs.hasOpenUnder(row.file)) {
                show(getString(R.string.close_before_rename));
                return true;
            }
            Dialogs.promptText(this, R.string.rename, row.name(), R.string.rename, FileOps::validateName,
                    name -> work(() -> {
                        FileOps.rename(row.file, name);
                        tree.forget(row.file);
                    }, () -> forgetSelection(row.file)));
        } else if (id == R.id.action_duplicate) {
            work(() -> FileOps.copy(row.file, parent), null);
        } else if (id == R.id.action_copy_path) {
            ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            clipboard.setPrimaryClip(ClipData.newPlainText("path", row.file.getAbsolutePath()));
            show(getString(R.string.path_copied));
        } else if (id == R.id.action_delete) {
            Dialogs.confirm(this, getString(R.string.delete_item_title, row.name()),
                    getString(R.string.delete_item_message), R.string.delete, () -> work(() -> {
                        if (!FileOps.isInside(project.dir(), row.file) || row.file.equals(project.dir())) {
                            throw new IOException("This item is outside the project");
                        }
                        FileOps.delete(row.file);
                        tree.forget(row.file);
                    }, () -> forgetSelection(row.file)));
        } else {
            return false;
        }
        return true;
    }

    private void askNewFile(File dir) {
        Dialogs.promptText(this, R.string.new_file, "", R.string.create, FileOps::validateName,
                name -> work(() -> {
                    FileOps.createFile(dir, name);
                    tree.expand(dir);
                }, null));
    }

    private void askNewFolder(File dir) {
        Dialogs.promptText(this, R.string.new_folder, "", R.string.create, FileOps::validateName,
                name -> work(() -> {
                    FileOps.createDirectory(dir, name);
                    tree.expand(dir);
                }, null));
    }

    private void openTerminal(File dir) {
        File target = dir != null ? dir : (project == null ? null : project.dir());
        Intent intent = new Intent(this, TerminalActivity.class);
        if (target != null) {
            intent.putExtra(TerminalActivity.EXTRA_CWD, target.getAbsolutePath());
        }
        startActivity(intent);
    }

    // --- background work -------------------------------------------------------------------

    /** Changes the tree on the background thread, then shows the new rows. */
    private void onTree(Runnable change) {
        if (tree == null) {
            return;
        }
        executor.execute(() -> {
            change.run();
            publishRows();
        });
    }

    /** A file operation on the background thread; {@code after} runs on the UI thread when it worked. */
    private void work(IoWork job, Runnable after) {
        executor.execute(() -> {
            try {
                job.run();
                if (after != null) {
                    runOnUiThread(after);
                }
                publishRows();
            } catch (IOException | RuntimeException e) {
                String message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
                runOnUiThread(() -> show(message));
            }
        });
    }

    private void publishRowsAsync() {
        if (tree != null) {
            executor.execute(this::publishRows);
        }
    }

    /** Must run on the executor thread. */
    private void publishRows() {
        final List<FileTree.Row> rows = tree.rows();
        runOnUiThread(() -> {
            if (isFinishing() || isDestroyed()) {
                return;
            }
            adapter.submitList(rows);
            binding.treeEmpty.setVisibility(rows.isEmpty() ? View.VISIBLE : View.GONE);
        });
    }

    // --- helpers ---------------------------------------------------------------------------

    private void forgetSelection(File changed) {
        editorTabs.closeUnder(changed);
        if (selectedFile != null && FileOps.isInside(changed, selectedFile)) {
            selectedFile = null;
        }
        if (selectedDir != null && FileOps.isInside(changed, selectedDir)) {
            selectedDir = project.dir();
            updateTarget();
        }
    }

    private void updateTarget() {
        binding.treeTarget.setText(getString(R.string.tree_target, "/" + relativeOrEmpty(selectedDir)));
    }

    private String relativeOrEmpty(File file) {
        return file == null || file.equals(project.dir()) ? "" : relative(file);
    }

    private String relative(File file) {
        return project.dir().toPath().relativize(file.toPath()).toString();
    }

    private void show(String message) {
        if (!isFinishing() && !isDestroyed()) {
            Snackbar.make(binding.getRoot(), message, Snackbar.LENGTH_LONG).show();
        }
    }
}
