#!/usr/bin/env python3
"""Builds the TextMate assets of the editor (syntax highlighting grammars and themes).

Input: the unpacked npm packages "tm-grammars" (folder grammars/) and "tm-themes" (folder themes/).
Output: a "textmate" folder to place in app/src/main/assets/ with
  languages.json   the grammar registry read by Sora Editor
  file-types.txt   file extension / file name to scope name
  themes.txt       which theme is used in light and in dark mode
  grammars/ themes/ configs/ notices/

Only the languages listed below are included, together with every grammar they embed or include.
"""
import argparse
import json
import os
import shutil
import sys

# (grammar name in tm-grammars, language configuration group, extensions, exact file names)
LANGUAGES = [
    ("html", "html", ["html", "htm", "xhtml"], []),
    ("css", "css", ["css"], []),
    ("scss", "scss", ["scss"], []),
    ("sass", "scss", ["sass"], []),
    ("less", "scss", ["less"], []),
    ("javascript", "c-like", ["js", "mjs", "cjs"], []),
    ("jsx", "c-like", ["jsx"], []),
    ("typescript", "c-like", ["ts", "mts", "cts"], []),
    ("tsx", "c-like", ["tsx"], []),
    ("json", "json", ["json", "webmanifest", "map"], [".babelrc", ".prettierrc"]),
    ("jsonc", "jsonc", ["jsonc"], ["tsconfig.json", "jsconfig.json", ".eslintrc.json"]),
    ("markdown", "markdown", ["md", "markdown"], []),
    ("yaml", "hash", ["yml", "yaml"], []),
    ("xml", "html", ["xml", "xsd", "xsl", "xslt", "plist", "svg", "csproj"], []),
    ("shellscript", "hash", ["sh", "bash", "zsh"], [".bashrc", ".zshrc", ".profile"]),
    ("python", "hash", ["py", "pyw"], []),
    ("java", "c-like", ["java"], []),
    ("kotlin", "c-like", ["kt", "kts"], []),
    ("c", "c-like", ["c", "h"], []),
    ("cpp", "c-like", ["cpp", "cc", "cxx", "hpp", "hh", "hxx"], []),
    ("go", "c-like", ["go"], []),
    ("rust", "c-like", ["rs"], []),
    ("php", "c-like", ["php"], []),
    ("sql", "sql", ["sql"], []),
    ("toml", "hash", ["toml"], []),
    ("ini", "hash", ["ini", "cfg", "conf"], [".env"]),
    ("vue", "html", ["vue"], []),
    ("svelte", "html", ["svelte"], []),
    ("docker", "hash", [], ["dockerfile"]),
    ("make", "hash", ["mk"], ["makefile"]),
    ("diff", "generic", ["diff", "patch"], []),
    ("graphql", "hash", ["graphql", "gql"], []),
    ("lua", "lua", ["lua"], []),
    ("ruby", "hash", ["rb"], []),
]

# The web languages must be present; the others are skipped with a warning if a grammar is missing.
REQUIRED = {"html", "css", "javascript", "typescript", "json", "markdown"}

LIGHT_THEMES = ["github-light", "light-plus", "min-light"]
DARK_THEMES = ["github-dark", "dark-plus", "one-dark-pro"]


def find_includes(node, found):
    """Collects the scope names that a grammar includes from other grammars."""
    if isinstance(node, dict):
        value = node.get("include")
        if isinstance(value, str) and value[:1] not in ("#", "$"):
            found.add(value.split("#")[0])
        for child in node.values():
            find_includes(child, found)
    elif isinstance(node, list):
        for child in node:
            find_includes(child, found)


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--grammars", required=True, help="tm-grammars/grammars folder")
    ap.add_argument("--themes", required=True, help="tm-themes/themes folder")
    ap.add_argument("--configs", required=True, help="language configuration groups (tools/textmate-config)")
    ap.add_argument("--out", required=True, help="output folder, named textmate")
    ap.add_argument("--notices", action="append", default=[],
                    help="package folders whose NOTICE and LICENSE files are copied (repeatable)")
    ap.add_argument("--light", default=None, help="light theme id")
    ap.add_argument("--dark", default=None, help="dark theme id")
    args = ap.parse_args()

    # ---- index every grammar by file name and scope name ----
    by_name, by_scope, data = {}, {}, {}
    for f in sorted(os.listdir(args.grammars)):
        if not f.endswith(".json"):
            continue
        path = os.path.join(args.grammars, f)
        with open(path, encoding="utf-8") as fh:
            grammar = json.load(fh)
        stem = f[:-5]
        by_name[stem] = path
        data[stem] = grammar
        scope = grammar.get("scopeName")
        if scope:
            by_scope[scope] = stem
    print("grammars available: %d" % len(by_name))

    # ---- chosen languages and everything they depend on ----
    chosen, group_of = [], {}
    for name, group, _exts, _names in LANGUAGES:
        if name not in by_name:
            if name in REQUIRED:
                sys.exit("Required grammar missing: " + name)
            print("skipping %s: no grammar" % name)
            continue
        chosen.append(name)
        group_of[name] = group

    needed, queue = set(), list(chosen)
    while queue:
        name = queue.pop()
        if name in needed:
            continue
        needed.add(name)
        includes = set()
        find_includes(data[name], includes)
        for scope in includes:
            dep = by_scope.get(scope)
            if dep and dep not in needed:
                queue.append(dep)
    print("grammars included: %d (%d chosen, %d only as dependencies)"
          % (len(needed), len(chosen), len(needed) - len(chosen)))

    # ---- write the output ----
    out = args.out
    if os.path.isdir(out):
        shutil.rmtree(out)
    for sub in ("grammars", "themes", "configs", "notices"):
        os.makedirs(os.path.join(out, sub))

    used_groups = set()
    languages = []
    for name in sorted(needed):
        shutil.copyfile(by_name[name], os.path.join(out, "grammars", name + ".json"))
        entry = {
            "name": name,
            "scopeName": data[name]["scopeName"],
            "grammar": "textmate/grammars/%s.json" % name,
        }
        group = group_of.get(name)
        if group:
            entry["languageConfiguration"] = "textmate/configs/%s.json" % group
            used_groups.add(group)
        languages.append(entry)
    with open(os.path.join(out, "languages.json"), "w", encoding="utf-8") as fh:
        json.dump({"languages": languages}, fh, indent=2)

    for group in sorted(used_groups):
        src = os.path.join(args.configs, group + ".json")
        if not os.path.isfile(src):
            sys.exit("Missing language configuration: " + src)
        shutil.copyfile(src, os.path.join(out, "configs", group + ".json"))

    lines = ["# extension=scope name, or name:<file name>=scope name"]
    for name, _group, exts, names in LANGUAGES:
        if name not in chosen:
            continue
        scope = data[name]["scopeName"]
        lines += ["%s=%s" % (e, scope) for e in exts]
        lines += ["name:%s=%s" % (n, scope) for n in names]
    with open(os.path.join(out, "file-types.txt"), "w", encoding="utf-8") as fh:
        fh.write("\n".join(lines) + "\n")

    # ---- themes ----
    def pick(requested, fallbacks, kind):
        for candidate in ([requested] if requested else []) + fallbacks:
            path = os.path.join(args.themes, candidate + ".json")
            if os.path.isfile(path):
                shutil.copyfile(path, os.path.join(out, "themes", candidate + ".json"))
                return candidate
        sys.exit("No %s theme found" % kind)

    light = pick(args.light, LIGHT_THEMES, "light")
    dark = pick(args.dark, DARK_THEMES, "dark")
    with open(os.path.join(out, "themes.txt"), "w", encoding="utf-8") as fh:
        fh.write("light=%s\ndark=%s\n" % (light, dark))

    # ---- licenses ----
    copied = 0
    for folder in args.notices:
        if not os.path.isdir(folder):
            continue
        for f in sorted(os.listdir(folder)):
            if f.upper().startswith(("NOTICE", "LICENSE")):
                tag = os.path.basename(os.path.dirname(os.path.abspath(folder.rstrip("/\\"))))
                shutil.copyfile(os.path.join(folder, f), os.path.join(out, "notices", "%s-%s" % (tag, f)))
                copied += 1
    print("themes: light=%s dark=%s; notice files copied: %d" % (light, dark, copied))


if __name__ == "__main__":
    main()
