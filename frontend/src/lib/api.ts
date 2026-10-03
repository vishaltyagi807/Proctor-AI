export const API_BASE = import.meta.env.VITE_API_URL ?? "/api";

let unauthorizedHandler: (() => void) | null = null;

export function onUnauthorized(handler: (() => void) | null) {
  unauthorizedHandler = handler;
}

export class ApiError extends Error {
  status: number;

  constructor(status: number, message: string) {
    super(message);
    this.status = status;
  }
}

let refreshing: Promise<boolean> | null = null;

export function refreshSession(): Promise<boolean> {
  refreshing ??= fetch(`${API_BASE}/auth/refresh`, { method: "POST", credentials: "include" })
    .then((response) => response.ok)
    .catch(() => false)
    .finally(() => {
      window.setTimeout(() => {
        refreshing = null;
      }, 1_000);
    });
  return refreshing;
}

export type RequestOptions = RequestInit & { quiet?: boolean };

async function send(method: string, path: string, body: unknown, init: RequestInit | undefined): Promise<Response> {
  const isForm = typeof FormData !== "undefined" && body instanceof FormData;
  return fetch(`${API_BASE}${path}`, {
    method,
    credentials: "include",
    headers: body !== undefined && !isForm ? { "Content-Type": "application/json" } : undefined,
    body: body === undefined ? undefined : isForm ? (body as FormData) : JSON.stringify(body),
    ...init,
  });
}

async function request<T>(method: string, path: string, body?: unknown, options?: RequestOptions): Promise<T> {
  const { quiet, ...init } = options ?? {};
  let response = await send(method, path, body, init);
  if (response.status === 401 && !path.startsWith("/auth/") && (await refreshSession())) {
    response = await send(method, path, body, init);
  }
  if (response.status === 401 && !quiet) {
    unauthorizedHandler?.();
    throw new ApiError(401, "Session expired");
  }
  if (response.status === 204) return undefined as T;
  const text = await response.text();
  const data = text ? safeJson(text) : undefined;
  if (!response.ok) {
    const message = (data as { message?: string } | undefined)?.message ?? `Request failed (${response.status})`;
    throw new ApiError(response.status, message);
  }
  return data as T;
}

function safeJson(text: string): unknown {
  try {
    return JSON.parse(text);
  } catch {
    return text;
  }
}

export const api = {
  get: <T>(path: string, init?: RequestOptions) => request<T>("GET", path, undefined, init),
  post: <T>(path: string, body?: unknown, init?: RequestOptions) => request<T>("POST", path, body ?? {}, init),
  put: <T>(path: string, body?: unknown) => request<T>("PUT", path, body ?? {}),
  patch: <T>(path: string, body?: unknown) => request<T>("PATCH", path, body ?? {}),
  delete: <T = void>(path: string, body?: unknown) => request<T>("DELETE", path, body),
};

export function apiUrl(path: string): string {
  return `${API_BASE}${path}`;
}

export function qs(params: Record<string, string | number | boolean | null | undefined | string[]>): string {
  const search = new URLSearchParams();
  Object.entries(params).forEach(([key, value]) => {
    if (value === undefined || value === null || value === "") return;
    if (Array.isArray(value)) value.forEach((item) => search.append(key, item));
    else search.set(key, String(value));
  });
  const text = search.toString();
  return text ? `?${text}` : "";
}
