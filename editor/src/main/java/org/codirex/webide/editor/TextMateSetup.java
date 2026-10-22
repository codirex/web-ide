package org.codirex.webide.editor;

import android.content.Context;
import android.util.Log;

import org.eclipse.tm4e.core.registry.IThemeSource;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import io.github.rosemoe.sora.langs.textmate.registry.FileProviderRegistry;
import io.github.rosemoe.sora.langs.textmate.registry.GrammarRegistry;
import io.github.rosemoe.sora.langs.textmate.registry.ThemeRegistry;
import io.github.rosemoe.sora.langs.textmate.registry.model.ThemeModel;
import io.github.rosemoe.sora.langs.textmate.registry.provider.AssetsFileResolver;

/**
 * One-time setup of TextMate syntax highlighting from the app's assets ({@code assets/textmate}).
 * If the assets are not there, the editor still works as a plain text editor.
 */
public final class TextMateSetup {

    private static final String TAG = "WebIdeEditor";
    private static final String ROOT = "textmate/";

    private static boolean initialized;
    private static boolean ready;
    private static FileTypes fileTypes = FileTypes.EMPTY;
    private static String lightTheme;
    private static String darkTheme;

    private TextMateSetup() {}

    /**
     * Loads grammars and themes the first time it is called. Returns whether highlighting is
     * available.
     */
    public static synchronized boolean init(Context context) {
        if (initialized) {
            return ready;
        }
        initialized = true;
        try {
            Context app = context.getApplicationContext();
            FileProviderRegistry.getInstance()
                    .addFileProvider(new AssetsFileResolver(app.getAssets()));

            String themes = readAsset(app, ROOT + "themes.txt");
            for (String line : themes.split("\\r?\\n")) {
                String t = line.trim();
                if (t.startsWith("light=")) {
                    lightTheme = t.substring(6).trim();
                } else if (t.startsWith("dark=")) {
                    darkTheme = t.substring(5).trim();
                }
            }
            loadTheme(lightTheme, false);
            loadTheme(darkTheme, true);

            GrammarRegistry.getInstance().loadGrammars(ROOT + "languages.json");
            fileTypes = FileTypes.parse(readAsset(app, ROOT + "file-types.txt"));
            ready = true;
        } catch (Exception | LinkageError e) {
            Log.w(TAG, "Syntax highlighting is not available: " + e);
            ready = false;
        }
        return ready;
    }

    public static boolean isReady() {
        return ready;
    }

    /** Switches the colors of all editors between the light and the dark theme. */
    public static void applyTheme(boolean dark) {
        if (!ready) {
            return;
        }
        String name = dark ? darkTheme : lightTheme;
        if (name != null) {
            try {
                ThemeRegistry.getInstance().setTheme(name);
            } catch (Exception e) {
                Log.w(TAG, "Cannot switch theme: " + e);
            }
        }
    }

    /** The TextMate scope name for a file, or null if there is no grammar for it. */
    public static String scopeFor(File file) {
        return ready ? fileTypes.scopeFor(file.getName()) : null;
    }

    private static void loadTheme(String name, boolean dark) throws IOException {
        if (name == null || name.isEmpty()) {
            return;
        }
        String path = ROOT + "themes/" + name + ".json";
        ThemeModel model =
                new ThemeModel(
                        IThemeSource.fromInputStream(
                                FileProviderRegistry.getInstance().tryGetInputStream(path),
                                path,
                                null),
                        name);
        model.setDark(dark);
        try {
            ThemeRegistry.getInstance().loadTheme(model);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static String readAsset(Context context, String path) throws IOException {
        try (InputStream in = context.getAssets().open(path)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int n;
            while ((n = in.read(buffer)) != -1) {
                out.write(buffer, 0, n);
            }
            return out.toString(StandardCharsets.UTF_8.name());
        }
    }
}
