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

## Workspace layout

Projects are folders directly inside `~/projects` (`/data/data/org.codirex.webide/files/home/projects`).
The folder name is the project name. The database (Room) only remembers creation and last-opened
times; it is brought in line with the folders every time the home screen opens, so folders created
in the terminal (for example with `git clone`) appear as projects. Deleting a project deletes its
folder. Zip export leaves out `node_modules`; zip import removes a single wrapping top-level folder.

## Project screen

- **Drawer** (menu button, top left): a navigation rail with *Files* (shows the tree), *Terminal*
  (opens a terminal in the selected folder) and *Close* (back to the project list), next to the file
  tree. A tap on a folder opens or closes it and makes it the target for new files and folders; a
  long press shows the actions for that item.
- **Bottom sheet**: *Build* (runs a shell command in the project folder and shows the output),
  *Live view* (WebView for a dev server on this phone, default http://127.0.0.1:5173) and *Problems*
  (filled by the language servers later).
- `FileTree` (module `:workspace`, pure Java) is the model of the tree: open folders and the
  flattened rows. It is used from one background thread only.

## Editor

`:editor` wraps Sora Editor. `EditorFileView` is one open file (a `CodeEditor` plus its unsaved state);
`TextFile` reads and writes UTF-8 text safely; `FileTypes` maps file names to TextMate scopes using the
table shipped with the grammar assets; `TextMateSetup` loads grammars and themes once. In the app,
`EditorTabs` manages the tabs, saving and reloading, so `ProjectActivity` only reacts to its state.

## Open risks

- The minimum targetSdk Android accepts for installs is 24 on Android 15 and 16; Android 17 is unconfirmed.
- 32-bit processes have a smaller address space, which limits Node.js and the TypeScript server.
