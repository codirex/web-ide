package org.codirex.webide.terminal;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.util.Log;

import com.termux.terminal.TerminalEmulator;
import com.termux.terminal.TerminalSession;
import com.termux.terminal.TerminalSessionClient;

import org.codirex.webide.runtime.RuntimeEnvironment;
import org.codirex.webide.runtime.RuntimePaths;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Owns the terminal sessions. Lives inside {@link TerminalService}, so sessions survive the
 * screen being closed. All methods must be called on the main thread.
 */
public final class TerminalSessionManager {

    /** Receives changes so the screen can follow them. */
    public interface Listener {
        /** A session was added or removed. */
        void onSessionsChanged();

        /** The text of a session changed and the view should redraw it. */
        void onSessionTextChanged(TerminalSession session);

        /** The title of a session changed. */
        void onSessionTitleChanged(TerminalSession session);
    }

    private static final String TAG = "WebIdeTerminal";
    private static final int TRANSCRIPT_ROWS = 2000;

    private final Context context;
    private final RuntimePaths paths;
    private final RuntimeEnvironment environment;
    private final List<TerminalSession> sessions = new ArrayList<>();
    private final Map<TerminalSession, String> names = new IdentityHashMap<>();
    private final Client client = new Client();
    private Listener listener;
    private Runnable countObserver;
    private int counter;

    public TerminalSessionManager(Context context) {
        this.context = context.getApplicationContext();
        this.paths = new RuntimePaths(this.context);
        this.environment = new RuntimeEnvironment(paths);
    }

    public boolean isRuntimeInstalled() {
        return environment.bash().exists();
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    /** Called when the number of sessions changes. Used by the service to update its notification. */
    void setCountObserver(Runnable observer) {
        this.countObserver = observer;
    }

    public List<TerminalSession> getSessions() {
        return Collections.unmodifiableList(sessions);
    }

    public String getTitle(TerminalSession session) {
        String name = names.get(session);
        return name == null ? "bash" : name;
    }

    /** Starts a login shell in the home directory. The process starts when a view first sizes it. */
    public TerminalSession createSession() {
        return createSession(null);
    }

    /** Starts a login shell in {@code cwd}, or in the home directory if that folder does not exist. */
    public TerminalSession createSession(String cwd) {
        String directory = paths.home.getAbsolutePath();
        if (cwd != null && new java.io.File(cwd).isDirectory()) {
            directory = cwd;
        }
        counter++;
        // argv[0] starting with "-" makes bash a login shell, so it reads the profile scripts.
        TerminalSession session = new TerminalSession(
                environment.bash().getAbsolutePath(),
                directory,
                new String[]{"-bash"},
                environment.buildArray(),
                TRANSCRIPT_ROWS,
                client);
        sessions.add(session);
        names.put(session, "bash " + counter);
        sessionsChanged();
        return session;
    }

    public void remove(TerminalSession session) {
        if (sessions.remove(session)) {
            names.remove(session);
            sessionsChanged();
        }
    }

    /** Ends all sessions. */
    public void finishAll() {
        List<TerminalSession> copy = new ArrayList<>(sessions);
        sessions.clear();
        names.clear();
        for (TerminalSession session : copy) {
            session.finishIfRunning();
        }
        sessionsChanged();
    }

    public void paste(TerminalSession session) {
        pasteFromClipboard(session);
    }

    private void sessionsChanged() {
        if (listener != null) {
            listener.onSessionsChanged();
        }
        if (countObserver != null) {
            countObserver.run();
        }
    }

    private void copyToClipboard(String text) {
        ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null) {
            clipboard.setPrimaryClip(ClipData.newPlainText("terminal", text));
        }
    }

    private void pasteFromClipboard(TerminalSession session) {
        ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard == null || session == null || session.getEmulator() == null) {
            return;
        }
        ClipData clip = clipboard.getPrimaryClip();
        if (clip == null || clip.getItemCount() == 0) {
            return;
        }
        CharSequence text = clip.getItemAt(0).coerceToText(context);
        if (text != null && text.length() > 0) {
            session.getEmulator().paste(text.toString());
        }
    }

    private final class Client implements TerminalSessionClient {

        @Override
        public void onTextChanged(TerminalSession changedSession) {
            if (listener != null) {
                listener.onSessionTextChanged(changedSession);
            }
        }

        @Override
        public void onTitleChanged(TerminalSession updatedSession) {
            if (listener != null) {
                listener.onSessionTitleChanged(updatedSession);
            }
        }

        @Override
        public void onSessionFinished(TerminalSession finishedSession) {
            remove(finishedSession);
        }

        @Override
        public void onCopyTextToClipboard(TerminalSession session, String text) {
            copyToClipboard(text);
        }

        @Override
        public void onPasteTextFromClipboard(TerminalSession session) {
            pasteFromClipboard(session);
        }

        @Override
        public void onBell(TerminalSession session) {
            // No sound or vibration for now.
        }

        @Override
        public void onColorsChanged(TerminalSession session) {
            // Colors are not themed yet.
        }

        @Override
        public void onTerminalCursorStateChange(boolean state) {
            // The view handles blinking itself.
        }

        @Override
        public Integer getTerminalCursorStyle() {
            return TerminalEmulator.DEFAULT_TERMINAL_CURSOR_STYLE;
        }

        @Override
        public void logError(String tag, String message) {
            Log.e(tag, message);
        }

        @Override
        public void logWarn(String tag, String message) {
            Log.w(tag, message);
        }

        @Override
        public void logInfo(String tag, String message) {
            Log.i(tag, message);
        }

        @Override
        public void logDebug(String tag, String message) {
            Log.d(tag, message);
        }

        @Override
        public void logVerbose(String tag, String message) {
            Log.v(tag, message);
        }

        @Override
        public void logStackTraceWithMessage(String tag, String message, Exception e) {
            Log.e(tag, message, e);
        }

        @Override
        public void logStackTrace(String tag, Exception e) {
            Log.e(tag, TAG, e);
        }
    }
}
