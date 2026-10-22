# Editor

The editor is Sora Editor (LGPL-2.1-or-later) with TextMate grammars for syntax highlighting.
Every open file has its own editor inside a tab, so each keeps its cursor, scroll position and undo history.

## Syntax highlighting assets

Grammars and themes are not part of the source. They are built from the npm packages `tm-grammars`
and `tm-themes` and placed in `app/src/main/assets/textmate/`:

1. Push the project to GitHub, then open **Actions → Build editor assets → Run workflow**
   (optionally choose the light and dark theme ids; defaults `github-light` and `github-dark`).
2. Download the artifact `textmate-assets`, unzip it once. It contains a folder `textmate`.
3. Copy that folder to `app/src/main/assets/` and rebuild the app.

Without these assets the editor still works as a plain text editor and shows a note once.

The generator is `tools/build_textmate_assets.py`. Languages: HTML, CSS, SCSS, Sass, Less, JavaScript,
JSX, TypeScript, TSX, JSON, JSONC, Markdown, YAML, XML/SVG, shell, Python, Java, Kotlin, C, C++, Go,
Rust, PHP, SQL, TOML, INI, Vue, Svelte, Dockerfile, Makefile, diff, GraphQL, Lua, Ruby. Embedded languages
(CSS and JavaScript inside HTML, for example) are included automatically. Brackets, quotes and comment
markers per language come from `tools/textmate-config/`. The licenses of the grammars and themes are
copied to `textmate/notices/`.

## Working with files

- A tap on a file in the tree opens it in a tab. The menu has Save, Undo, Redo, Word wrap and Close file.
- Unsaved changes are written when the screen leaves the foreground. Closing a tab with unsaved changes asks first.
- A file changed outside the editor (for example by git in the terminal) is reloaded when you come back,
  unless you have unsaved changes in it; then your version is kept and a message tells you.
- Only valid UTF-8 text up to 5 MB is opened, so that saving never alters bytes you did not edit.
  Line endings (LF or CRLF) and a byte order mark are preserved. Saving goes through a temporary file, keeps
  file permissions and follows symbolic links.
- The row of symbol keys above the keyboard types `{ } ( ) [ ] < > ; : " ' / = $ # \ | & ! _ -` and two spaces for TAB.

## Not yet there

Find and replace, code completion (language servers) and formatting come in later phases.
