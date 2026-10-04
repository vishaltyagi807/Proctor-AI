import { execFileSync } from "node:child_process";
import { copyFileSync, cpSync, mkdtempSync, readFileSync, rmSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { fileURLToPath } from "node:url";

const root = fileURLToPath(new URL("..", import.meta.url));
const brand = join(root, "brand");
const icons = join(root, "src-tauri", "icons");
const publicDir = join(root, "public");
const npx = process.platform === "win32" ? "npx.cmd" : "npx";

const work = mkdtempSync(join(tmpdir(), "proctorai-icons-"));

function tauriIcon(source, args) {
  execFileSync(npx, ["tauri", "icon", join(brand, source), ...args], { cwd: root, stdio: ["ignore", "ignore", "inherit"] });
}

function render(source, sizes) {
  const out = mkdtempSync(join(work, "render-"));
  tauriIcon(source, ["-o", out, "-p", sizes.join(",")]);
  return (size) => readFileSync(join(out, `${size}x${size}.png`));
}

function packIco(images) {
  const header = Buffer.alloc(6);
  header.writeUInt16LE(0, 0);
  header.writeUInt16LE(1, 2);
  header.writeUInt16LE(images.length, 4);
  const entries = [];
  let offset = 6 + images.length * 16;
  for (const { size, data } of images) {
    const entry = Buffer.alloc(16);
    entry.writeUInt8(size >= 256 ? 0 : size, 0);
    entry.writeUInt8(size >= 256 ? 0 : size, 1);
    entry.writeUInt8(0, 2);
    entry.writeUInt8(0, 3);
    entry.writeUInt16LE(1, 4);
    entry.writeUInt16LE(32, 6);
    entry.writeUInt32LE(data.length, 8);
    entry.writeUInt32LE(offset, 12);
    entries.push(entry);
    offset += data.length;
  }
  return Buffer.concat([header, ...entries, ...images.map((image) => image.data)]);
}

try {
  console.log("Generating desktop and mobile icons from brand/icon-manifest.json");
  rmSync(join(icons, "android"), { recursive: true, force: true });
  tauriIcon("icon-manifest.json", []);

  const ios = mkdtempSync(join(work, "ios-"));
  tauriIcon("proctorai-icon-maskable.svg", ["-o", ios, "--ios-color", "#5851f8"]);
  rmSync(join(icons, "ios"), { recursive: true, force: true });
  cpSync(join(ios, "ios"), join(icons, "ios"), { recursive: true });

  const small = render("proctorai-icon-small.svg", [16, 24, 32, 48]);
  const full = render("proctorai-icon.svg", [64, 128, 192, 256, 512]);
  const maskable = render("proctorai-icon-maskable.svg", [180, 512]);
  const mac = mkdtempSync(join(work, "macos-"));
  tauriIcon("proctorai-icon-macos.svg", ["-o", mac]);

  writeFileSync(join(icons, "32x32.png"), small(32));
  writeFileSync(
    join(icons, "icon.ico"),
    packIco([
      { size: 16, data: small(16) },
      { size: 24, data: small(24) },
      { size: 32, data: small(32) },
      { size: 48, data: small(48) },
      { size: 64, data: full(64) },
      { size: 128, data: full(128) },
      { size: 256, data: full(256) },
    ]),
  );
  copyFileSync(join(mac, "icon.icns"), join(icons, "icon.icns"));

  console.log("Generating web icons in public/");
  copyFileSync(join(brand, "proctorai-icon-small.svg"), join(publicDir, "favicon.svg"));
  writeFileSync(join(publicDir, "favicon.ico"), packIco([16, 32, 48].map((size) => ({ size, data: small(size) }))));
  writeFileSync(join(publicDir, "icon-192.png"), full(192));
  writeFileSync(join(publicDir, "icon-512.png"), full(512));
  writeFileSync(join(publicDir, "icon-maskable-512.png"), maskable(512));
  writeFileSync(join(publicDir, "apple-touch-icon.png"), maskable(180));
  console.log("Icons updated.");
} finally {
  rmSync(work, { recursive: true, force: true });
}
