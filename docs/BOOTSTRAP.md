# Runtime bootstrap

The runtime (shell, coreutils, Node.js, Git, npm) is a bootstrap archive that the app unpacks into
`/data/data/org.codirex.webide/files/usr`. Prebuilt Termux packages contain the path
`/data/data/com.termux/files/usr`, so the archive must be built for `org.codirex.webide`.

## Build (about 2.5 to 3.5 hours)

1. Push this project to GitHub (the workflow files must be on the default branch).
2. Open **Actions → Build bootstrap → Run workflow**. Defaults: architecture `aarch64`,
   extra packages `nodejs,git`, termux-packages ref `master`. Turn on `preflight_only` first to test
   the hand-over of the archive in a few minutes.
3. When the run finishes, download the artifacts (GitHub wraps each in a zip, so unzip once):
   - `bootstrap-full`: `bootstrap-aarch64-full.zip`, exactly as the Termux script produced it.
   - `bootstrap`: `bootstrap-aarch64.zip`, the final archive: build-time tools removed
     (python, doxygen, docbook, ...) and npm added. Plus `build.log` and `prune.log`.
   - `debs`: `debs.tar` with every package built for this app name (for a future apt repository).

The Node.js package built here has no npm, so npm is added from the npm registry
(`tools/add_npm.py`). `tools/prune_bootstrap.py` removes the packages that are not needed.

## Change what is included without rebuilding

**Actions → Prune bootstrap → Run workflow** with the run id of a finished build (the number at the
end of the run's URL). It takes the `bootstrap-full` artifact of that run, prunes it, adds npm
(`npm_version`, default 11) and uploads `bootstrap-pruned`. It takes a few minutes.

## Install

Option A (no rebuild of the app): copy `bootstrap-aarch64.zip` to the phone, open the app and tap
**Install from file**. After unpacking, the app runs a first login shell, which executes the
bootstrap's second-stage setup; its result is shown under the status line.

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

If a command fails because of `LD_PRELOAD`, try it again with `LD_PRELOAD= ` in front, for example
`LD_PRELOAD= node -v`.
