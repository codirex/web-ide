# Runtime bootstrap

The runtime (shell, coreutils, Node.js, npm, Git) is a bootstrap archive that the app unpacks
into `/data/data/org.codirex.webide/files/usr`. Prebuilt Termux packages contain the path
`/data/data/com.termux/files/usr`, so the archive must be built for `org.codirex.webide`.

## Build

The build runs in Docker on Linux x86_64 and compiles every package from source. It takes about
3.5 hours (Node.js alone about 2.5) and does not fit on a phone.

1. Push this project to GitHub (the workflow files must be on the default branch).
2. Open **Actions → Build bootstrap → Run workflow**. Defaults: architecture `aarch64`,
   extra packages `nodejs,git`, termux-packages ref `master`.
3. When the run finishes, download the artifacts. GitHub wraps each in a zip, so unzip once.
   - `bootstrap-full`: `bootstrap-aarch64-full.zip`, as the Termux script produced it. It is
     uploaded before anything else can go wrong.
   - `bootstrap`: `bootstrap-aarch64.zip`, the same archive without build-time tools (python,
     doxygen, docbook, ...), plus `build.log` and `prune.log`.
4. If the small archive is missing or wrong, run **Actions → Prune bootstrap** with the run id of
   the build. It makes the small archive from `bootstrap-full` in a few minutes.

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
