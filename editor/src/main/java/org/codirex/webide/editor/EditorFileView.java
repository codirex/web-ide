package org.codirex.webide.editor;

import android.content.Context;
import android.graphics.Typeface;
import android.util.Log;
import android.view.View;

import java.io.File;

import io.github.rosemoe.sora.event.ContentChangeEvent;
import io.github.rosemoe.sora.langs.textmate.TextMateColorScheme;
import io.github.rosemoe.sora.langs.textmate.TextMateLanguage;
import io.github.rosemoe.sora.langs.textmate.registry.ThemeRegistry;
import io.github.rosemoe.sora.widget.CodeEditor;

/**
 * One open file: a code editor with the file's text, its language and its unsaved state.
 * Must be used from the UI thread.
 */
public final class EditorFileView {

    /** Told when the unsaved state changes. */
    public interface Listener {
        void onModifiedChanged(EditorFileView view);
    }

    private static final String TAG = "WebIdeEditor";

    private final File file;
    private final CodeEditor editor;
    private final Listener listener;
    private boolean modified;
    private boolean crlf;
    private boolean bom;
    private long lastModified;
    private boolean ignoreChanges;

    public EditorFileView(Context context, File file, TextFile.Loaded loaded, Listener listener) {
        this.file = file;
        this.listener = listener;
        this.editor = new CodeEditor(context);
        editor.setTypefaceText(Typeface.MONOSPACE);
        editor.setTextSize(14f);
        editor.setTabWidth(2);
        editor.setLineNumberEnabled(true);
        editor.setWordwrap(false);

        if (TextMateSetup.isReady()) {
            try {
                editor.setColorScheme(TextMateColorScheme.create(ThemeRegistry.getInstance()));
                String scope = TextMateSetup.scopeFor(file);
                if (scope != null) {
                    editor.setEditorLanguage(TextMateLanguage.create(scope, true));
                }
            } catch (Exception | LinkageError e) {
                Log.w(TAG, "No highlighting for " + file.getName() + ": " + e);
            }
        }

        applyLoaded(loaded);
        editor.subscribeEvent(ContentChangeEvent.class, (event, unsubscribe) -> {
            if (!ignoreChanges) {
                setModified(true);
            }
        });
    }

    public File getFile() {
        return file;
    }

    public View getView() {
        return editor;
    }

    public boolean isModified() {
        return modified;
    }

    public boolean isCrlf() {
        return crlf;
    }

    public boolean hasBom() {
        return bom;
    }

    /** Modification time of the file when it was last read or written by this editor. */
    public long getLastModified() {
        return lastModified;
    }

    /** The current text, with "\n" line endings. */
    public String snapshot() {
        return editor.getText().toString();
    }

    /**
     * Called after {@code savedText} was written to disk. The editor counts as unmodified only if
     * the user did not type anything while the file was being written.
     */
    public void markSaved(long newLastModified, String savedText) {
        lastModified = newLastModified;
        setModified(!snapshot().equals(savedText));
    }

    /** Remembers the file's current modification time without touching the text (a change on disk was noticed). */
    public void setLastModified(long newLastModified) {
        lastModified = newLastModified;
    }

    /** Replaces the text with a fresh copy from disk. */
    public void reload(TextFile.Loaded loaded) {
        applyLoaded(loaded);
    }

    public void undo() {
        editor.undo();
    }

    public void redo() {
        editor.redo();
    }

    public boolean canUndo() {
        return editor.canUndo();
    }

    public boolean canRedo() {
        return editor.canRedo();
    }

    /** Types text at the cursor. */
    public void insert(String text) {
        editor.insertText(text, text.length());
    }

    public void setWordWrap(boolean wrap) {
        editor.setWordwrap(wrap);
    }

    public void requestFocus() {
        editor.requestFocus();
    }

    /** Frees the editor. The view must not be used afterwards. */
    public void release() {
        editor.release();
    }

    private void applyLoaded(TextFile.Loaded loaded) {
        crlf = loaded.crlf;
        bom = loaded.bom;
        lastModified = loaded.lastModified;
        ignoreChanges = true;
        editor.setText(loaded.text);
        ignoreChanges = false;
        setModified(false);
    }

    private void setModified(boolean value) {
        if (modified != value) {
            modified = value;
            if (listener != null) {
                listener.onModifiedChanged(this);
            }
        }
    }
}
