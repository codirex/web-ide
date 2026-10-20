#!/usr/bin/env python3
"""Add npm to a bootstrap archive.

The Termux Node.js package that was built does not contain npm. npm is plain JavaScript, so it can
be added to the finished archive from the tarball that "npm pack npm@<version>" downloads.

Adds:
  lib/node_modules/npm/...   the contents of the tarball (its "package/" folder)
  bin/npm, bin/npx           small shell scripts that start node with npm's own entry files

Usage: add_npm.py bootstrap.zip bootstrap-with-npm.zip npm-11.x.y.tgz
"""
import argparse
import sys
import tarfile
import zipfile

DEFAULT_PREFIX = "/data/data/org.codirex.webide/files/usr"
NPM_DIR = "lib/node_modules/npm/"


def wrapper(prefix, entry):
    return (
        "#!%(p)s/bin/sh\n"
        'exec "%(p)s/bin/node" "%(p)s/lib/node_modules/npm/bin/%(e)s" "$@"\n'
    ) % {"p": prefix, "e": entry}


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("source")
    ap.add_argument("target")
    ap.add_argument("tarball")
    ap.add_argument("--prefix", default=DEFAULT_PREFIX)
    args = ap.parse_args()

    with zipfile.ZipFile(args.source) as zin:
        names = set(zin.namelist())
        if any(n.startswith(NPM_DIR) for n in names) or "bin/npm" in names:
            sys.exit("The archive already contains npm; nothing to do.")

        with zipfile.ZipFile(args.target, "w", zipfile.ZIP_DEFLATED, compresslevel=6) as zout:
            for info in zin.infolist():
                zout.writestr(info, zin.read(info), compress_type=info.compress_type)

            files = skipped = 0
            with tarfile.open(args.tarball, "r:*") as tar:
                for member in tar:
                    if not member.name.startswith("package/"):
                        continue
                    rel = member.name[len("package/"):]
                    if not rel or member.isdir():
                        continue
                    if not member.isfile() or ".." in rel.split("/"):
                        skipped += 1
                        continue
                    zout.writestr(NPM_DIR + rel, tar.extractfile(member).read())
                    files += 1

            if files == 0:
                sys.exit("The tarball has no package/ files; is it an npm tarball?")
            if (NPM_DIR + "bin/npm-cli.js") not in zout.namelist():
                sys.exit("bin/npm-cli.js is missing from the tarball.")

            zout.writestr("bin/npm", wrapper(args.prefix, "npm-cli.js"))
            zout.writestr("bin/npx", wrapper(args.prefix, "npx-cli.js"))

    print("added npm: %d files (%d skipped), plus bin/npm and bin/npx" % (files, skipped))


if __name__ == "__main__":
    main()
