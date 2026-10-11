#!/usr/bin/env python3
"""
Lists the video hosts embedded in a site's episode pages, to see which extractors an extension needs.

    host_survey.py <episode page URL>...
        Surveys these episode pages.

    host_survey.py --follow '<regex>' <anime page URL>...
        Each URL is an anime page: the links whose absolute URL matches the regex are its episodes,
        and the first, middle and last ones are surveyed (--pick first,last to change that).

    Options: --file urls.txt (one URL per line), --referer URL, --delay 0.5 (seconds between requests)

For each page it prints the player URLs it found (iframes, data-src/data-url/... attributes, server
<option>s, embed links, and embed-looking URLs in inline scripts), then a table of hosts with how often
they appeared, a guess at the lib/ extractor, and whether this machine's DNS blocks the host.

It fetches raw HTML like the extension does, with the app's mobile User-Agent and no JavaScript. Players
loaded by JavaScript or behind a Cloudflare challenge don't show up: probe those pages with
chrome_probe.mjs (site-probing.md, "Survey the video hosts").
"""
import argparse
import re
import socket
import sys
import time
import urllib.error
import urllib.request
from collections import Counter
from html.parser import HTMLParser
from pathlib import Path
from urllib.parse import urljoin, urlparse

UA = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/141.0.0.0 Mobile Safari/537.36"
EMBED_ATTRIBUTES = ("data-src", "data-url", "data-embed", "data-link", "data-video", "data-player", "data-iframe")
EMBED_PATH = re.compile(r"/(embed|e|v|d|player|video)[/-]|embed|player", re.IGNORECASE)
SCRIPT_URL = re.compile(r"https?://[^\s\"'<>\\]+")


class PageParser(HTMLParser):
    def __init__(self):
        super().__init__(convert_charrefs=True)
        self.links = []
        self.players = []
        self.in_script = False

    def handle_starttag(self, tag, attrs):
        attrs = dict(attrs)
        if tag == "script":
            self.in_script = True
        if tag == "a" and attrs.get("href"):
            self.links.append(attrs["href"])
            if EMBED_PATH.search(attrs["href"]):
                self.players.append(attrs["href"])
        if tag in ("iframe", "embed", "frame") and attrs.get("src"):
            self.players.append(attrs["src"])
        if tag == "option" and (attrs.get("value") or "").startswith("http"):
            self.players.append(attrs["value"])
        self.players.extend(attrs[name] for name in EMBED_ATTRIBUTES if attrs.get(name))

    def handle_endtag(self, tag):
        if tag == "script":
            self.in_script = False

    def handle_data(self, data):
        if self.in_script:
            self.players.extend(u for u in SCRIPT_URL.findall(data) if EMBED_PATH.search(urlparse(u).path))


def fetch(url, referer):
    headers = {"User-Agent": UA, "Accept": "text/html,*/*"}
    if referer:
        headers["Referer"] = referer
    request = urllib.request.Request(url, headers=headers)
    try:
        with urllib.request.urlopen(request, timeout=25) as response:
            return response.geturl(), response.read().decode("utf-8", errors="replace"), None
    except urllib.error.HTTPError as e:
        body = e.read().decode("utf-8", errors="replace")
        if e.headers.get("cf-mitigated") == "challenge" or "Just a moment" in body:
            return url, None, "Cloudflare challenge: probe it with chrome_probe.mjs"
        return url, None, f"HTTP {e.code}"
    except (urllib.error.URLError, OSError) as e:
        return url, None, f"unreachable: {getattr(e, 'reason', e)}"


def parse(html):
    parser = PageParser()
    parser.feed(html)
    return parser


def extractor_names():
    lib = Path(__file__).resolve().parents[4] / "lib"
    if not lib.is_dir():
        return []
    return sorted(d.name.removesuffix("-extractor") for d in lib.iterdir() if d.name.endswith("-extractor"))


def guess_extractor(host, names):
    flat = host.replace(".", "").replace("-", "")
    matches = [n for n in names if n.replace("-", "") in flat]
    return ", ".join(matches) if matches else "-"


def dns_status(host, cache={}):
    if host not in cache:
        try:
            address = socket.gethostbyname(host)
            blocked = address.startswith("127.") or address == "0.0.0.0"
            cache[host] = f"BLOCKED here ({address})" if blocked else "ok"
        except OSError:
            cache[host] = "no DNS answer"
    return cache[host]


def pick(items, which):
    if not items:
        return []
    positions = {"first": 0, "middle": len(items) // 2, "last": len(items) - 1}
    return [items[i] for i in sorted({positions[w] for w in which})]


def main():
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("urls", nargs="*")
    parser.add_argument("--file", help="file with one URL per line")
    parser.add_argument("--follow", help="regex for episode links: the URLs given are anime pages")
    parser.add_argument("--pick", default="first,middle,last", help="episodes to survey with --follow")
    parser.add_argument("--referer", help="Referer header to send (default: none)")
    parser.add_argument("--delay", type=float, default=0.5, help="seconds between requests")
    args = parser.parse_args()

    urls = list(args.urls)
    if args.file:
        urls += [line.strip() for line in Path(args.file).read_text(encoding="utf-8").splitlines() if line.strip()]
    if not urls:
        parser.error("give episode page URLs, or anime page URLs with --follow")
    which = [w.strip() for w in args.pick.split(",") if w.strip()]
    if any(w not in ("first", "middle", "last") for w in which):
        parser.error("--pick takes first, middle and/or last")

    episode_pages = []
    if args.follow:
        regex = re.compile(args.follow)
        for url in urls:
            final_url, html, problem = fetch(url, args.referer)
            time.sleep(args.delay)
            if problem:
                print(f"{url}\n  {problem}")
                continue
            links = list(dict.fromkeys(urljoin(final_url, href) for href in parse(html).links))
            episodes = [link for link in links if regex.search(link)]
            print(f"{url}: {len(episodes)} episode links, surveying {', '.join(which)}")
            episode_pages += pick(episodes, which)
    else:
        episode_pages = urls

    names = extractor_names()
    hosts = Counter()
    example = {}
    for url in episode_pages:
        final_url, html, problem = fetch(url, args.referer)
        time.sleep(args.delay)
        print(f"\n{url}")
        if problem:
            print(f"  {problem}")
            continue
        page_host = urlparse(final_url).hostname
        players = []
        for raw in parse(html).players:
            absolute = urljoin(final_url, raw.strip())
            if absolute.startswith("http") and absolute not in players:
                players.append(absolute)
        if not players:
            print("  no players in the raw HTML (loaded by JavaScript? see chrome_probe.mjs)")
        page_hosts = set()
        for player in players:
            host = urlparse(player).hostname or "?"
            same_site = "  (same site: open it to find the real host)" if host == page_host else ""
            print(f"  {host:32} {player[:110]}{same_site}")
            # Counted once per page
            if host != page_host and host not in page_hosts:
                page_hosts.add(host)
                hosts[host] += 1
                example.setdefault(host, player)

    if not hosts:
        return
    print(f"\nHosts across {len(episode_pages)} pages")
    print(f"  {'pages':>5}  {'host':32} {'lib/ extractor guess':22} {'DNS':26} example")
    for host, count in hosts.most_common():
        print(f"  {count:>5}  {host:32} {guess_extractor(host, names):22} {dns_status(host):26} {example[host][:80]}")
    print("\nThe guess matches lib/ folder names against the host; mirrors under other names (VOE's random "
          "domains, streamwish/filemoon/vidhide clones) need opening to identify.")


if __name__ == "__main__":
    main()
