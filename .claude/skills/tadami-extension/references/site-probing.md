# Probing a site

Look at what the site actually serves before writing or fixing code. Most "the extension is broken"
problems are visible in a single response: a moved domain, renamed markup, a new header requirement.

## Contents
1. curl with the app's User-Agent
2. Recognising Cloudflare and other blocks
3. A real browser for protected sites (`chrome_probe.mjs`)
4. Finding the data: JSON APIs vs HTML
5. Players: packed JS, obfuscated URLs
6. Survey the video hosts across anime of different eras
7. Video hosts: referer and HTTP version
8. Verifying an HLS stream
9. Rate limits
10. The extension's icon

## 1. curl with the app's User-Agent

Send what the app sends, a recent Chrome for Android UA; the app adapts the version to the device's
WebView:

```bash
UA='Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/141.0.0.0 Mobile Safari/537.36'
curl -sL -A "$UA" -D - -o page.html "https://site.example/" | grep -iE '^(HTTP|location|server|cf-mitigated|content-type)'
```

Follow redirects (`-L`) and note the final host: sites move domains often, and the challenge and its
cookie belong to the final host.

## 2. Recognising Cloudflare and other blocks

| Response | Meaning | What to do |
|---|---|---|
| 403/503 + `cf-mitigated: challenge`, title "Just a moment..." | Cloudflare challenge | Nothing in code: `network.client` solves it in the app. Use a real browser to probe (section 3). |
| 403 "Request forbidden by administrative rules" | Firewall rule on that path | No browser passes it. Find another endpoint (AnimePahe: `/a/<id>` is blocked, the search API isn't). |
| 403 "Attention Required! \| Cloudflare" | Firewall/bot block, often a missing header or HTTP/1.1 | Check the referer and HTTP version (section 7). |
| 429 "Access denied ... used Cloudflare to restrict access" | Rate limit | Slow down; cache in the extension (section 9). |
| "Connection refused" or a timeout to 127.0.0.1 / 0.0.0.0 | DNS block: the network's resolver hides the host | Check it as below, then look for a mirror domain (below). |

The app's cookie can't be reused with curl: Cloudflare binds it to the client fingerprint, so curl gets
challenged even with it. Use the browser instead.

**DNS blocks.** Some ISPs resolve streaming hosts to 127.0.0.1 (seen with voe.sx, doods.pro, dsvplay,
vidmoly.biz and some VOE mirrors). Confirm and work around it:

```bash
nslookup voe.sx                    # 127.0.0.1 or 0.0.0.0: blocked here
nslookup voe.sx 1.1.1.1            # the real address
curl -s --doh-url https://cloudflare-dns.com/dns-query -A "$UA" -o /dev/null -w '%{http_code}\n' https://voe.sx/e/<code>
adb shell ping -c 1 voe.sx         # the device sees the same block
```

Users behind such a resolver lose the host too, so prefer a mirror over a DNS workaround: many hosts
serve the same codes on several domains. VOE codes play on any VOE domain (voe.sx and the random-name
mirrors the site links), vidmoly.biz codes play on vidmoly.org. Try the code on each domain the site
uses, and fall back to them in the extension (IAnimes' VOE fallback). `scripts/host_survey.py` marks
hosts that this machine's DNS blocks.

## 3. A real browser for protected sites

`scripts/chrome_probe.mjs` starts Chrome with a throwaway profile (never the user's) and DevTools access.
Requests made from inside the page carry a real browser's fingerprint and cookies.

```bash
S=.claude/skills/tadami-extension/scripts
node $S/chrome_probe.mjs open https://site.example/        # waits until past any challenge
node $S/chrome_probe.mjs eval "document.title"
# Fetch an API from the page origin, return a compact summary
node $S/chrome_probe.mjs eval "(async () => { const r = await fetch('/api?m=airing&page=1'); return { status: r.status, sample: (await r.text()).slice(0, 300) }; })()"
# Test selectors against fetched HTML, exactly like Jsoup would see it (no JS executed)
node $S/chrome_probe.mjs eval "(async () => { const d = new DOMParser().parseFromString(await (await fetch('/anime/some-slug')).text(), 'text/html'); return [...d.querySelectorAll('div.episode a')].slice(0, 5).map(a => a.getAttribute('href')); })()"
node $S/chrome_probe.mjs close
```

Use `eval @script.js` for long scripts, and `--match <url part>` when several tabs are open. If `open`
reports it's still on a challenge, solve it in the Chrome window, then continue.

Two tricks that save time:

```bash
# Where does a JavaScript loader send the browser? Record and cancel the navigation, then run the loader
node $S/chrome_probe.mjs eval "(async () => { const seen = []; navigation.addEventListener('navigate', e => { seen.push(e.destination.url); e.preventDefault(); }); /* paste or eval the loader's code here */ await new Promise(r => setTimeout(r, 3000)); return seen; })()"
# A "click to play" form or a search that POSTs: send the same request from the page
node $S/chrome_probe.mjs eval "(async () => { const r = await fetch('/play.php', { method: 'POST', headers: { 'Content-Type': 'application/x-www-form-urlencoded' }, body: new URLSearchParams({ id: '123' }) }); return (await r.text()).slice(0, 500); })()"
```

A form that POSTs often answers a GET with the same parameters too (IAnimes' search does): try it, since
a GET is simpler in the extension. Otherwise use `POST(url, headers, FormBody.Builder().add(...).build())`
from `com.sf.tadami.network`.

Parse fetched HTML with `DOMParser` rather than querying the live `document` when checking selectors:
the extension sees the raw HTML, not what the site's JavaScript builds afterwards. If the data only
appears after JS runs, look for the API the JS calls (section 4).

## 4. Finding the data: JSON APIs vs HTML

Prefer a JSON API when the site has one. It changes less often than markup, and kotlinx.serialization
DTOs with `ignoreUnknownKeys` survive extra fields. To find it, open the page in the probe browser
and list what it requested:

```bash
node $S/chrome_probe.mjs eval "performance.getEntriesByType('resource').map(e => e.name).filter(u => /api|json|ajax/i.test(u)).slice(0, 30)"
```

Check pagination fields (`current_page`/`last_page`, `hasNextPage`), how an anime is identified (id vs a
session/slug that may rotate: AnimePahe sessions change, so store the stable id and resolve the
session at request time), and whether search needs exact titles.

## 5. Players: packed JS, obfuscated URLs

- **Packed JS** (`eval(function(p,a,c,k,e,d)...`) is Dean Edwards' packer. Unpack it with `lib/unpacker`
  (or `dev.datlag.jsunpacker`, as AnimePahe does), then read the source URL from the result. To check by
  hand, evaluate the call without the outer `eval(` in Node and inspect the string.
- **Obfuscated player URLs** in an API (base64, hex, XOR, reversed strings) are almost always reversible.
  Decode one sample by hand, write the decoder in Kotlin, and validate the result (`startsWith("http")`,
  printable). FrAnime's `decodePlayerUrl` is a complete example. A WebView is the last resort.
- Note which host each player URL points to and whether `lib/` has an extractor for it.

## 6. Survey the video hosts

Sites change players over the years, so the hosts on this season's episodes are rarely all of them: a
2010 show often still streams from hosts the site stopped using for new uploads. Survey a spread of
anime before settling the video-sources code, and again when fixing playback.

For a plain HTML site, `scripts/host_survey.py` does the fetching and counting. Give it anime pages and
a regex matching their episode links; it surveys the first, middle and last episode of each:

```bash
python $S/host_survey.py --follow '-ep\d+.*\.htm$' \
  'https://site.example/liste.php?manga=death-note-vostfr' \
  'https://site.example/liste.php?manga=one-piece-vostfr' \
  'https://site.example/liste.php?manga=airing-show-vf'
python $S/host_survey.py --file episode_urls.txt      # or episode pages directly
```

It prints each page's players, then a table: host, how many pages had it, the `lib/` extractor its name
suggests, and whether this machine's DNS blocks it. It sees only the raw HTML; for players loaded by
JavaScript or pages behind a challenge, use the browser script at the end of this section. The steps:

1. **Pick the sample** from the site's search or its catalogue sorted by year: a currently airing
   anime, one from 2 to 5 years ago, an old one (10+ years), a movie, and a dub if the site separates
   them (VF/VOSTFR, sub/dub).
2. **List the players** of one or two episodes of each: iframe/embed hosts, server tabs and their
   labels, and the player URLs in data attributes or API fields (decoded if obfuscated, section 5).
   Players that a tab loads through JavaScript aren't in the raw HTML: find that request (section 4).
3. **Map each host to an extractor**: `ls lib`, and think in host families, since many hosts are
   mirrors under new domains (vidhide, streamwish and filemoon clones). VOE also links random-name
   mirrors (`jessicayeahcatch.com/e/<code>`, `rebeccapracticeloss.com/e/<code>`, ...), some of them
   broken on their side; route any unknown `/e/<code>` domain to the VOE extractor when the site uses
   VOE. Follow at least one URL per host down to a playable stream (sections 7 and 8).
4. **Support every host an extractor covers.** For the others, say so in the report; write a new
   extractor only for a host that comes up often in the survey.
5. **Report the survey**: host, the anime it was seen on, and the extractor or "unsupported".

Collecting the hosts of several episode pages in one go (fill in real paths from the site):

```bash
cat > hosts.js <<'EOF'
(async () => {
  const pages = ['/anime/airing-show/episode-1', '/anime/show-2019/episode-1', '/anime/show-2008/episode-1', '/film/some-movie'];
  const out = {};
  for (const p of pages) {
    const d = new DOMParser().parseFromString(await (await fetch(p)).text(), 'text/html');
    const urls = [...d.querySelectorAll('iframe[src], [data-src], [data-url], [data-embed], a[href*="embed"]')]
      .map(e => e.getAttribute('src') || e.getAttribute('data-src') || e.getAttribute('data-url') || e.getAttribute('data-embed') || e.getAttribute('href'));
    out[p] = [...new Set(urls.filter(Boolean).map(u => { try { return new URL(u, location.href).host; } catch { return u; } }))];
  }
  return out;
})()
EOF
node $S/chrome_probe.mjs eval @hosts.js
```

## 7. Video hosts: referer and HTTP version

Hosts check headers more strictly than the site itself. Test a matrix before concluding anything:

```bash
URL='https://host.example/e/abc123'
for ref in "https://site.example/" "https://site.example" ""; do
  for h in --http1.1 --http2; do
    printf "%-28s %-9s " "'$ref'" "$h"
    curl $h -s -A "$UA" ${ref:+-e "$ref"} -o /dev/null -w '%{http_code}\n' "$URL"
  done
done
```

Real findings: Kwik's embed page needs the referer `https://animepahe.pw/` with the trailing slash,
its CDN needs `https://kwik.cx/`, and both return 403 to any HTTP/1.1 request. The extension's OkHttp
client and the app's player both speak HTTP/2; curl and Android's `HttpURLConnection` default to
HTTP/1.1, which is a classic "works in the browser, 403 in my test" trap.

## 8. Verifying an HLS stream

When playback fails but extraction works, fetch the chain the player fetches, with the headers from
`StreamSource.headers`:

```bash
H=(-A "$UA" -e "https://host.example/" --http2)
curl -s "${H[@]}" "$M3U8" -o pl.m3u8 && head -12 pl.m3u8          # playlist parses?
KEY=$(grep -o 'URI="[^"]*"' pl.m3u8 | head -1 | sed 's/URI="//; s/"$//')
curl -s "${H[@]}" "$KEY" -o seg.key && wc -c seg.key                # 16 bytes for AES-128
SEG=$(grep -v '^#' pl.m3u8 | head -1)
curl -s "${H[@]}" "$SEG" -o seg1.bin
openssl aes-128-cbc -d -nopad -K "$(xxd -p seg.key)" -iv "$(printf '%032x' 1)" -in seg1.bin -out seg1.ts
xxd -l 4 seg1.ts                                                    # 47 = MPEG-TS sync byte
```

Segments disguised as images (`.jpg`, `image/jpeg`) are normal for these hosts and fine for the player.
A playlist that skips segment numbers, with AES and no explicit IV, is handled by the app.

## 9. Rate limits

Find the limit before relying on an endpoint in a per-episode path. Space requests in the probe and
watch for 429:

```bash
node $S/chrome_probe.mjs eval "(async () => { const out = []; for (let i = 0; i < 12; i++) { out.push((await fetch('/api?m=search&q=naruto')).status); await new Promise(r => setTimeout(r, 300)); } return out.join(' '); })()"
```

If it trips, cache results in the extension (a `ConcurrentHashMap` with a time limit), take data from
list responses you already have, and throw a readable "wait a minute and try again" message on 429.

## 10. The extension's icon

Use the site's own square icon. Look in the home page's `<head>` and try each candidate, since declared
icons sometimes 404:

```bash
curl -s -A "$UA" https://site.example/ | grep -oiE '<link[^>]+rel="(apple-touch-icon|icon|manifest)[^"]*"[^>]*>'
for path in /apple-touch-icon.png /apple-touch-icon-precomposed.png /favicon.ico; do
  printf '%-36s ' "$path"; curl -s -A "$UA" -o /dev/null -w '%{http_code} %{content_type} %{size_download}\n' "https://site.example$path"
done
```

A web manifest (`manifest.json`, `site.webmanifest`) lists more icons, often up to 512 px. Take the
largest square PNG, then resize it to 48, 72, 96, 144 and 192 px for `mipmap-mdpi` to `mipmap-xxxhdpi`.
Windows has neither PIL nor ImageMagick by default; PowerShell's System.Drawing does it:

```powershell
Add-Type -AssemblyName System.Drawing
$source = [System.Drawing.Image]::FromFile("C:\path\to\apple-touch-icon.png")
$sizes = [ordered]@{ mdpi = 48; hdpi = 72; xhdpi = 96; xxhdpi = 144; xxxhdpi = 192 }
foreach ($density in $sizes.Keys) {
    $size = $sizes[$density]
    $bitmap = New-Object System.Drawing.Bitmap $size, $size
    $graphics = [System.Drawing.Graphics]::FromImage($bitmap)
    $graphics.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
    $graphics.DrawImage($source, 0, 0, $size, $size)
    $dir = "extensions\<lang>\<name>\res\mipmap-$density"
    New-Item -ItemType Directory -Force $dir | Out-Null
    $bitmap.Save("$dir\ic_launcher.png", [System.Drawing.Imaging.ImageFormat]::Png)
    $graphics.Dispose(); $bitmap.Dispose()
}
$source.Dispose()
```

A wide wordmark or a logo with text makes a poor launcher icon: prefer the square one even if it's
smaller, and mention the alternative in the report. Without any usable icon, ask the user.
