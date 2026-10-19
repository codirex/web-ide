# Architecture

## Modules

| Module | Responsibility | Depends on |
|---|---|---|
| `:app` | Screens, navigation, wiring of all modules | all |
| `:core` | Shared constants and contracts | none |
| `:runtime` | Environment (bootstrap, Node.js, Git), process launching, ABI detection | core |
| `:terminal` | Interactive sessions, PTY, signals | core, runtime |
| `:editor` | Sora Editor, TextMate, Tree-sitter, tabs | core |
| `:lsp` | Language servers and the bridge to the editor | core, runtime, editor |
| `:workspace` | Projects and files | core |
| `:git` | Git UI support (JGit) | core |
| `:templates` | Template engine (plain Java, no Android) | none |
| `:preview` | WebView host, static server, dev-server tracking | core, runtime |

Feature modules never depend on each other, except `:lsp` on `:editor`. `:app` connects them.

## Decisions

- **targetSdk 28, direct exec.** Apps targeting API 29+ cannot execute files from their own
  data directory. Apps targeting 25-28 run in an SELinux domain that allows it. The app is
  distributed from the project website and GitHub, never from Google Play. The lint checks
  `ExpiredTargetSdkVersion` and `OldTargetApi` are disabled on purpose.
- **Package name `org.codirex.webide`.** Prebuilt packages contain the absolute path
  `/data/data/org.codirex.webide/files/usr`, so they only run for the primary user.
- **Storage.** Projects and tools live in the app's private storage, which supports executable files.
  Import and export use the system file picker.
- **ABIs.** `arm64-v8a` and `armeabi-v7a`. The bootstrap is chosen by the bitness of the running process.
- **LSP.** TypeScript 6 with typescript-language-server first; TypeScript 7 later.
- **UI language.** English only.

## Open risks

- The minimum targetSdk Android accepts for installs is 24 on Android 15 and 16; Android 17 is unconfirmed.
- 32-bit processes have a smaller address space, which limits Node.js and the TypeScript server.
