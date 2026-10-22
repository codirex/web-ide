package org.codirex.webide.editor;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Maps file names to TextMate scope names, using the table that the grammar pack ships
 * ({@code textmate/file-types.txt}). Pure Java.
 *
 * <p>Format, one rule per line; lines starting with # are comments:
 * <pre>
 * ts=source.ts            (by extension)
 * name:Dockerfile=source.dockerfile   (by exact file name, ignoring case)
 * </pre>
 */
public final class FileTypes {

    public static final FileTypes EMPTY = new FileTypes(new HashMap<>(), new HashMap<>());

    private final Map<String, String> byExtension;
    private final Map<String, String> byName;

    private FileTypes(Map<String, String> byExtension, Map<String, String> byName) {
        this.byExtension = byExtension;
        this.byName = byName;
    }

    public static FileTypes parse(String text) {
        Map<String, String> extensions = new HashMap<>();
        Map<String, String> names = new HashMap<>();
        for (String raw : text.split("\\r?\\n")) {
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            int equals = line.indexOf('=');
            if (equals <= 0 || equals == line.length() - 1) {
                continue;
            }
            String key = line.substring(0, equals).trim().toLowerCase(Locale.ROOT);
            String scope = line.substring(equals + 1).trim();
            if (key.startsWith("name:")) {
                names.put(key.substring(5), scope);
            } else {
                extensions.put(key, scope);
            }
        }
        return new FileTypes(extensions, names);
    }

    /** The scope name for a file, or {@code null} if the file has no known language. */
    public String scopeFor(String fileName) {
        if (fileName == null || fileName.isEmpty()) {
            return null;
        }
        String lower = fileName.toLowerCase(Locale.ROOT);
        String byFullName = byName.get(lower);
        if (byFullName != null) {
            return byFullName;
        }
        int dot = lower.lastIndexOf('.');
        if (dot < 0 || dot == lower.length() - 1) {
            return null;
        }
        return byExtension.get(lower.substring(dot + 1));
    }
}
