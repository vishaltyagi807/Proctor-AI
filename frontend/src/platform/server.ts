import { isNative } from "./env";
import { httpFetch } from "./http";
import { nativeStore } from "./store";

const STORE = "settings.json";
const KEY = "serverUrl";

let server = "";

export function normalizeServerUrl(value: string): string | null {
  const trimmed = value.trim();
  if (!trimmed) return null;
  const candidate = /^[a-z][a-z0-9+.-]*:\/\//i.test(trimmed) ? trimmed : `https://${trimmed}`;
  try {
    const url = new URL(candidate);
    if ((url.protocol !== "https:" && url.protocol !== "http:") || url.username || url.password) return null;
    return url.origin;
  } catch {
    return null;
  }
}

function bundledServer(): string {
  const configured = normalizeServerUrl(import.meta.env.VITE_SERVER_URL ?? "");
  if (configured) return configured;
  const { protocol, hostname, origin } = window.location;
  if (import.meta.env.DEV && (protocol === "http:" || protocol === "https:") && !hostname.endsWith("tauri.localhost")) return origin;
  return "";
}

export async function loadServerUrl(): Promise<void> {
  const store = nativeStore(STORE);
  if (!store) return;
  const saved = normalizeServerUrl((await (await store).get<string>(KEY)) ?? "");
  server = saved ?? bundledServer();
}

export function serverUrl(): string {
  return server;
}

export function needsServer(): boolean {
  return isNative() && !server;
}

export function apiBase(): string {
  return isNative() ? `${server}/api` : (import.meta.env.VITE_API_URL ?? "/api");
}

export async function saveServerUrl(value: string): Promise<string> {
  const url = normalizeServerUrl(value);
  if (!url) throw new Error("Enter a valid server address, for example https://app.example.com");
  let response: Response;
  try {
    response = await httpFetch(`${url}/api/actuator/health`, { headers: { Accept: "application/json" } });
  } catch {
    throw new Error("Could not reach that server. Check the address and your connection.");
  }
  if (!response.ok) throw new Error(`That address did not respond like a ProctorAI server (${response.status}).`);
  const store = nativeStore(STORE);
  if (store) {
    const settings = await store;
    await settings.set(KEY, url);
    await settings.save();
  }
  server = url;
  return url;
}
