import { isMobile, native } from "./env";

export async function openExternal(url: string): Promise<void> {
  const tauri = native();
  if (!tauri) {
    window.open(url, "_blank", "noopener");
    return;
  }
  const { openUrl } = await tauri.opener();
  await openUrl(url);
}

export async function downloadFromUrl(url: string, fileName: string): Promise<string | null> {
  const tauri = native();
  if (!tauri || isMobile()) {
    await openExternal(url);
    return null;
  }
  const { save } = await tauri.dialog();
  const path = await save({ defaultPath: fileName, title: "Save file" });
  if (!path) return null;
  const { download } = await tauri.upload();
  await download(url, path);
  return path;
}

export async function saveResponse(response: Response, fallbackName: string): Promise<string | null> {
  const name = fileNameFrom(response.headers.get("Content-Disposition")) ?? fallbackName;
  const tauri = native();
  if (!tauri) {
    const href = URL.createObjectURL(await response.blob());
    const link = document.createElement("a");
    link.href = href;
    link.download = name;
    document.body.appendChild(link);
    link.click();
    link.remove();
    window.setTimeout(() => URL.revokeObjectURL(href), 10_000);
    return null;
  }
  const { save } = await tauri.dialog();
  const path = await save({ defaultPath: name, title: "Save file" });
  if (!path) return null;
  const { writeFile } = await tauri.fs();
  await writeFile(path, new Uint8Array(await response.arrayBuffer()));
  return path;
}

export async function revealFile(path: string): Promise<void> {
  const tauri = native();
  if (!tauri || isMobile()) return;
  const { revealItemInDir } = await tauri.opener();
  await revealItemInDir(path);
}

function fileNameFrom(disposition: string | null): string | null {
  if (!disposition) return null;
  const encoded = /filename\*\s*=\s*(?:UTF-8'')?([^;]+)/i.exec(disposition);
  if (encoded) {
    try {
      return sanitize(decodeURIComponent(encoded[1].trim().replace(/^"|"$/g, "")));
    } catch {
      return null;
    }
  }
  const plain = /filename\s*=\s*"?([^";]+)"?/i.exec(disposition);
  return plain ? sanitize(plain[1].trim()) : null;
}

function sanitize(name: string): string | null {
  const cleaned = Array.from(name, (char) => (char.charCodeAt(0) < 32 || '\\/:*?"<>|'.includes(char) ? "_" : char)).join("").trim();
  return cleaned && cleaned !== "." && cleaned !== ".." ? cleaned : null;
}
