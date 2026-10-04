import { native } from "./env";

function isBinaryBody(body: BodyInit | null | undefined): boolean {
  return (
    body instanceof FormData ||
    body instanceof Blob ||
    body instanceof ArrayBuffer ||
    body instanceof ReadableStream ||
    (body !== null && body !== undefined && ArrayBuffer.isView(body))
  );
}

export async function httpFetch(input: string, init?: RequestInit): Promise<Response> {
  const tauri = native();
  if (!tauri) return fetch(input, init);
  if (isBinaryBody(init?.body)) return fetch(input, { ...init, credentials: "omit" });
  const { fetch: nativeFetch } = await tauri.http();
  return nativeFetch(input, init);
}
