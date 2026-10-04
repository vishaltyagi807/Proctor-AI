import type { Store } from "@tauri-apps/plugin-store";
import { native } from "./env";

const stores = new Map<string, Promise<Store>>();

export function nativeStore(name: string): Promise<Store> | null {
  const tauri = native();
  if (!tauri) return null;
  let store = stores.get(name);
  if (!store) {
    store = tauri.store().then(({ load }) => load(name, { defaults: {}, autoSave: false }));
    stores.set(name, store);
  }
  return store;
}
