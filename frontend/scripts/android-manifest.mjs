import { existsSync, readFileSync, writeFileSync } from "node:fs";
import { fileURLToPath } from "node:url";

const manifest = fileURLToPath(new URL("../src-tauri/gen/android/app/src/main/AndroidManifest.xml", import.meta.url));

const entries = [
  { name: "android.permission.CAMERA", line: '<uses-permission android:name="android.permission.CAMERA" />' },
  { name: "android.hardware.camera", line: '<uses-feature android:name="android.hardware.camera" android:required="false" />' },
];

if (!existsSync(manifest)) {
  console.error("AndroidManifest.xml not found. Run npm run tauri:android:init first.");
  process.exit(1);
}

let xml = readFileSync(manifest, "utf8");
const missing = entries.filter((entry) => !xml.includes(`android:name="${entry.name}"`));
if (missing.length === 0) {
  console.log("AndroidManifest.xml already declares camera access.");
  process.exit(0);
}

const index = xml.indexOf("<application");
if (index < 0) {
  console.error("AndroidManifest.xml has no <application> element.");
  process.exit(1);
}

xml = `${xml.slice(0, index)}${missing.map((entry) => `${entry.line}\n    `).join("")}${xml.slice(index)}`;
writeFileSync(manifest, xml);
console.log(`Declared ${missing.map((entry) => entry.name).join(" and ")} in AndroidManifest.xml.`);
