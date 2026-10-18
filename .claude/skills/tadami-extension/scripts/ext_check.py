#!/usr/bin/env python3
"""
Pre-release checks for Tadami extensions, run from anywhere inside tadami-extensions-source.

    ext_check.py ids [--published <dir | base URL>]
        Source ids in use across extensions/*/* and by published extensions that have no source
        folder anymore. A new source doesn't pick one: it uses the generated BuildConfig.SOURCE_ID.

    ext_check.py check <lang>.<name> [--published <extensions repo checkout dir | base URL>]
        Checks what the "Release extensions" workflow relies on (it generates the index entry from the
        code and the build, see .github/scripts/publish_extensions.py, whose regexes are mirrored here):
        - exactly one source id declaration, `sourceId = N` (a released source) or
          `sourceId = BuildConfig.SOURCE_ID` (generated, see common.gradle), and exactly one
          `override val name: String = "X"` and `DEFAULT_BASE_URL = "..."`
        - a source id no other extension uses (the id of a build, build/outputs/tadami/sources.json, when
          there is one)
        - pkgNameSuffix == "<lang>.<name>", AndroidManifest.xml, res/mipmap-xhdpi/ic_launcher.png
        - extVersionCode above the published code (index.json and index.min.json of the published repo,
          GitHub by default)
        - notes a published source id that differs from the code's (the release republishes the code's)
        Exits 1 when something would make the release fail or ship a broken entry.
"""
import argparse
import json
import re
import sys
import urllib.error
import urllib.request
from pathlib import Path

# Same patterns as .github/scripts/publish_extensions.py: keep them in sync
SOURCE_ID = re.compile(r"\bsourceId\s*=\s*(\d+)")
# A generated id, as common.gradle reads it
GENERATED_ID = re.compile(r"(?:\bsourceId|\boverride\s+val\s+id)\s*(?::\s*Long\s*)?=\s*BuildConfig\.(SOURCE_ID\w*)")
SOURCES_JSON = Path("build") / "outputs" / "tadami" / "sources.json"
SOURCE_NAME = re.compile(r"override\s+val\s+name\s*(?::\s*String)?\s*=\s*\"([^\"]+)\"")
BASE_URL = re.compile(r"\bDEFAULT_BASE_URL\s*=\s*\"([^\"]+)\"")
VERSION_CODE = re.compile(r"set\(\s*\"extVersionCode\"\s*,\s*(\d+)\s*\)")
PKG_SUFFIX = re.compile(r"set\(\s*\"pkgNameSuffix\"\s*,\s*\"([^\"]+)\"\s*\)")
API_VERSION = re.compile(r"set\(\s*\"apiVersion\"\s*,\s*\"([^\"]+)\"\s*\)")

PACKAGE_PREFIX = "com.sf.tadami.extension."
PUBLISHED_REPO = "https://raw.githubusercontent.com/tadamiorg/tadami-extensions/main"
INDEXES = ("index.json", "index.min.json")


def repo_root():
    here = Path.cwd().resolve()
    for candidate in [here, *here.parents, Path(__file__).resolve().parents[3]]:
        if (candidate / "extensions").is_dir() and (candidate / "common.gradle").is_file():
            return candidate
    sys.exit("Run this inside tadami-extensions-source (no extensions/ + common.gradle found)")


def modules(root):
    return sorted(d for d in (root / "extensions").glob("*/*") if d.is_dir())


def module_code(module):
    return "\n".join(p.read_text(encoding="utf-8") for p in sorted((module / "src").rglob("*.kt")))


def built_ids(module):
    """The source ids of the module's last build (common.gradle writes them), None when it wasn't built."""
    path = module / SOURCES_JSON
    if not path.exists():
        return None
    return [source["id"] for source in json.loads(path.read_text(encoding="utf-8"))["sources"] if source["id"]]


def ids_in_use(root):
    """Literal ids, and the generated ones of the built modules."""
    used = {}
    for module in modules(root):
        ids = set(SOURCE_ID.findall(module_code(module))) | set(built_ids(module) or [])
        for source_id in ids:
            used.setdefault(int(source_id), []).append(f"{module.parent.name}.{module.name}")
    return used


def load_published(source):
    """Entries by package from index.json and index.min.json, highest code wins. None if unreachable."""
    entries = {}
    for name in INDEXES:
        try:
            if source.startswith("http"):
                with urllib.request.urlopen(f"{source.rstrip('/')}/{name}", timeout=20) as response:
                    data = json.loads(response.read().decode("utf-8-sig"))
            else:
                path = Path(source) / name
                if not path.exists():
                    continue
                data = json.loads(path.read_text(encoding="utf-8-sig"))
        except (urllib.error.URLError, OSError, ValueError) as e:
            print(f"warning: could not read {name} from {source}: {e}")
            return None
        for entry in data:
            known = entries.get(entry["pkg"])
            if known is None or int(entry["code"]) > int(known["code"]):
                entries[entry["pkg"]] = entry
    return entries


def check(root, name, published_source):
    lang, _, ext = name.partition(".")
    module = root / "extensions" / lang / ext
    if not lang or not ext or not module.is_dir():
        available = ", ".join(f"{m.parent.name}.{m.name}" for m in modules(root))
        sys.exit(f'No extension "{name}". Available: {available}')

    problems, notes = [], []
    code = module_code(module)
    found = {}
    literal_ids = sorted(set(SOURCE_ID.findall(code)))
    generated_ids = sorted(set(GENERATED_ID.findall(code)))
    if len(literal_ids) + len(generated_ids) != 1:
        problems.append(
            "expected exactly one source id declaration, sourceId = N (a released source) or "
            f"sourceId = BuildConfig.SOURCE_ID (a new one), found {literal_ids + generated_ids or 'none'}"
        )
    elif literal_ids:
        found["sourceId"] = literal_ids[0]
    else:
        built = built_ids(module)
        if built:
            found["sourceId"] = built[0]
            notes.append(f"generated source id (BuildConfig.{generated_ids[0]}), as last built")
        else:
            notes.append(f"generated source id (BuildConfig.{generated_ids[0]}): build the extension to check it")
    for key, pattern in (("name", SOURCE_NAME), ("DEFAULT_BASE_URL", BASE_URL)):
        values = sorted(set(pattern.findall(code)))
        if len(values) == 1:
            found[key] = values[0]
        else:
            problems.append(
                f"expected exactly one {key} declaration matching {pattern.pattern!r}, found {values or 'none'} "
                "(the release index entry is generated from it)"
            )

    if "sourceId" in found:
        others = [m for m in ids_in_use(root).get(int(found["sourceId"]), []) if m != name]
        if others:
            problems.append(f"source id {found['sourceId']} is also used by {', '.join(others)}")

    gradle_file = module / "build.gradle.kts"
    gradle = gradle_file.read_text(encoding="utf-8") if gradle_file.exists() else ""
    version_code = VERSION_CODE.search(gradle)
    suffix = PKG_SUFFIX.search(gradle)
    api = API_VERSION.search(gradle)
    if not version_code:
        problems.append('build.gradle.kts has no set("extVersionCode", N)')
    if not suffix:
        problems.append('build.gradle.kts has no set("pkgNameSuffix", "...")')
    elif suffix.group(1) != f"{lang}.{ext}":
        problems.append(
            f'pkgNameSuffix is "{suffix.group(1)}" but the folder is {lang}/{ext}: the release script and the app '
            "expect com.sf.tadami.extension.<lang>.<name>"
        )
    if not (module / "AndroidManifest.xml").exists():
        problems.append("AndroidManifest.xml is missing (common.gradle points the manifest to it)")
    if not (module / "res" / "mipmap-xhdpi" / "ic_launcher.png").exists():
        problems.append("res/mipmap-xhdpi/ic_launcher.png is missing (it's the icon the release publishes)")
    missing_densities = [d for d in ("mdpi", "hdpi", "xxhdpi", "xxxhdpi")
                         if not (module / "res" / f"mipmap-{d}" / "ic_launcher.png").exists()]
    if missing_densities:
        notes.append(f"no launcher icon for {', '.join(missing_densities)}")

    published = load_published(published_source)
    pkg = PACKAGE_PREFIX + (suffix.group(1) if suffix else f"{lang}.{ext}")
    if published is None:
        notes.append("published version unknown (repo unreachable), check the version code by hand")
        published_text = "unknown"
    elif pkg in published:
        entry = published[pkg]
        published_text = f"{entry['version']} (code {entry['code']})"
        if version_code and int(version_code.group(1)) <= int(entry["code"]):
            problems.append(
                f"extVersionCode {version_code.group(1)} is not above the published code {entry['code']}: "
                "the release would be refused and the app wouldn't offer it as an update"
            )
        published_ids = sorted({str(source.get("id")) for source in entry.get("sources", [])})
        if "sourceId" in found and published_ids and published_ids != [found["sourceId"]]:
            notes.append(
                f"the published index lists source id {', '.join(published_ids)} but the code says "
                f"{found['sourceId']}: the release republishes the code's id. Don't change sourceId to match "
                "the index, the app and the users' libraries use the code's id"
            )
    else:
        published_text = "not listed (new, or left out of the index on purpose)"

    print(f"{name}")
    print(f"  source: id {found.get('sourceId', '?')}, name {found.get('name', '?')!r}, "
          f"base URL {found.get('DEFAULT_BASE_URL', '?')}")
    print(f"  version: code {version_code.group(1) if version_code else '?'}, "
          f"lib {api.group(1) if api else 'default (gradle/build.versions.toml latestApiVersion)'}")
    print(f"  published: {published_text}")
    for note in notes:
        print(f"  note: {note}")
    for problem in problems:
        print(f"  PROBLEM: {problem}")
    print("  OK" if not problems else f"  {len(problems)} problem(s)")
    return not problems


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    sub = parser.add_subparsers(dest="command", required=True)
    ids_parser = sub.add_parser("ids", help="source ids in use")
    ids_parser.add_argument("--published", default=PUBLISHED_REPO,
                            help="published repo: local checkout dir or base URL (default: GitHub)")
    check_parser = sub.add_parser("check", help="pre-release checks for one or more extensions")
    check_parser.add_argument("extensions", nargs="+", help="<lang>.<name>")
    check_parser.add_argument("--published", default=PUBLISHED_REPO,
                              help="published repo: local checkout dir or base URL (default: GitHub)")
    args = parser.parse_args()
    root = repo_root()

    if args.command == "ids":
        used = ids_in_use(root)
        # A published extension whose folder was removed still owns its id on users' devices
        published = load_published(args.published)
        local_packages = {f"{PACKAGE_PREFIX}{m.parent.name}.{m.name}" for m in modules(root)}
        for pkg, entry in (published or {}).items():
            if pkg in local_packages:
                continue
            for source in entry.get("sources", []):
                if str(source.get("id", "")).isdigit():
                    used.setdefault(int(source["id"]), []).append(f"{pkg.removeprefix(PACKAGE_PREFIX)} (published only)")
        for source_id in sorted(used):
            flag = "  <- DUPLICATE" if len(used[source_id]) > 1 else ""
            print(f"{source_id:>5}  {', '.join(used[source_id])}{flag}")
        # The released sources keep their small literal ids; a new one never picks a number
        print("new source: sourceId = BuildConfig.SOURCE_ID, generated from tadami.repoId, the package and the class")
        return

    results = [check(root, name, args.published) for name in args.extensions]
    sys.exit(0 if all(results) else 1)


if __name__ == "__main__":
    main()
