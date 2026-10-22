# Third-party components

Phase 0 uses only AndroidX and Material Components (Apache-2.0).

Planned for later phases (licenses to be re-checked when each is added):

| Component | License | Phase |
|---|---|---|
| Sora Editor | LGPL-2.1-or-later (TextMate module contains EPL code from Eclipse TM4E) | 4 |
| Termux terminal-emulator and terminal-view | Apache-2.0 | 2 |
| Termux app and termux-shared code | GPL-3.0 | 1-2 |
| Eclipse LSP4J | EPL-2.0 / EDL | 6 |
| JGit | EDL (BSD-3-Clause) | 7 |
| Node.js, Git, proot-style tools in the bootstrap | MIT, GPL-2.0 and others; source and notices must ship with each release | 1 |
| TypeScript, typescript-language-server | Apache-2.0 | 6 |

## Editor (phase 4)

| Component | License | Use |
|---|---|---|
| Sora Editor (`editor`, `language-textmate`, `oniguruma-native`) | LGPL-2.1-or-later; the TextMate module contains EPL code from Eclipse TM4E | code editor |
| Gson | Apache-2.0 | needed by the TextMate module |
| tm-grammars | MIT for the package; each grammar has its own license, listed in `assets/textmate/notices/` | syntax highlighting |
| tm-themes | MIT for the package; each theme has its own license, listed in `assets/textmate/notices/` | editor colors |
