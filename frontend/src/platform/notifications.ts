import { native } from "./env";

let permission: Promise<boolean> | null = null;

export function ensureNotificationPermission(): Promise<boolean> {
  const tauri = native();
  if (!tauri) return Promise.resolve(false);
  permission ??= tauri
    .notification()
    .then(async ({ isPermissionGranted, requestPermission }) => (await isPermissionGranted()) || (await requestPermission()) === "granted")
    .catch(() => false);
  return permission;
}

export async function notifyNative(title: string, body?: string): Promise<void> {
  const tauri = native();
  if (!tauri || (!document.hidden && document.hasFocus())) return;
  if (!(await ensureNotificationPermission())) return;
  const { sendNotification } = await tauri.notification();
  sendNotification(body ? { title, body } : { title });
}
