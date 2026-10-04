import { apiUrl, authHeaders, refreshSession } from "./api";
import { httpFetch } from "@/platform/http";

const MAX_DELAY_MS = 30_000;

export type StreamSource = {
  addEventListener(type: string, listener: (event: MessageEvent<string>) => void): void;
};

type Emit = (type: string, data: string) => void;

async function readEvents(body: ReadableStream<Uint8Array>, emit: Emit, signal: AbortSignal): Promise<void> {
  const reader = body.getReader();
  const decoder = new TextDecoder();
  let buffer = "";
  let type = "message";
  let data: string[] = [];
  const line = (text: string) => {
    if (text === "") {
      if (data.length > 0) emit(type, data.join("\n"));
      type = "message";
      data = [];
      return;
    }
    if (text.startsWith(":")) return;
    const colon = text.indexOf(":");
    const field = colon < 0 ? text : text.slice(0, colon);
    let value = colon < 0 ? "" : text.slice(colon + 1);
    if (value.startsWith(" ")) value = value.slice(1);
    if (field === "event") type = value || "message";
    else if (field === "data") data.push(value);
  };
  try {
    while (!signal.aborted) {
      const { value, done } = await reader.read();
      if (done) break;
      buffer += decoder.decode(value, { stream: true });
      let index = buffer.search(/[\r\n]/);
      while (index >= 0) {
        if (buffer[index] === "\r" && index === buffer.length - 1) break;
        const step = buffer[index] === "\r" && buffer[index + 1] === "\n" ? 2 : 1;
        line(buffer.slice(0, index));
        buffer = buffer.slice(index + step);
        index = buffer.search(/[\r\n]/);
      }
    }
  } finally {
    reader.releaseLock();
  }
}

export function openStream(path: string, attach: (source: StreamSource) => void): () => void {
  const target = new EventTarget();
  attach({ addEventListener: (type, listener) => target.addEventListener(type, listener as EventListener) });

  let controller: AbortController | null = null;
  let timer: number | undefined;
  let attempt = 0;
  let closed = false;

  const emit: Emit = (type, data) => target.dispatchEvent(new MessageEvent(type, { data }));

  const reconnect = (unauthorized: boolean) => {
    if (closed) return;
    target.dispatchEvent(new Event("error"));
    const delay = Math.min(1_000 * 2 ** attempt, MAX_DELAY_MS);
    attempt += 1;
    timer = window.setTimeout(async () => {
      if (unauthorized) await refreshSession();
      void connect();
    }, delay);
  };

  const connect = async () => {
    if (closed) return;
    controller = new AbortController();
    let unauthorized = false;
    try {
      const response = await httpFetch(apiUrl(path), {
        headers: { Accept: "text/event-stream", ...(await authHeaders()) },
        credentials: "include",
        cache: "no-store",
        signal: controller.signal,
      });
      unauthorized = response.status === 401;
      if (!response.ok || !response.body) throw new Error(`Stream failed (${response.status})`);
      attempt = 0;
      target.dispatchEvent(new Event("open"));
      await readEvents(response.body, emit, controller.signal);
    } catch {
      if (closed) return;
    }
    reconnect(unauthorized);
  };

  timer = window.setTimeout(() => void connect(), 0);

  return () => {
    closed = true;
    window.clearTimeout(timer);
    controller?.abort();
  };
}
