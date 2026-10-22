package org.codirex.webide.terminal;

/**
 * State of the Ctrl and Alt toggle keys of the on-screen key row. A toggle is armed by a tap and
 * is used up by the next key press. Used from the UI thread only.
 */
public final class ModifierState {

    /** Called whenever a toggle changes, so the buttons can show it. */
    public interface Listener {
        void onModifiersChanged();
    }

    private boolean ctrl;
    private boolean alt;
    private Listener listener;

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public boolean isCtrl() {
        return ctrl;
    }

    public boolean isAlt() {
        return alt;
    }

    public void toggleCtrl() {
        ctrl = !ctrl;
        changed();
    }

    public void toggleAlt() {
        alt = !alt;
        changed();
    }

    /** Returns whether Ctrl was armed and disarms it. */
    public boolean consumeCtrl() {
        if (!ctrl) {
            return false;
        }
        ctrl = false;
        changed();
        return true;
    }

    /** Returns whether Alt was armed and disarms it. */
    public boolean consumeAlt() {
        if (!alt) {
            return false;
        }
        alt = false;
        changed();
        return true;
    }

    private void changed() {
        if (listener != null) {
            listener.onModifiersChanged();
        }
    }
}
