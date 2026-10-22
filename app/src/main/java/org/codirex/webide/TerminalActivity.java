package org.codirex.webide;

import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.inputmethod.InputMethodManager;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.tabs.TabLayout;
import com.termux.terminal.TerminalSession;

import org.codirex.webide.databinding.ActivityTerminalBinding;
import org.codirex.webide.terminal.ModifierState;
import org.codirex.webide.terminal.TerminalService;
import org.codirex.webide.terminal.TerminalSessionManager;
import org.codirex.webide.terminal.TerminalViewClientImpl;

import java.util.List;

/** Interactive terminal: tabs for the sessions, the terminal view and a row of extra keys. */
public class TerminalActivity extends AppCompatActivity
        implements ServiceConnection, TerminalSessionManager.Listener, TerminalViewClientImpl.Host {

    /** Optional folder in which a new session should start. */
    public static final String EXTRA_CWD = "org.codirex.webide.extra.CWD";

    private static final int MIN_TEXT_SP = 8;
    private static final int MAX_TEXT_SP = 32;

    private ActivityTerminalBinding binding;
    private TerminalSessionManager manager;
    private final ModifierState modifiers = new ModifierState();
    private TerminalSession current;
    private int textSizeSp = 14;
    private boolean updatingTabs;
    private int knownSessionCount;
    private boolean bound;
    private MaterialButton ctrlButton;
    private MaterialButton altButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityTerminalBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        setSupportActionBar(binding.toolbar);

        binding.terminalView.setTerminalViewClient(new TerminalViewClientImpl(this, modifiers));
        applyTextSize();
        buildExtraKeys();
        modifiers.setListener(this::updateModifierButtons);

        binding.tabs.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                if (!updatingTabs && manager != null) {
                    List<TerminalSession> sessions = manager.getSessions();
                    int position = tab.getPosition();
                    if (position >= 0 && position < sessions.size()) {
                        select(sessions.get(position));
                    }
                }
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {
            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {
            }
        });

        Intent service = new Intent(this, TerminalService.class);
        startForegroundService(service);
        bound = bindService(service, this, BIND_AUTO_CREATE);
    }

    @Override
    protected void onDestroy() {
        if (manager != null) {
            manager.setListener(null);
        }
        if (bound) {
            unbindService(this);
            bound = false;
        }
        super.onDestroy();
    }

    // --- service connection ----------------------------------------------------------------

    @Override
    public void onServiceConnected(ComponentName name, IBinder service) {
        manager = ((TerminalService.LocalBinder) service).getService().getManager();
        if (!manager.isRuntimeInstalled()) {
            Toast.makeText(this, R.string.terminal_no_runtime, Toast.LENGTH_LONG).show();
            startService(new Intent(this, TerminalService.class).setAction(TerminalService.ACTION_EXIT));
            finish();
            return;
        }
        manager.setListener(this);
        String cwd = takeCwd(getIntent());
        if (cwd != null) {
            manager.createSession(cwd);
        } else if (manager.getSessions().isEmpty()) {
            manager.createSession();
        } else {
            onSessionsChanged();
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        String cwd = takeCwd(intent);
        if (cwd != null && manager != null) {
            manager.createSession(cwd);
        }
    }

    private static String takeCwd(Intent intent) {
        String cwd = intent == null ? null : intent.getStringExtra(EXTRA_CWD);
        if (intent != null) {
            intent.removeExtra(EXTRA_CWD);
        }
        return cwd;
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
        manager = null;
    }

    // --- session manager listener ----------------------------------------------------------

    @Override
    public void onSessionsChanged() {
        List<TerminalSession> sessions = manager.getSessions();
        if (sessions.isEmpty()) {
            finish();
            return;
        }
        boolean added = sessions.size() > knownSessionCount;
        knownSessionCount = sessions.size();
        if (current == null || !sessions.contains(current) || added) {
            current = sessions.get(sessions.size() - 1);
        }
        rebuildTabs();
        select(current);
    }

    @Override
    public void onSessionTextChanged(TerminalSession session) {
        if (session == current) {
            binding.terminalView.onScreenUpdated();
        }
    }

    @Override
    public void onSessionTitleChanged(TerminalSession session) {
        rebuildTabs();
    }

    // --- terminal view host ----------------------------------------------------------------

    @Override
    public void changeFontSize(boolean increase) {
        textSizeSp = Math.max(MIN_TEXT_SP, Math.min(MAX_TEXT_SP, textSizeSp + (increase ? 1 : -1)));
        applyTextSize();
    }

    @Override
    public void showKeyboard() {
        binding.terminalView.requestFocus();
        InputMethodManager imm = getSystemService(InputMethodManager.class);
        if (imm != null) {
            imm.showSoftInput(binding.terminalView, 0);
        }
    }

    @Override
    public void onEmulatorSet() {
        binding.terminalView.onScreenUpdated();
    }

    // --- menu ------------------------------------------------------------------------------

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.terminal_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_new_session) {
            if (manager != null) {
                manager.createSession();
            }
        } else if (id == R.id.action_close_session) {
            if (current != null) {
                current.finishIfRunning();
            }
        } else if (id == R.id.action_paste) {
            if (manager != null) {
                manager.paste(current);
            }
        } else if (id == R.id.action_font_larger) {
            changeFontSize(true);
        } else if (id == R.id.action_font_smaller) {
            changeFontSize(false);
        } else if (id == R.id.action_exit_all) {
            startService(new Intent(this, TerminalService.class).setAction(TerminalService.ACTION_EXIT));
            finish();
        } else {
            return super.onOptionsItemSelected(item);
        }
        return true;
    }

    // --- helpers ---------------------------------------------------------------------------

    private void select(TerminalSession session) {
        current = session;
        binding.terminalView.attachSession(session);
        binding.terminalView.onScreenUpdated();
        binding.terminalView.requestFocus();
        List<TerminalSession> sessions = manager.getSessions();
        int index = sessions.indexOf(session);
        if (index >= 0 && index < binding.tabs.getTabCount()
                && binding.tabs.getSelectedTabPosition() != index) {
            updatingTabs = true;
            binding.tabs.selectTab(binding.tabs.getTabAt(index));
            updatingTabs = false;
        }
    }

    private void rebuildTabs() {
        updatingTabs = true;
        binding.tabs.removeAllTabs();
        for (TerminalSession session : manager.getSessions()) {
            binding.tabs.addTab(binding.tabs.newTab().setText(manager.getTitle(session)),
                    session == current);
        }
        updatingTabs = false;
    }

    private void applyTextSize() {
        float density = getResources().getDisplayMetrics().scaledDensity;
        binding.terminalView.setTextSize(Math.round(textSizeSp * density));
    }

    // --- extra keys ------------------------------------------------------------------------

    private void buildExtraKeys() {
        addKey("ESC", () -> sendKey(KeyEvent.KEYCODE_ESCAPE));
        addKey("TAB", () -> sendKey(KeyEvent.KEYCODE_TAB));
        ctrlButton = addKey("CTRL", modifiers::toggleCtrl);
        altButton = addKey("ALT", modifiers::toggleAlt);
        ctrlButton.setCheckable(true);
        altButton.setCheckable(true);
        addKey("^C", () -> sendText("\u0003"));
        addKey("^D", () -> sendText("\u0004"));
        addKey("\u2190", () -> sendKey(KeyEvent.KEYCODE_DPAD_LEFT));
        addKey("\u2193", () -> sendKey(KeyEvent.KEYCODE_DPAD_DOWN));
        addKey("\u2191", () -> sendKey(KeyEvent.KEYCODE_DPAD_UP));
        addKey("\u2192", () -> sendKey(KeyEvent.KEYCODE_DPAD_RIGHT));
        addKey("HOME", () -> sendKey(KeyEvent.KEYCODE_MOVE_HOME));
        addKey("END", () -> sendKey(KeyEvent.KEYCODE_MOVE_END));
        addKey("PGUP", () -> sendKey(KeyEvent.KEYCODE_PAGE_UP));
        addKey("PGDN", () -> sendKey(KeyEvent.KEYCODE_PAGE_DOWN));
        for (String text : new String[]{"-", "/", "|", "~", "\\", "$", "&", "*"}) {
            addKey(text, () -> sendText(text));
        }
    }

    private MaterialButton addKey(String label, Runnable action) {
        MaterialButton button = (MaterialButton) getLayoutInflater()
                .inflate(R.layout.item_extra_key, binding.extraKeys, false);
        button.setText(label);
        button.setOnClickListener(v -> action.run());
        binding.extraKeys.addView(button);
        return button;
    }

    private void updateModifierButtons() {
        ctrlButton.setChecked(modifiers.isCtrl());
        altButton.setChecked(modifiers.isAlt());
    }

    private void sendKey(int keyCode) {
        binding.terminalView.onKeyDown(keyCode, new KeyEvent(KeyEvent.ACTION_DOWN, keyCode));
        binding.terminalView.onKeyUp(keyCode, new KeyEvent(KeyEvent.ACTION_UP, keyCode));
    }

    private void sendText(String text) {
        if (current != null) {
            current.write(text);
        }
    }
}
