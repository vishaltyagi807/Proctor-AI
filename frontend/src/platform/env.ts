export type PlatformName = "web" | "windows" | "macos" | "linux" | "android" | "ios";

const modules =
  import.meta.env.MODE === "native"
    ? {
        http: () => import("@tauri-apps/plugin-http"),
        notification: () => import("@tauri-apps/plugin-notification"),
        upload: () => import("@tauri-apps/plugin-upload"),
        fs: () => import("@tauri-apps/plugin-fs"),
        dialog: () => import("@tauri-apps/plugin-dialog"),
        deepLink: () => import("@tauri-apps/plugin-deep-link"),
        opener: () => import("@tauri-apps/plugin-opener"),
        store: () => import("@tauri-apps/plugin-store"),
        os: () => import("@tauri-apps/plugin-os"),
      }
    : null;

export type NativeModules = NonNullable<typeof modules>;

let current: PlatformName = "web";

export function native(): NativeModules | null {
  return modules && typeof window !== "undefined" && "__TAURI_INTERNALS__" in window ? modules : null;
}

export async function detectPlatform(): Promise<PlatformName> {
  const tauri = native();
  if (tauri) {
    const { platform } = await tauri.os();
    const name = platform();
    current = name === "windows" || name === "macos" || name === "android" || name === "ios" ? name : "linux";
  }
  document.documentElement.dataset.platform = current;
  document.documentElement.dataset.native = String(current !== "web");
  return current;
}

export function platformName(): PlatformName {
  return current;
}

export function isNative(): boolean {
  return current !== "web";
}

export function isMobile(): boolean {
  return current === "android" || current === "ios";
}

export function isDesktopApp(): boolean {
  return isNative() && !isMobile();
}
