import { native } from "./env";
import { openExternal } from "./files";

const EXTERNAL_PROTOCOLS = new Set(["http:", "https:", "mailto:", "tel:"]);

export function interceptExternalLinks(): void {
  if (!native()) return;
  document.addEventListener("click", (event) => {
    if (event.defaultPrevented || event.button !== 0) return;
    const anchor = event.target instanceof Element ? event.target.closest<HTMLAnchorElement>("a[href]") : null;
    if (!anchor) return;
    const url = new URL(anchor.href, window.location.href);
    if (url.origin === window.location.origin || !EXTERNAL_PROTOCOLS.has(url.protocol)) return;
    event.preventDefault();
    void openExternal(url.href);
  });
}
