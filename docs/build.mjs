import { readFileSync, writeFileSync, readdirSync, mkdirSync, existsSync } from "node:fs";
import { join, dirname, basename } from "node:path";
import { fileURLToPath, pathToFileURL } from "node:url";
import { Marked } from "marked";

const root = dirname(fileURLToPath(import.meta.url));
const srcDir = join(root, "src");
const htmlDir = join(root, "html");
const pdfDir = join(root, "pdf");
const htmlOnly = process.argv.includes("--html-only");
const only = process.argv.find((a) => a.startsWith("--only="))?.slice(7);

const AUTHORS = ["Varshit Tyagi", "Mohini Teotia"];
const PRODUCT_VERSION = "1.1.0";
const ISSUED = "November 2026";
const logo = readFileSync(join(root, "theme", "logo.svg"), "utf8").replace(/<\?xml[^>]*>/, "");
const css = readFileSync(join(root, "theme", "theme.css"), "utf8");

const escapeHtml = (s) =>
  String(s).replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;").replace(/"/g, "&quot;");

const slugify = (text, used) => {
  let base = text
    .toLowerCase()
    .replace(/<[^>]+>/g, "")
    .replace(/&[a-z]+;/g, "")
    .replace(/[^a-z0-9\s-]/g, "")
    .trim()
    .replace(/\s+/g, "-") || "section";
  let slug = base;
  let i = 2;
  while (used.has(slug)) slug = `${base}-${i++}`;
  used.add(slug);
  return slug;
};

function parseFrontMatter(raw) {
  const match = raw.match(/^---\n([\s\S]*?)\n---\n/);
  if (!match) return { meta: {}, body: raw };
  const meta = {};
  for (const line of match[1].split("\n")) {
    const idx = line.indexOf(":");
    if (idx > 0) meta[line.slice(0, idx).trim()] = line.slice(idx + 1).trim();
  }
  return { meta, body: raw.slice(match[0].length) };
}

const CALLOUTS = {
  NOTE: { label: "Note", cls: "note" },
  TIP: { label: "Tip", cls: "tip" },
  IMPORTANT: { label: "Important", cls: "important" },
  WARNING: { label: "Warning", cls: "warning" },
  CAUTION: { label: "Caution", cls: "caution" },
};

function render(markdown, prefix) {
  const used = new Set();
  const toc = [];
  const marked = new Marked({ gfm: true });
  marked.use({
    renderer: {
      heading({ tokens, depth }) {
        const inner = this.parser.parseInline(tokens);
        const id = `${prefix}-${slugify(inner, used)}`;
        if (depth === 2 || depth === 3) toc.push({ depth, id, text: inner.replace(/<[^>]+>/g, "") });
        return `<h${depth} id="${id}">${inner}</h${depth}>\n`;
      },
      code({ text, lang }) {
        if (lang === "mermaid") return `<div class="diagram"><pre class="mermaid">${escapeHtml(text)}</pre></div>\n`;
        const label = lang ? `<span class="code-lang">${escapeHtml(lang)}</span>` : "";
        return `<div class="code">${label}<pre><code>${escapeHtml(text)}</code></pre></div>\n`;
      },
      table(token) {
        const head = token.header.map((c) => `<th${c.align ? ` style="text-align:${c.align}"` : ""}>${this.parser.parseInline(c.tokens)}</th>`).join("");
        const rows = token.rows
          .map((r) => `<tr>${r.map((c) => `<td${c.align ? ` style="text-align:${c.align}"` : ""}>${this.parser.parseInline(c.tokens)}</td>`).join("")}</tr>`)
          .join("");
        return `<div class="table-wrap"><table><thead><tr>${head}</tr></thead><tbody>${rows}</tbody></table></div>\n`;
      },
      blockquote({ tokens }) {
        const body = this.parser.parse(tokens);
        const m = body.match(/^<p>\[!(NOTE|TIP|IMPORTANT|WARNING|CAUTION)\]\s*/);
        if (m) {
          const c = CALLOUTS[m[1]];
          return `<div class="callout ${c.cls}"><div class="callout-title">${c.label}</div>${body.replace(m[0], "<p>")}</div>\n`;
        }
        return `<blockquote>${body}</blockquote>\n`;
      },
    },
  });
  const html = marked.parse(markdown);
  return { html, toc };
}

function tocHtml(toc, title = "Contents") {
  let n2 = 0;
  let n3 = 0;
  const items = toc
    .map((t) => {
      if (t.depth === 2) {
        n2++;
        n3 = 0;
        return `<li class="toc-2"><a href="#${t.id}"><span class="toc-num">${n2}</span>${t.text}</a></li>`;
      }
      n3++;
      return `<li class="toc-3"><a href="#${t.id}"><span class="toc-num">${n2}.${n3}</span>${t.text}</a></li>`;
    })
    .join("");
  return `<section class="toc page-break"><h1 class="toc-title">${title}</h1><ol>${items}</ol></section>`;
}

function cover(meta, extra = "") {
  return `<section class="cover">
  <div class="cover-top">
    <div class="cover-logo">${logo}</div>
    <div class="cover-brand"><span>ProctorAI</span><small>AI-assisted complaint, identity &amp; access platform</small></div>
  </div>
  <div class="cover-main">
    <div class="cover-kicker">${escapeHtml(meta.category || "Documentation")}</div>
    <h1 class="cover-title">${escapeHtml(meta.title)}</h1>
    <p class="cover-subtitle">${escapeHtml(meta.subtitle || "")}</p>
    ${extra}
  </div>
  <div class="cover-meta">
    <div><span>Document ID</span><b>${escapeHtml(meta.id || "PAI-DOC")}</b></div>
    <div><span>Product version</span><b>${PRODUCT_VERSION}</b></div>
    <div><span>Document version</span><b>${escapeHtml(meta.version || "1.0")}</b></div>
    <div><span>Issued</span><b>${ISSUED}</b></div>
    <div><span>Classification</span><b>${escapeHtml(meta.classification || "Internal")}</b></div>
  </div>
  <div class="cover-authors">
    <span>Creator &amp; Developer</span>
    <b>${AUTHORS.join(" &amp; ")}</b>
  </div>
</section>`;
}

function control(meta) {
  return `<section class="control page-break">
  <h1 class="control-title">Document control</h1>
  <div class="table-wrap"><table class="kv"><tbody>
    <tr><th>Title</th><td>${escapeHtml(meta.title)}</td></tr>
    <tr><th>Document ID</th><td>${escapeHtml(meta.id)}</td></tr>
    <tr><th>Audience</th><td>${escapeHtml(meta.audience || "Engineering and operations")}</td></tr>
    <tr><th>Product</th><td>ProctorAI ${PRODUCT_VERSION}</td></tr>
    <tr><th>Status</th><td>${escapeHtml(meta.status || "Approved for use")}</td></tr>
    <tr><th>Classification</th><td>${escapeHtml(meta.classification || "Internal")}</td></tr>
  </tbody></table></div>
  <h2 class="plain">Creator &amp; Developer</h2>
  <div class="table-wrap"><table><thead><tr><th>Name</th><th>Role</th></tr></thead><tbody>
    ${AUTHORS.map((a) => `<tr><td>${a}</td><td>Creator &amp; Developer</td></tr>`).join("")}
  </tbody></table></div>
  <h2 class="plain">Revision history</h2>
  <div class="table-wrap"><table><thead><tr><th>Version</th><th>Date</th><th>Author</th><th>Change</th></tr></thead><tbody>
    <tr><td>${escapeHtml(meta.version || "1.0")}</td><td>${ISSUED}</td><td>${AUTHORS.join(", ")}</td><td>Initial release for ProctorAI ${PRODUCT_VERSION}</td></tr>
  </tbody></table></div>
  <h2 class="plain">Related documents</h2>
  <p class="related">${escapeHtml(meta.related || "See the ProctorAI documentation index (docs/README.md).")}</p>
  <p class="legal">© 2026 ${AUTHORS.join(" &amp; ")}. ProctorAI is released under the MIT License. Product names mentioned are trademarks of their respective owners.</p>
</section>`;
}

function page(title, footer, body) {
  return `<!doctype html>
<html lang="en"><head><meta charset="utf-8"><title>${escapeHtml(title)}</title>
<link rel="preconnect" href="https://fonts.googleapis.com">
<link href="https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700;800&family=JetBrains+Mono:wght@400;600&display=swap" rel="stylesheet">
<style>${css}
@page { @bottom-left { content: "${footer.replace(/"/g, "'")}"; } }
</style>
<script src="https://cdn.jsdelivr.net/npm/mermaid@11.4.1/dist/mermaid.min.js"></script>
</head><body>
${body}
<script>
window.__ready = (async () => {
  await document.fonts.ready;
  await Promise.all(["400 13px Inter", "600 13px Inter", "400 13px 'JetBrains Mono'"].map((f) => document.fonts.load(f)));
  try {
    if (window.mermaid) {
      mermaid.initialize({ startOnLoad: false, securityLevel: "loose", theme: "base", fontFamily: "Inter, sans-serif",
        flowchart: { htmlLabels: true, curve: "basis", padding: 12 },
        sequence: { useMaxWidth: true, mirrorActors: false },
        er: { useMaxWidth: true },
        themeVariables: { primaryColor: "#ffffff", primaryBorderColor: "#4f46e5", primaryTextColor: "#111827",
          lineColor: "#6b7280", secondaryColor: "#f6f7f9", tertiaryColor: "#f6f7f9", fontSize: "13px",
          actorBkg: "#ffffff", actorBorder: "#4f46e5", signalColor: "#374151", noteBkgColor: "#f6f7f9", noteBorderColor: "#9ca3af",
          clusterBkg: "#f9fafb", clusterBorder: "#d1d5db", edgeLabelBackground: "#ffffff" } });
      await mermaid.run({ querySelector: ".mermaid" });
    }
  } catch (e) { console.error("mermaid: " + String((e && (e.message || e.str)) || e)); }
  await document.fonts.ready;
  return true;
})();
</script>
</body></html>`;
}

const files = readdirSync(srcDir).filter((f) => f.endsWith(".md")).sort();
const docs = files.map((file, index) => {
  const { meta, body } = parseFrontMatter(readFileSync(join(srcDir, file), "utf8"));
  const prefix = `d${index + 1}`;
  const { html, toc } = render(body, prefix);
  const name = basename(file, ".md");
  return { file, name, meta, html, toc, prefix };
});

mkdirSync(htmlDir, { recursive: true });
mkdirSync(pdfDir, { recursive: true });
const outputs = [];

for (const d of docs) {
  if (only && !d.name.includes(only)) continue;
  const body = `${cover(d.meta)}${control(d.meta)}${tocHtml(d.toc)}<main class="doc page-break">${d.html}</main>`;
  const out = join(htmlDir, `${d.name}.html`);
  writeFileSync(out, page(d.meta.title, `ProctorAI · ${d.meta.title} · ${d.meta.id}`, body));
  outputs.push({ html: out, pdf: join(pdfDir, `${d.meta.pdf || d.name}.pdf`) });
}

if (!only) {
  const master = {
    title: "ProctorAI Documentation Handbook",
    subtitle: "The complete reference set: requirements, architecture, API, data, security, deployment, operations and user guidance.",
    id: "PAI-HB-000",
    version: "1.0",
    category: "Complete handbook",
    audience: "Everyone who builds, deploys, operates or uses ProctorAI",
    related: "Every document in this handbook is also published as a standalone PDF in docs/pdf/.",
  };
  const list = docs
    .map((d, i) => `<li><span class="doc-index">${String(i + 1).padStart(2, "0")}</span><div><b>${escapeHtml(d.meta.title)}</b><small>${escapeHtml(d.meta.id)} · ${escapeHtml(d.meta.subtitle || "")}</small></div></li>`)
    .join("");
  const masterToc = `<section class="toc page-break"><h1 class="toc-title">Documents in this handbook</h1><ol class="doc-list">${docs
    .map((d, i) => `<li><a href="#${d.prefix}-chapter"><span class="toc-num">${String(i + 1).padStart(2, "0")}</span>${escapeHtml(d.meta.title)}<em>${escapeHtml(d.meta.id)}</em></a></li>`)
    .join("")}</ol></section>`;
  const chapters = docs
    .map(
      (d, i) => `<section class="chapter page-break" id="${d.prefix}-chapter">
  <div class="chapter-num">${String(i + 1).padStart(2, "0")}</div>
  <div class="chapter-kicker">${escapeHtml(d.meta.category || "")} · ${escapeHtml(d.meta.id)}</div>
  <h1 class="chapter-title">${escapeHtml(d.meta.title)}</h1>
  <p class="chapter-subtitle">${escapeHtml(d.meta.subtitle || "")}</p>
  <ol class="chapter-toc">${d.toc.filter((t) => t.depth === 2).map((t) => `<li><a href="#${t.id}">${t.text}</a></li>`).join("")}</ol>
</section><main class="doc page-break">${d.html}</main>`
    )
    .join("");
  const body = `${cover(master, `<ol class="cover-list">${list}</ol>`)}${control(master)}${masterToc}${chapters}`;
  const out = join(htmlDir, "00-handbook.html");
  writeFileSync(out, page(master.title, "ProctorAI · Documentation Handbook · PAI-HB-000", body));
  outputs.push({ html: out, pdf: join(pdfDir, "ProctorAI-Documentation-Handbook.pdf") });
}

if (htmlOnly) {
  console.log(`HTML written to ${htmlDir}`);
  process.exit(0);
}

const { default: puppeteer } = await import("puppeteer-core");
const candidates = [
  process.env.CHROME_PATH,
  "/usr/bin/google-chrome",
  "/usr/bin/google-chrome-stable",
  "/usr/bin/chromium",
  "/usr/bin/chromium-browser",
  "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome",
  "C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe",
].filter(Boolean);
const executablePath = candidates.find((p) => existsSync(p));
if (!executablePath) {
  console.error("Chrome or Chromium not found. Set CHROME_PATH.");
  process.exit(1);
}
const browser = await puppeteer.launch({ executablePath, headless: true, args: ["--no-sandbox", "--font-render-hinting=none"] });
for (const o of outputs) {
  const tab = await browser.newPage();
  tab.on("console", (m) => m.type() === "error" && console.error(`  [${basename(o.html)}] ${m.text()}`));
  await tab.goto(pathToFileURL(o.html).href, { waitUntil: "networkidle0", timeout: 120000 });
  await tab.evaluate(() => window.__ready);
  await tab.pdf({ path: o.pdf, preferCSSPageSize: true, printBackground: true, outline: true, tagged: true, timeout: 300000 });
  await tab.close();
  console.log(`✓ ${basename(o.pdf)}`);
}
await browser.close();
