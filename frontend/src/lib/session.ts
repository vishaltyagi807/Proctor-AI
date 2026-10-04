import { queryOptions, type QueryClient } from "@tanstack/react-query";
import { ApiError, api } from "./api";
import { clearTokens, currentRefreshToken, saveTokens, TOKEN_DELIVERY, type TokenInfo } from "@/platform/auth";
import { isNative } from "@/platform/env";
import type { Session } from "./types";

const quiet = { quiet: true };

export const sessionQuery = queryOptions({
  queryKey: ["session"],
  queryFn: async (): Promise<Session> => {
    const [user, roles, permissions, departments] = await Promise.all([
      api.get<Session["user"]>("/users/me", quiet).catch((error: unknown) => {
        if (error instanceof ApiError && (error.status === 403 || error.status === 404)) throw new ApiError(401, "Session expired");
        throw error;
      }),
      api.get<Session["roles"]>("/users/me/roles", quiet),
      api.get<Session["permissions"]>("/users/me/permissions", quiet),
      api.get<Session["departments"]>("/users/me/departments", quiet),
    ]);
    return { user, roles, permissions, departments };
  },
  staleTime: 30_000,
  retry: false,
});

export async function signIn(email: string, password: string): Promise<void> {
  if (!isNative()) {
    await api.post("/auth/login", { email, password }, quiet);
    return;
  }
  const data = await api.post<{ tokenInfo?: TokenInfo }>("/auth/login", { email, password }, { ...quiet, headers: TOKEN_DELIVERY });
  await saveTokens(data.tokenInfo);
}

export async function signOut(client: QueryClient, everywhere = false): Promise<void> {
  const refreshToken = currentRefreshToken();
  const body = isNative() && refreshToken && !everywhere ? { refreshToken } : {};
  await api.post(everywhere ? "/auth/logout-all" : "/auth/logout", body, quiet).catch(() => undefined);
  if (isNative()) await clearTokens();
  await client.cancelQueries();
  client.removeQueries({ queryKey: sessionQuery.queryKey });
}

export function isUnauthenticated(error: unknown): boolean {
  return error instanceof ApiError && error.status === 401;
}

export function safeNext(value: unknown): string {
  return typeof value === "string" && value.startsWith("/") && !value.startsWith("//") ? value : "/";
}
