#!/usr/bin/env node
// Drives a real Chrome over the DevTools protocol to look at sites that block curl (Cloudflare,
// bot checks): requests made from the page carry a real browser's fingerprint and cookies.
// Node 18+ (22+ has a global WebSocket; older versions need --experimental-websocket). No dependencies.
//
//   node chrome_probe.mjs open <url> [--port 9333] [--profile <dir>] [--timeout 90]
//       Starts Chrome with a throwaway profile (not the user's) and waits until the page is past any
//       Cloudflare challenge. Reuses the instance already listening on that port.
//   node chrome_probe.mjs eval "<js expression>" | @file.js [--match <url substring>] [--port 9333]
//       Evaluates in a page tab (promises awaited). Strings print raw, anything else as JSON.
//       e.g. eval "(async () => (await fetch('/api?page=1')).status)()"
//   node chrome_probe.mjs close [--port 9333]
//       Closes that Chrome instance (only the one started with this port).
import { spawn } from "node:child_process";
import { existsSync, mkdirSync, readFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";

const args = process.argv.slice(2);
const command = args[0];
const option = (name, fallback) => {
  const i = args.indexOf(`--${name}`);
  return i >= 0 && i + 1 < args.length ? args[i + 1] : fallback;
};
const positional = args.filter((a, i) => i > 0 && !a.startsWith("--") && !args[i - 1]?.startsWith("--"));
const port = Number(option("port", "9333"));
const base = `http://127.0.0.1:${port}`;
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

function findChrome() {
  const candidates = {
    win32: [
      `${process.env["ProgramFiles"]}\\Google\\Chrome\\Application\\chrome.exe`,
      `${process.env["ProgramFiles(x86)"]}\\Google\\Chrome\\Application\\chrome.exe`,
      `${process.env.LOCALAPPDATA}\\Google\\Chrome\\Application\\chrome.exe`,
      `${process.env["ProgramFiles"]}\\Microsoft\\Edge\\Application\\msedge.exe`,
    ],
    darwin: [
      "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome",
      "/Applications/Chromium.app/Contents/MacOS/Chromium",
    ],
    linux: ["/usr/bin/google-chrome", "/usr/bin/google-chrome-stable", "/usr/bin/chromium", "/usr/bin/chromium-browser"],
  }[process.platform] ?? [];
  const found = [process.env.CHROME_PATH, ...candidates].find((p) => p && existsSync(p));
  if (!found) throw new Error("Chrome not found, set CHROME_PATH");
  return found;
}

async function targets() {
  const response = await fetch(`${base}/json`);
  return response.json();
}

async function running() {
  try {
    await targets();
    return true;
  } catch {
    return false;
  }
}

async function cdp(wsUrl, method, params = {}) {
  const ws = new WebSocket(wsUrl);
  await new Promise((resolve, reject) => {
    ws.onopen = resolve;
    ws.onerror = () => reject(new Error(`Could not connect to ${wsUrl}`));
  });
  const result = await new Promise((resolve) => {
    ws.onmessage = (event) => {
      const message = JSON.parse(event.data);
      if (message.id === 1) resolve(message);
    };
    ws.send(JSON.stringify({ id: 1, method, params }));
  });
  ws.close();
  return result;
}

async function page(match) {
  const pages = (await targets()).filter((t) => t.type === "page");
  const target = (match && pages.find((t) => t.url.includes(match))) || pages[0];
  if (!target) throw new Error("No page tab open, run `open <url>` first");
  return target;
}

async function evaluate(expression, match) {
  const target = await page(match);
  const response = await cdp(target.webSocketDebuggerUrl, "Runtime.evaluate", {
    expression,
    awaitPromise: true,
    returnByValue: true,
  });
  const result = response.result;
  if (result?.exceptionDetails) {
    const description = result.exceptionDetails.exception?.description ?? result.exceptionDetails.text;
    throw new Error(`Page threw: ${description}`);
  }
  return result?.result?.value;
}

// Past the challenge once the challenge page markers are gone. Not window._cf_chl_opt: after a
// challenge, Cloudflare's precursor script keeps it defined on the site's real pages.
const CHALLENGE_STATE = `(() => ({
  url: location.href,
  title: document.title,
  challenge: document.getElementById('challenge-error-text') !== null ||
    /just a moment|un instant|attention required|checking your browser/i.test(document.title),
}))()`;

async function open(url) {
  if (!(await running())) {
    const profile = option("profile", join(tmpdir(), `chrome-probe-${port}`));
    mkdirSync(profile, { recursive: true });
    const chrome = spawn(
      findChrome(),
      [
        `--user-data-dir=${profile}`,
        `--remote-debugging-port=${port}`,
        "--no-first-run",
        "--no-default-browser-check",
        "--window-size=1000,800",
        url,
      ],
      { detached: true, stdio: "ignore" },
    );
    chrome.unref();
    for (let i = 0; i < 40 && !(await running()); i++) await sleep(500);
  } else {
    const target = await page();
    await cdp(target.webSocketDebuggerUrl, "Page.navigate", { url });
  }

  const timeout = Number(option("timeout", "90")) * 1000;
  const start = Date.now();
  let state;
  while (Date.now() - start < timeout) {
    await sleep(2000);
    try {
      state = await evaluate(CHALLENGE_STATE);
    } catch {
      continue; // navigating
    }
    if (state && !state.challenge && state.url !== "about:blank") {
      console.log(`ready: ${state.title} | ${state.url}`);
      return;
    }
  }
  console.log(`still on a challenge after ${timeout / 1000}s: ${state?.title} | ${state?.url}`);
  console.log("Solve it in the Chrome window (or wait), then run eval.");
  process.exitCode = 2;
}

async function main() {
  switch (command) {
    case "open": {
      if (!positional[0]) throw new Error("usage: open <url>");
      await open(positional[0]);
      break;
    }
    case "eval": {
      const raw = positional[0];
      if (!raw) throw new Error('usage: eval "<js>" | @file.js');
      const expression = raw.startsWith("@") ? readFileSync(raw.slice(1), "utf8") : raw;
      const value = await evaluate(expression, option("match"));
      console.log(typeof value === "string" ? value : JSON.stringify(value, null, 1));
      break;
    }
    case "close": {
      const version = await (await fetch(`${base}/json/version`)).json();
      await cdp(version.webSocketDebuggerUrl, "Browser.close");
      console.log("closed");
      break;
    }
    default:
      console.log(readFileSync(new URL(import.meta.url), "utf8").split("\n").filter((l) => l.startsWith("//")).join("\n"));
      process.exitCode = command ? 1 : 0;
  }
}

main().catch((error) => {
  console.error(error.message);
  process.exit(1);
});
