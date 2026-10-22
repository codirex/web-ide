# WebIDE

A web-development IDE for Android phones: code editor, terminal, Git, project templates
and local preview, all on the device.

- Package: `org.codirex.webide`
- Language: Java (Android), UI language: English
- Build: Gradle 9.6.1, AGP 9.3.1, JDK 17, minSdk 26, targetSdk 28, ABIs `arm64-v8a` and `armeabi-v7a`
- License: GPL-3.0-or-later. Add the official license text as `LICENSE` (https://www.gnu.org/licenses/gpl-3.0.txt).

## Build

1. Make sure the SDK has the platform set in `gradle.properties` (`webide.compileSdk`, default 37).
   If it is missing, set the value to 36.
2. Generate the Gradle wrapper once (`gradle wrapper`), or open the project in your IDE.
   `gradle/wrapper/gradle-wrapper.properties` already points to Gradle 9.6.1.
3. Build `:app:assembleDebug`.

## Status

Phase 4: editor. Files open in tabs with syntax highlighting (Sora Editor and TextMate grammars), saving,
undo and redo. See `docs/EDITOR.md` for how to install the highlighting assets. Earlier phases: runtime and
terminal (`docs/BOOTSTRAP.md`), projects and the project screen (drawer with file tree, bottom sheet).

See `docs/ARCHITECTURE.md` for modules and decisions.
