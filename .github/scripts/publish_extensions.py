#!/usr/bin/env python3
"""
Publishes built extensions into the extensions repo (tadamiorg/tadami-extensions).

    publish_extensions.py tasks   --extensions "en.animepahe fr.franime"
        Prints the Gradle tasks building those extensions.

    publish_extensions.py publish --extensions "en.animepahe fr.franime" --repo <extensions repo checkout>
                                  --aapt2 <aapt2> [--apksigner <apksigner>] [--dexdump <dexdump>]
                                  [--summary <file>] [--commit-message <file>]
        For each built extension: copies its APK (replacing the previous one) and its icon into the repo, and
        adds or replaces its entry in index.json and index.min.json. The other entries of each index are left
        as they are, including the extensions left out of it on purpose.

Refuses to publish an APK that the app couldn't install as an update (same or lower version code than the
published one, or signed with another key), or that uses extension API members its lib version doesn't cover
(older apps would load it and crash).
"""
import argparse
import json
import re
import shutil
import subprocess
import sys
import tempfile
import zipfile
from pathlib import Path

SOURCE_ROOT = Path(__file__).resolve().parents[2]
EXTENSIONS_DIR = SOURCE_ROOT / "extensions"
ICON = Path("res") / "mipmap-xhdpi" / "ic_launcher.png"

SOURCE_ID = re.compile(r"\bsourceId\s*=\s*(\d+)")
SOURCE_NAME = re.compile(r"override\s+val\s+name\s*(?::\s*String)?\s*=\s*\"([^\"]+)\"")
BASE_URL = re.compile(r"\bDEFAULT_BASE_URL\s*=\s*\"([^\"]+)\"")


class PublishError(Exception):
    pass


def parse_extensions(raw):
    """ "en.animepahe, fr.franime" -> [("en", "animepahe"), ("fr", "franime")], each checked to exist. """
    names = [n for n in re.split(r"[\s,]+", raw or "") if n]
    if not names:
        raise PublishError("No extension given, expected <lang>.<name> separated by spaces or commas")
    extensions = []
    for name in names:
        lang, _, ext = name.partition(".")
        if not lang or not ext or "/" in name or "\\" in name:
            raise PublishError(f'"{name}" is not <lang>.<name>')
        if not (EXTENSIONS_DIR / lang / ext).is_dir():
            available = sorted(f"{d.parent.name}.{d.name}" for d in EXTENSIONS_DIR.glob("*/*") if d.is_dir())
            raise PublishError(f'No extension "{name}". Available: {", ".join(available)}')
        if (lang, ext) not in extensions:
            extensions.append((lang, ext))
    return extensions


def gradle_tasks(extensions):
    return [f":extensions:{lang}:{ext}:assembleRelease" for lang, ext in extensions]


def built_apk(module):
    apks = sorted((module / "build" / "outputs" / "apk" / "release").glob("*.apk"))
    if len(apks) != 1:
        raise PublishError(f"Expected one release APK in {module}/build/outputs/apk/release, found {len(apks)}")
    return apks[0]


def badging(aapt2, apk):
    """Package name, version code, version name and label, read from the APK itself."""
    out = subprocess.run([aapt2, "dump", "badging", str(apk)], capture_output=True, text=True, check=True).stdout
    package = re.search(r"package: name='([^']+)' versionCode='(\d+)' versionName='([^']*)'", out)
    label = re.search(r"application-label:'([^']*)'", out)
    if not package or not label:
        raise PublishError(f"Could not read the package info of {apk.name}")
    return {"pkg": package.group(1), "code": int(package.group(2)), "version": package.group(3), "name": label.group(1)}


def source_info(module):
    """The extension's source, as declared in its code: sourceId, name and DEFAULT_BASE_URL."""
    code = "\n".join(p.read_text(encoding="utf-8") for p in sorted((module / "src").rglob("*.kt")))
    found = {}
    for key, pattern in (("id", SOURCE_ID), ("name", SOURCE_NAME), ("baseUrl", BASE_URL)):
        values = sorted(set(pattern.findall(code)))
        if len(values) != 1:
            raise PublishError(
                f"{module.relative_to(SOURCE_ROOT)}: expected one {pattern.pattern!r}, found {values or 'none'}. "
                "The index entry is generated from it."
            )
        found[key] = values[0]
    return found


def signer_digests(apksigner, apk):
    out = subprocess.run([apksigner, "verify", "--print-certs", str(apk)], capture_output=True, text=True)
    if out.returncode != 0:
        raise PublishError(f"{apk.name} is not properly signed: {out.stderr.strip() or out.stdout.strip()}")
    return sorted(set(re.findall(r"certificate SHA-256 digest: ([0-9a-f]+)", out.stdout)))


# Extension API members that need a lib version. An app accepts lib versions up to its own max only, so an
# extension using a newer member must declare that version (apiVersion in its build.gradle.kts): built as an
# older lib, it would be offered to older apps and crash there on the missing member. One entry per addition.
API_REQUIREMENTS = [
    # (method reference in the APK's code, minimum lib version, member)
    ("Lcom/sf/tadami/source/model/SAnime;.setLanguage:", (1, 6), "SAnime.language"),
]


def lib_version(version_name):
    """ "1.6.3" -> (1, 6), None when the version name isn't <major>.<minor>.<code>. """
    parts = version_name.rsplit(".", 1)[0].split(".")
    if len(parts) != 2 or not all(p.isdigit() for p in parts):
        return None
    return int(parts[0]), int(parts[1])


def api_problems(dexdump, apk, version_name):
    """The API members the APK's code uses that its lib version doesn't cover."""
    lib = lib_version(version_name)
    if lib is None:
        return [f"its version name {version_name!r} has no <major>.<minor> lib version"]
    with tempfile.TemporaryDirectory() as tmp:
        with zipfile.ZipFile(apk) as archive:
            dexes = [archive.extract(n, tmp) for n in archive.namelist() if re.fullmatch(r"classes\d*\.dex", n)]
        code = "".join(
            subprocess.run([dexdump, "-d", dex], capture_output=True, text=True, check=True).stdout for dex in dexes
        )
    return [
        f"it uses {member}, which needs lib {'.'.join(map(str, minimum))}, but is built as lib "
        f"{'.'.join(map(str, lib))}: set apiVersion to {'.'.join(map(str, minimum))} in its build.gradle.kts"
        for reference, minimum, member in API_REQUIREMENTS
        if reference in code and lib < minimum
    ]


# Both indexes are kept as they are apart from the released extensions: some extensions are left out on
# purpose (unmaintained or broken), and the app reads index.min.json
INDEXES = {
    "index.json": {"indent": "\t"},
    "index.min.json": {"separators": (",", ":")},
}


def read_index(path):
    if not path.exists():
        return []
    return json.loads(path.read_text(encoding="utf-8-sig"))


def write_index(path, index):
    options = INDEXES[path.name]
    text = json.dumps(index, ensure_ascii=False, **options) + ("\n" if "indent" in options else "")
    path.write_text(text, encoding="utf-8")


def upsert(index, entry):
    """Replaces the entry of the same package in place, or appends it."""
    for i, existing in enumerate(index):
        if existing.get("pkg") == entry["pkg"]:
            index[i] = entry
            return
    index.append(entry)


def publish(extensions, repo, aapt2, apksigner, dexdump=None):
    indexes = {name: read_index(repo / name) for name in INDEXES}
    # Version checks against whichever index lists the extension, with the highest code
    by_pkg = {}
    for index in indexes.values():
        for entry in index:
            known = by_pkg.get(entry["pkg"])
            if known is None or int(entry["code"]) > int(known["code"]):
                by_pkg[entry["pkg"]] = entry

    # Every extension is checked before any file changes, so a refusal leaves the repo untouched
    plans = []
    for lang, ext in extensions:
        module = EXTENSIONS_DIR / lang / ext
        apk = built_apk(module)
        info = badging(aapt2, apk)
        source = source_info(module)
        previous = by_pkg.get(info["pkg"])
        # The previous APK of this extension, listed or not (some are published without an index entry)
        old_apks = sorted(p for p in (repo / "apk").glob(f"tadami-{lang}.{ext}-v*.apk") if p.name != apk.name)

        if previous and info["code"] <= int(previous["code"]):
            raise PublishError(
                f"{lang}.{ext}: version code {info['code']} is not above the published {previous['code']}, "
                "the app wouldn't see it as an update. Bump extVersionCode."
            )
        if apksigner:
            new_signers = signer_digests(apksigner, apk)
            for old in old_apks:
                if signer_digests(apksigner, old) != new_signers:
                    raise PublishError(
                        f"{lang}.{ext}: {apk.name} isn't signed with the key of the published {old.name}, "
                        "users couldn't update to it. Check the signing secrets."
                    )
        if dexdump:
            problems = api_problems(dexdump, apk, info["version"])
            if problems:
                raise PublishError(f"{lang}.{ext}: " + "; ".join(problems))
        else:
            print(f"::warning::{lang}.{ext}: no dexdump, the lib version wasn't checked against the API it uses",
                  file=sys.stderr)
        plans.append((lang, ext, module, apk, info, source, previous, old_apks))

    (repo / "apk").mkdir(exist_ok=True)
    (repo / "icon").mkdir(exist_ok=True)
    released = []
    for lang, ext, module, apk, info, source, previous, old_apks in plans:
        for old in old_apks:
            old.unlink()
        shutil.copyfile(apk, repo / "apk" / apk.name)
        icon = module / ICON
        if icon.exists():
            shutil.copyfile(icon, repo / "icon" / f"{info['pkg']}.png")

        entry = {
            "name": info["name"],
            "pkg": info["pkg"],
            "apk": apk.name,
            "lang": lang,
            "code": info["code"],
            "version": info["version"],
            "sources": [
                {
                    "name": source["name"],
                    "lang": lang,
                    "id": source["id"],
                    "baseUrl": source["baseUrl"],
                    "versionId": info["code"],
                }
            ],
        }
        # Released means listed: an extension left out of an index comes back in it
        for index in indexes.values():
            upsert(index, entry)
        released.append((f"{lang}.{ext}", previous["version"] if previous else None, info["version"]))

    for name, index in indexes.items():
        write_index(repo / name, index)
    return released


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("command", choices=["tasks", "publish"])
    parser.add_argument("--extensions", required=True)
    parser.add_argument("--repo", type=Path)
    parser.add_argument("--aapt2")
    parser.add_argument("--apksigner")
    parser.add_argument("--dexdump", help="Checks the lib version covers the extension API the APK uses")
    parser.add_argument("--summary", type=Path, help="Markdown summary to append to (GITHUB_STEP_SUMMARY)")
    parser.add_argument("--commit-message", type=Path, help="Where to write the extensions repo commit message")
    args = parser.parse_args()

    try:
        extensions = parse_extensions(args.extensions)
        if args.command == "tasks":
            print(" ".join(gradle_tasks(extensions)))
            return
        if not args.repo or not args.aapt2:
            raise PublishError("publish needs --repo and --aapt2")
        released = publish(extensions, args.repo, args.aapt2, args.apksigner, args.dexdump)
    except PublishError as e:
        print(f"::error::{e}", file=sys.stderr)
        sys.exit(1)

    lines = [f"- {name}: {old or 'new'} -> {new}" for name, old, new in released]
    print("\n".join(lines))
    if args.commit_message:
        names = ", ".join(f"{name} {new}" for name, _, new in released)
        args.commit_message.write_text(f"Release {names}\n\n" + "\n".join(lines) + "\n", encoding="utf-8")
    if args.summary:
        with args.summary.open("a", encoding="utf-8") as summary:
            summary.write("### Released extensions\n\n" + "\n".join(lines) + "\n")


if __name__ == "__main__":
    main()
