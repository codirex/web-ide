# Runtime bootstrap

The runtime (shell, coreutils, Node.js, npm, Git) is a bootstrap archive that the app unpacks
into `/data/data/org.codirex.webide/files/usr`. Prebuilt Termux packages contain the path
`/data/data/com.termux/files/usr`, so the archive must be built for `org.codirex.webide`.

## Build

The build runs in Docker on Linux x86_64 and compiles the packages from source, including Node.js.
It does not fit on a phone.

1. Push this project to GitHub.
2. Open **Actions → Build bootstrap → Run workflow**. Defaults: architecture `aarch64`,
   extra packages `nodejs,git`.
3. When the run finishes, download the artifact `bootstrap`. GitHub wraps it in a zip, so unzip it
   once to get `bootstrap-aarch64.zip`.
4. If the run fails, download `build.log` from the same artifact and send it to me.

Things to check on the first run (not verified yet):

- Whether the job finishes within six hours.
- Whether `--add nodejs,git` is the right syntax for the script; `build-bootstraps.sh --help` shows it.
- The step "Look for leftover com.termux paths" lists files that still mention the old path.

## Install

Option A (no rebuild of the app): copy `bootstrap-aarch64.zip` to the phone, open the app and tap
**Install from file**.

Option B: put the file in `app/src/main/assets/bootstrap/` and rebuild. The button
**Install bundled archive** appears.

## Verify

In the **Runtime** card run, one at a time:

```
node -v && npm -v
git --version
echo $PATH
cd ~ && mkdir t && cd t && npm init -y && npm install left-pad && node -e "console.log(require('left-pad')('x',3))"
```

Send me the output of each, or the error text.
