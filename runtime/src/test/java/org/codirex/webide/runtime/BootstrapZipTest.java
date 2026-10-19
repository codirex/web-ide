package org.codirex.webide.runtime;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class BootstrapZipTest {

    @Test
    public void parsesSymlinkLine() {
        BootstrapZip.SymlinkEntry entry = BootstrapZip.parseSymlinkLine("busybox\u2190./bin/ls");
        assertNotNull(entry);
        assertEquals("busybox", entry.target);
        assertEquals("./bin/ls", entry.path);
    }

    @Test
    public void blankSymlinkLineIsIgnored() {
        assertNull(BootstrapZip.parseSymlinkLine(""));
        assertNull(BootstrapZip.parseSymlinkLine("   "));
        assertNull(BootstrapZip.parseSymlinkLine(null));
    }

    @Test(expected = IllegalArgumentException.class)
    public void symlinkLineWithoutSeparatorFails() {
        BootstrapZip.parseSymlinkLine("no-separator");
    }

    @Test(expected = IllegalArgumentException.class)
    public void symlinkLineWithEmptyTargetFails() {
        BootstrapZip.parseSymlinkLine("\u2190./bin/x");
    }

    @Test(expected = IllegalArgumentException.class)
    public void symlinkLineWithEmptyPathFails() {
        BootstrapZip.parseSymlinkLine("target\u2190");
    }

    @Test
    public void acceptsSafePaths() {
        assertTrue(BootstrapZip.isSafeRelativePath("bin/sh"));
        assertTrue(BootstrapZip.isSafeRelativePath("./bin/sh"));
        assertTrue(BootstrapZip.isSafeRelativePath("etc/profile.d/"));
    }

    @Test
    public void rejectsUnsafePaths() {
        assertFalse(BootstrapZip.isSafeRelativePath(null));
        assertFalse(BootstrapZip.isSafeRelativePath(""));
        assertFalse(BootstrapZip.isSafeRelativePath("/etc/passwd"));
        assertFalse(BootstrapZip.isSafeRelativePath("../x"));
        assertFalse(BootstrapZip.isSafeRelativePath("a/../../b"));
        assertFalse(BootstrapZip.isSafeRelativePath("a\\b"));
        assertFalse(BootstrapZip.isSafeRelativePath("a\0b"));
    }

    @Test
    public void marksExecutableFiles() {
        assertTrue(BootstrapZip.needsExecutableBit("bin/node"));
        assertTrue(BootstrapZip.needsExecutableBit("./bin/node"));
        assertTrue(BootstrapZip.needsExecutableBit("libexec/git-core/git"));
        assertTrue(BootstrapZip.needsExecutableBit("lib/apt/apt-helper"));
        assertTrue(BootstrapZip.needsExecutableBit("lib/apt/methods/http"));
        assertFalse(BootstrapZip.needsExecutableBit("lib/libc.so"));
        assertFalse(BootstrapZip.needsExecutableBit("etc/profile"));
        assertFalse(BootstrapZip.needsExecutableBit("binary/readme"));
    }
}
