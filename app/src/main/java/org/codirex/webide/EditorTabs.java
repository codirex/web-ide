package org.codirex.webide;

import android.app.Activity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.tabs.TabLayout;

import org.codirex.webide.editor.EditorFileView;
import org.codirex.webide.editor.TextFile;
import org.codirex.webide.editor.TextMateSetup;
import org.codirex.webide.workspace.FileOps;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The open files of a project: a tab per file, one editor per tab, a row of symbol keys, saving,
 * and reloading files that were changed outside the editor (for example by git in the terminal).
 * Used from the UI thread.
 */
final class EditorTabs implements EditorFileView.Listener {

    /** What the screen has to do for the editor. */
    interface Host {
        void onStateChanged();

        void show(String message);

        boolean isNightMode();
    }

    private static final String[] SYMBOLS = {
            "{", "}", "(", ")", "[", "]", "<", ">", ";", ":", "\"", "'", "/", "=", "$", "#", "\\", "|", "&", "!", "_", "-"
    };

    private static final class Entry {
        final File file;
        final EditorFileView view;
        TabLayout.Tab tab;

        Entry(File file, EditorFileView view) {
            this.file = file;
            this.view = view;
        }
    }

    private final Activity activity;
    private final Host host;
    private final TabLayout tabs;
    private final FrameLayout editorHost;
    private final View placeholder;
    private final View symbolBar;
    private final Map<String, Entry> open = new LinkedHashMap<>();
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private Entry current;
    private boolean wordWrap;
    private boolean updatingTabs;
    private boolean missingAssetsReported;

    EditorTabs(Activity activity, Host host, TabLayout tabs, FrameLayout editorHost, View placeholder,
               View symbolBar, LinearLayout symbolKeys) {
        this.activity = activity;
        this.host = host;
        this.tabs = tabs;
        this.editorHost = editorHost;
        this.placeholder = placeholder;
        this.symbolBar = symbolBar;

        tabs.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                if (!updatingTabs && tab.getTag() instanceof Entry) {
                    show((Entry) tab.getTag());
                }
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {
            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {
            }
        });
        buildSymbols(symbolKeys);
        updateVisibility();
    }

    // --- opening and closing ---------------------------------------------------------------

    void open(File file) {
        Entry existing = open.get(file.getPath());
        if (existing != null) {
            select(existing);
            return;
        }
        io.execute(() -> {
            try {
                // The first call loads the grammars; it is done here so the UI thread stays free.
                boolean highlighting = TextMateSetup.init(activity);
                TextFile.Loaded loaded = TextFile.load(file);
                activity.runOnUiThread(() -> {
                    if (activity.isFinishing() || activity.isDestroyed()) {
                        return;
                    }
                    if (!highlighting && !missingAssetsReported) {
                        missingAssetsReported = true;
                        host.show(activity.getString(R.string.highlighting_missing));
                    }
                    add(file, loaded);
                });
            } catch (IOException | RuntimeException e) {
                activity.runOnUiThread(() -> host.show(message(e)));
            }
        });
    }

    private void add(File file, TextFile.Loaded loaded) {
        if (open.containsKey(file.getPath())) {
            select(open.get(file.getPath()));
            return;
        }
        TextMateSetup.applyTheme(host.isNightMode());
        EditorFileView view = new EditorFileView(activity, file, loaded, this);
        view.setWordWrap(wordWrap);
        view.getView().setVisibility(View.GONE);
        editorHost.addView(view.getView(), new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        Entry entry = new Entry(file, view);
        updatingTabs = true;
        entry.tab = tabs.newTab().setText(file.getName()).setTag(entry);
        tabs.addTab(entry.tab, false);
        updatingTabs = false;
        open.put(file.getPath(), entry);
        select(entry);
    }

    private void select(Entry entry) {
        if (current != null && current != entry) {
            current.view.getView().setVisibility(View.GONE);
        }
        current = entry;
        entry.view.getView().setVisibility(View.VISIBLE);
        entry.view.requestFocus();
        if (!entry.tab.isSelected()) {
            updatingTabs = true;
            entry.tab.select();
            updatingTabs = false;
        }
        updateVisibility();
        host.onStateChanged();
    }

    private void show(Entry entry) {
        select(entry);
    }

    /** Closes the current tab, asking first if it has unsaved changes. */
    void closeCurrent() {
        if (current == null) {
            return;
        }
        final Entry entry = current;
        if (!entry.view.isModified()) {
            remove(entry);
            return;
        }
        new MaterialAlertDialogBuilder(activity)
                .setTitle(activity.getString(R.string.unsaved_title, entry.file.getName()))
                .setMessage(R.string.unsaved_message)
                .setPositiveButton(R.string.save, (d, w) -> save(entry, () -> remove(entry)))
                .setNegativeButton(R.string.discard, (d, w) -> remove(entry))
                .setNeutralButton(android.R.string.cancel, null)
                .show();
    }

    /** Closes every tab for a file at or below {@code path}, without saving (used after a delete). */
    void closeUnder(File path) {
        for (Entry entry : new ArrayList<>(open.values())) {
            if (FileOps.isInside(path, entry.file)) {
                remove(entry);
            }
        }
    }

    boolean hasOpenUnder(File path) {
        for (Entry entry : open.values()) {
            if (FileOps.isInside(path, entry.file)) {
                return true;
            }
        }
        return false;
    }

    private void remove(Entry entry) {
        open.remove(entry.file.getPath());
        updatingTabs = true;
        tabs.removeTab(entry.tab);
        updatingTabs = false;
        editorHost.removeView(entry.view.getView());
        entry.view.release();
        if (current == entry) {
            current = null;
            if (!open.isEmpty()) {
                List<Entry> entries = new ArrayList<>(open.values());
                select(entries.get(entries.size() - 1));
                return;
            }
        }
        updateVisibility();
        host.onStateChanged();
    }

    // --- saving ----------------------------------------------------------------------------

    void saveCurrent() {
        if (current != null) {
            save(current, null);
        }
    }

    /** Saves every file with unsaved changes. Called when the screen goes to the background. */
    void saveAll() {
        for (Entry entry : open.values()) {
            if (entry.view.isModified()) {
                save(entry, null);
            }
        }
    }

    private void save(Entry entry, Runnable after) {
        final String text = entry.view.snapshot();
        final boolean crlf = entry.view.isCrlf();
        final boolean bom = entry.view.hasBom();
        io.execute(() -> {
            try {
                TextFile.save(entry.file, text, crlf, bom);
                final long modifiedAt = entry.file.lastModified();
                activity.runOnUiThread(() -> {
                    entry.view.markSaved(modifiedAt, text);
                    if (after != null) {
                        after.run();
                    }
                });
            } catch (IOException | RuntimeException e) {
                activity.runOnUiThread(() -> host.show(activity.getString(R.string.save_failed,
                        entry.file.getName(), message(e))));
            }
        });
    }

    // --- changes made outside the editor --------------------------------------------------

    /** Reloads open files that changed on disk; warns if the user has unsaved changes in such a file. */
    void checkDiskChanges() {
        for (Entry entry : new ArrayList<>(open.values())) {
            final File file = entry.file;
            final long onDisk = file.lastModified();
            if (!file.exists() || onDisk == entry.view.getLastModified()) {
                continue;
            }
            if (entry.view.isModified()) {
                entry.view.setLastModified(onDisk);
                host.show(activity.getString(R.string.changed_on_disk, file.getName()));
                continue;
            }
            io.execute(() -> {
                try {
                    TextFile.Loaded loaded = TextFile.load(file);
                    activity.runOnUiThread(() -> {
                        if (open.get(file.getPath()) == entry && !entry.view.isModified()) {
                            entry.view.reload(loaded);
                        }
                    });
                } catch (IOException | RuntimeException ignored) {
                    // Keep the text that is shown.
                }
            });
        }
    }

    // --- editing commands ------------------------------------------------------------------

    boolean hasCurrent() {
        return current != null;
    }

    boolean isCurrentModified() {
        return current != null && current.view.isModified();
    }

    boolean canUndo() {
        return current != null && current.view.canUndo();
    }

    boolean canRedo() {
        return current != null && current.view.canRedo();
    }

    File currentFile() {
        return current == null ? null : current.file;
    }

    void undo() {
        if (current != null) {
            current.view.undo();
            host.onStateChanged();
        }
    }

    void redo() {
        if (current != null) {
            current.view.redo();
            host.onStateChanged();
        }
    }

    boolean isWordWrap() {
        return wordWrap;
    }

    void toggleWordWrap() {
        wordWrap = !wordWrap;
        for (Entry entry : open.values()) {
            entry.view.setWordWrap(wordWrap);
        }
    }

    void release() {
        io.shutdownNow();
        for (Entry entry : open.values()) {
            entry.view.release();
        }
        open.clear();
        editorHost.removeAllViews();
    }

    // --- EditorFileView.Listener -----------------------------------------------------------

    @Override
    public void onModifiedChanged(EditorFileView view) {
        Entry entry = open.get(view.getFile().getPath());
        if (entry != null && entry.tab != null) {
            entry.tab.setText(view.isModified() ? view.getFile().getName() + " \u2022" : view.getFile().getName());
        }
        host.onStateChanged();
    }

    // --- helpers ---------------------------------------------------------------------------

    private void updateVisibility() {
        boolean any = !open.isEmpty();
        tabs.setVisibility(any ? View.VISIBLE : View.GONE);
        symbolBar.setVisibility(any ? View.VISIBLE : View.GONE);
        placeholder.setVisibility(any ? View.GONE : View.VISIBLE);
    }

    private void buildSymbols(LinearLayout container) {
        LayoutInflater inflater = LayoutInflater.from(activity);
        addSymbolKey(inflater, container, "TAB", "  ");
        for (String symbol : SYMBOLS) {
            addSymbolKey(inflater, container, symbol, symbol);
        }
    }

    private void addSymbolKey(LayoutInflater inflater, LinearLayout container, String label, String text) {
        MaterialButton button = (MaterialButton) inflater.inflate(R.layout.item_extra_key, container, false);
        button.setText(label);
        button.setOnClickListener(v -> {
            if (current != null) {
                current.view.insert(text);
            }
        });
        container.addView(button);
    }

    private static String message(Exception e) {
        return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
    }
}
