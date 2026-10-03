import { apiUrl, refreshSession } from "./api";

const MAX_DELAY_MS = 30_000;

export function openStream(path: string, attach: (source: EventSource) => void): () => void {
  let source: EventSource | null = null;
  let timer: number | undefined;
  let attempt = 0;
  let closed = false;

  const connect = () => {
    if (closed) return;
    source = new EventSource(apiUrl(path), { withCredentials: true });
    source.addEventListener("open", () => {
      attempt = 0;
    });
    source.addEventListener("error", () => {
      if (!source || source.readyState !== EventSource.CLOSED || closed) return;
      source = null;
      const delay = Math.min(1_000 * 2 ** attempt, MAX_DELAY_MS);
      attempt += 1;
      timer = window.setTimeout(async () => {
        await refreshSession();
        connect();
      }, delay);
    });
    attach(source);
  };

  timer = window.setTimeout(connect, 0);

  return () => {
    closed = true;
    window.clearTimeout(timer);
    source?.close();
  };
}
