package org.codirex.webide.terminal;

import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;

import com.termux.terminal.TerminalSession;
import com.termux.view.TerminalViewClient;

/** Connects the terminal view to the screen: font size, keyboard, and the Ctrl/Alt toggles. */
public final class TerminalViewClientImpl implements TerminalViewClient {

    /** What the screen has to do for the view. */
    public interface Host {
        /** Pinch gesture: make the text larger or smaller. */
        void changeFontSize(boolean increase);

        /** A tap on the terminal: bring up the keyboard. */
        void showKeyboard();

        /** The terminal emulator of the attached session is ready. */
        void onEmulatorSet();
    }

    private final Host host;
    private final ModifierState modifiers;

    public TerminalViewClientImpl(Host host, ModifierState modifiers) {
        this.host = host;
        this.modifiers = modifiers;
    }

    @Override
    public float onScale(float scale) {
        if (scale < 0.9f || scale > 1.1f) {
            host.changeFontSize(scale > 1f);
            return 1.0f;
        }
        return scale;
    }

    @Override
    public void onSingleTapUp(MotionEvent e) {
        host.showKeyboard();
    }

    @Override
    public boolean shouldBackButtonBeMappedToEscape() {
        return false;
    }

    @Override
    public boolean shouldEnforceCharBasedInput() {
        // Some keyboards (predictive text, voice) only work with character based input.
        return true;
    }

    @Override
    public boolean shouldUseCtrlSpaceWorkaround() {
        return false;
    }

    @Override
    public boolean isTerminalViewSelected() {
        return true;
    }

    @Override
    public void copyModeChanged(boolean copyMode) {
        // Nothing to update.
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent e, TerminalSession session) {
        return false;
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent e) {
        return false;
    }

    @Override
    public boolean onLongPress(MotionEvent event) {
        // Returning false lets the view start its own text selection.
        return false;
    }

    @Override
    public boolean readControlKey() {
        return modifiers.consumeCtrl();
    }

    @Override
    public boolean readAltKey() {
        return modifiers.consumeAlt();
    }

    @Override
    public boolean readShiftKey() {
        return false;
    }

    @Override
    public boolean readFnKey() {
        return false;
    }

    @Override
    public boolean onCodePoint(int codePoint, boolean ctrlDown, TerminalSession session) {
        return false;
    }

    @Override
    public void onEmulatorSet() {
        host.onEmulatorSet();
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
        Log.e(tag, "terminal", e);
    }
}
