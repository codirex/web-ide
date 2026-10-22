package org.codirex.webide.terminal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ModifierStateTest {

    @Test
    public void toggleThenConsumeUsesUpTheModifier() {
        ModifierState state = new ModifierState();
        assertFalse(state.consumeCtrl());
        state.toggleCtrl();
        assertTrue(state.isCtrl());
        assertTrue(state.consumeCtrl());
        assertFalse(state.isCtrl());
        assertFalse(state.consumeCtrl());
    }

    @Test
    public void ctrlAndAltAreIndependent() {
        ModifierState state = new ModifierState();
        state.toggleAlt();
        assertFalse(state.consumeCtrl());
        assertTrue(state.consumeAlt());
    }

    @Test
    public void listenerHearsEveryChange() {
        ModifierState state = new ModifierState();
        final int[] calls = {0};
        state.setListener(() -> calls[0]++);
        state.toggleCtrl();   // armed
        state.consumeCtrl();  // used up
        state.consumeCtrl();  // nothing to use up: no event
        state.toggleAlt();
        assertEquals(3, calls[0]);
    }
}
