package org.codirex.webide.editor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class FileTypesTest {

    private static final FileTypes TYPES = FileTypes.parse(
            "# comment\n"
                    + "ts=source.ts\n"
                    + "html=text.html.basic\n"
                    + "name:Dockerfile=source.dockerfile\n"
                    + "name:.gitignore=source.ignore\n"
                    + "bad line\n"
                    + "=nokey\n");

    @Test
    public void usesTheLastExtension() {
        assertEquals("source.ts", TYPES.scopeFor("main.ts"));
        assertEquals("source.ts", TYPES.scopeFor("types.d.ts"));
        assertEquals("text.html.basic", TYPES.scopeFor("INDEX.HTML"));
    }

    @Test
    public void exactNamesWinAndIgnoreCase() {
        assertEquals("source.dockerfile", TYPES.scopeFor("Dockerfile"));
        assertEquals("source.dockerfile", TYPES.scopeFor("dockerfile"));
        assertEquals("source.ignore", TYPES.scopeFor(".gitignore"));
    }

    @Test
    public void unknownFilesHaveNoLanguage() {
        assertNull(TYPES.scopeFor("notes.xyz"));
        assertNull(TYPES.scopeFor("README"));
        assertNull(TYPES.scopeFor("ends-with-dot."));
        assertNull(TYPES.scopeFor(""));
        assertNull(TYPES.scopeFor(null));
    }
}
