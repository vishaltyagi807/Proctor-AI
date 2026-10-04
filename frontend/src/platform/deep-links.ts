import { native } from "./env";

export const DEEP_LINK_SCHEME = "proctorai";

export function pathFromDeepLink(raw: string): string | null {
  let url: URL;
  try {
    url = new URL(raw);
  } catch {
    return null;
  }
  let path: string;
  if (url.protocol === `${DEEP_LINK_SCHEME}:`) path = `/${url.host}${url.pathname}`.replace(/\/{2,}/g, "/");
  else if (url.protocol === "https:") path = url.pathname;
  else return null;
  const href = `${path}${url.search}${url.hash}`;
  return href.startsWith("/") && !href.startsWith("//") ? href : null;
}

export async function startDeepLinks(open: (href: string) => void): Promise<void> {
  const tauri = native();
  if (!tauri) return;
  const { getCurrent, onOpenUrl } = await tauri.deepLink();
  const handle = (urls: string[] | null) => {
    const href = urls?.map(pathFromDeepLink).find((value): value is string => value !== null);
    if (href) open(href);
  };
  handle(await getCurrent());
  await onOpenUrl(handle);
}
