import { accessTokenFresh, authorization, clearTokens, currentRefreshToken, saveTokens, TOKEN_DELIVERY, type TokenInfo } from "@/platform/auth";
import { isNative } from "@/platform/env";
import { httpFetch } from "@/platform/http";
import { apiBase } from "@/platform/server";

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

async function refreshNative(): Promise<boolean> {
  const refreshToken = currentRefreshToken();
  if (!refreshToken) return false;
  const response = await httpFetch(`${apiBase()}/auth/refresh`, {
    method: "POST",
    headers: { "Content-Type": "application/json", ...TOKEN_DELIVERY },
    body: JSON.stringify({ refreshToken }),
  });
  if (!response.ok) {
    if (response.status === 400 || response.status === 401 || response.status === 403) await clearTokens();
    return false;
  }
  const data = (await response.json()) as { tokenInfo?: TokenInfo };
  await saveTokens(data.tokenInfo);
  return true;
}

export function refreshSession(): Promise<boolean> {
  refreshing ??= (
    isNative()
      ? refreshNative()
      : httpFetch(`${apiBase()}/auth/refresh`, { method: "POST", credentials: "include" }).then((response) => response.ok)
  )
    .catch(() => false)
    .finally(() => {
      window.setTimeout(() => {
        refreshing = null;
      }, 1_000);
    });
  return refreshing;
}

export type RequestOptions = RequestInit & { quiet?: boolean };

export async function authHeaders(): Promise<Record<string, string>> {
  if (!isNative()) return {};
  if (!accessTokenFresh() && currentRefreshToken()) await refreshSession();
  const value = authorization();
  return value ? { Authorization: value } : {};
}

async function send(method: string, path: string, body: unknown, init: RequestInit | undefined): Promise<Response> {
  const isForm = typeof FormData !== "undefined" && body instanceof FormData;
  const headers = new Headers(init?.headers);
  if (body !== undefined && !isForm && !headers.has("Content-Type")) headers.set("Content-Type", "application/json");
  Object.entries(await authHeaders()).forEach(([name, value]) => headers.set(name, value));
  return httpFetch(`${apiBase()}${path}`, {
    method,
    credentials: "include",
    ...init,
    headers,
    body: body === undefined ? undefined : isForm ? (body as FormData) : JSON.stringify(body),
  });
}

export async function apiResponse(path: string, init?: RequestInit): Promise<Response> {
  let response = await send("GET", path, undefined, init);
  if (response.status === 401 && (await refreshSession())) response = await send("GET", path, undefined, init);
  if (response.status === 401) {
    unauthorizedHandler?.();
    throw new ApiError(401, "Session expired");
  }
  if (!response.ok) throw new ApiError(response.status, `Request failed (${response.status})`);
  return response;
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
  return `${apiBase()}${path}`;
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
