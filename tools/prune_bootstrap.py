#!/usr/bin/env python3
"""Remove the packages the app does not need from a bootstrap archive.

build-bootstraps.sh puts every package it built into the archive, including build-time
tools (python, doxygen, docbook, ...). This keeps the packages listed with --keep (plus the
default base set), everything they depend on, and removes the rest: their files, their
symlinks, their dpkg records.

It works on the finished zip and takes seconds, so the long package build never has to be
repeated just to change what is included.

Usage: prune_bootstrap.py bootstrap-aarch64-full.zip bootstrap-aarch64.zip --keep nodejs,git
"""
import argparse
import re
import sys
import zipfile

BASE_KEEP = (
    "apt,bash,bzip2,command-not-found,coreutils,dash,diffutils,findutils,gawk,grep,gzip,less,"
    "procps,psmisc,sed,tar,termux-core,termux-exec,termux-keyring,termux-tools,util-linux,ed,"
    "debianutils,dos2unix,inetutils,lsof,nano,net-tools,patch,unzip"
)
SYMLINKS = "SYMLINKS.txt"
SEPARATOR = "\u2190"
STATUS = "var/lib/dpkg/status"
INFO = "var/lib/dpkg/info/"


def dep_names(field):
    result = []
    for part in field.split(","):
        for alt in part.split("|"):
            name = re.sub(r"\(.*?\)", "", alt).strip().split(":")[0].strip()
            if name:
                result.append(name)
    return result


def parse_status(text):
    """Returns {package: (fields, raw stanza)} from a dpkg status file."""
    packages = {}
    for raw in re.split(r"\n\s*\n", text.strip("\n")):
        if not raw.strip():
            continue
        fields, key = {}, None
        for line in raw.splitlines():
            if line[:1] in (" ", "\t") and key:
                fields[key] += " " + line.strip()
            elif ":" in line:
                key, _, value = line.partition(":")
                fields[key] = value.strip()
        if "Package" in fields:
            packages[fields["Package"]] = (fields, raw)
    return packages


def rel(path):
    """Turns a path from a dpkg .list file or SYMLINKS.txt into a path inside the archive."""
    p = path.strip()
    if p.startswith("./"):
        p = p[2:]
    p = p.lstrip("/")
    p = re.sub(r"^data/data/[^/]+/files/usr/?", "", p)
    return p.rstrip("/")


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("source")
    ap.add_argument("target")
    ap.add_argument("--keep", default="", help="extra packages to keep, comma separated")
    args = ap.parse_args()

    roots = [r.strip() for r in (BASE_KEEP + "," + args.keep).split(",") if r.strip()]

    with zipfile.ZipFile(args.source) as zin:
        names = zin.namelist()
        if STATUS not in names:
            sys.exit("%s not found in the archive; is this a bootstrap archive?" % STATUS)
        packages = parse_status(zin.read(STATUS).decode("utf-8", "replace"))

        provided = {}
        for name, (fields, _) in packages.items():
            for v in dep_names(fields.get("Provides", "")):
                provided[v] = name

        keep, missing, queue = set(), set(), list(roots)
        while queue:
            n = queue.pop()
            n = n if n in packages else provided.get(n, n)
            if n in keep:
                continue
            if n not in packages:
                missing.add(n)
                continue
            keep.add(n)
            fields = packages[n][0]
            queue.extend(dep_names(fields.get("Depends", "")) + dep_names(fields.get("Pre-Depends", "")))
        drop = set(packages) - keep

        def listed(pkg):
            name = INFO + pkg + ".list"
            if name not in names:
                return set()
            lines = zin.read(name).decode("utf-8", "replace").splitlines()
            return {r for r in (rel(l) for l in lines) if r}

        kept_paths = set()
        for p in keep:
            kept_paths |= listed(p)
        dropped_paths = set()
        for p in drop:
            dropped_paths |= listed(p)
        dropped_paths -= kept_paths

        # Symbolic links live in SYMLINKS.txt, not as zip entries.
        link_lines = zin.read(SYMLINKS).decode("utf-8").splitlines() if SYMLINKS in names else []
        kept_links, dropped_links = [], 0
        for line in link_lines:
            if SEPARATOR not in line:
                kept_links.append(line)
                continue
            if rel(line.split(SEPARATOR, 1)[1]) in dropped_paths:
                dropped_links += 1
            else:
                kept_links.append(line)

        def drop_info(name):
            if not name.startswith(INFO) or "." not in name[len(INFO):]:
                return False
            base = name[len(INFO):].rsplit(".", 1)[0]
            return base in drop

        # Files that stay, to decide which directories are now empty.
        survivors = set()
        for n in names:
            if n.endswith("/") or n == SYMLINKS or n == STATUS or drop_info(n):
                continue
            if rel(n) in dropped_paths:
                continue
            survivors.add(rel(n))
        for line in kept_links:
            if SEPARATOR in line:
                survivors.add(rel(line.split(SEPARATOR, 1)[1]))

        def dir_is_empty(d):
            prefix = d + "/"
            return not any(s.startswith(prefix) for s in survivors)

        new_status = "\n\n".join(packages[p][1] for p in packages if p in keep) + "\n"

        removed_files = removed_dirs = 0
        with zipfile.ZipFile(args.target, "w", zipfile.ZIP_DEFLATED, compresslevel=6) as zout:
            for info in zin.infolist():
                n = info.filename
                if n == SYMLINKS:
                    zout.writestr(info, ("\n".join(kept_links) + "\n").encode("utf-8"))
                    continue
                if n == STATUS:
                    zout.writestr(info, new_status.encode("utf-8"))
                    continue
                if drop_info(n):
                    continue
                if n.endswith("/"):
                    d = rel(n)
                    if d in dropped_paths and dir_is_empty(d):
                        removed_dirs += 1
                        continue
                    zout.writestr(info, b"")
                    continue
                if rel(n) in dropped_paths:
                    removed_files += 1
                    continue
                zout.writestr(info, zin.read(info), compress_type=info.compress_type)

    print("packages: kept %d, removed %d" % (len(keep), len(drop)))
    print("files removed: %d, links removed: %d, empty directories removed: %d"
          % (removed_files, dropped_links, removed_dirs))
    print("removed:", " ".join(sorted(drop)))
    if missing:
        print("not in the archive (ignored):", " ".join(sorted(missing)))


if __name__ == "__main__":
    main()
