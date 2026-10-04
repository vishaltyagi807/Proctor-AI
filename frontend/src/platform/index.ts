import { detectPlatform, isNative } from "./env";
import { loadTokens } from "./auth";
import { interceptExternalLinks } from "./links";
import { loadServerUrl } from "./server";

export async function initPlatform(): Promise<void> {
  await detectPlatform();
  if (!isNative()) return;
  await Promise.all([loadServerUrl(), loadTokens()]);
  interceptExternalLinks();
}
