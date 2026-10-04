import { nativeStore } from "./store";

export type TokenInfo = {
  expiresIn: number;
  accessToken?: string | null;
  refreshToken?: string | null;
};

export const TOKEN_DELIVERY = { "X-Token-Delivery": "body" } as const;

const STORE = "session.json";
const KEY = "refreshToken";

let accessToken: string | null = null;
let accessExpiresAt = 0;
let refreshToken: string | null = null;

export async function loadTokens(): Promise<void> {
  const store = nativeStore(STORE);
  if (!store) return;
  refreshToken = (await (await store).get<string>(KEY)) ?? null;
}

export function authorization(): string | null {
  return accessToken && Date.now() < accessExpiresAt ? `Bearer ${accessToken}` : null;
}

export function accessTokenFresh(marginMs = 30_000): boolean {
  return accessToken !== null && Date.now() + marginMs < accessExpiresAt;
}

export function currentRefreshToken(): string | null {
  return refreshToken;
}

export async function saveTokens(info: TokenInfo | undefined): Promise<void> {
  if (!info?.accessToken || !info.refreshToken) throw new Error("The server did not return a session for this app.");
  accessToken = info.accessToken;
  accessExpiresAt = Date.now() + info.expiresIn * 1000;
  refreshToken = info.refreshToken;
  const store = nativeStore(STORE);
  if (!store) return;
  const session = await store;
  await session.set(KEY, refreshToken);
  await session.save();
}

export async function clearTokens(): Promise<void> {
  accessToken = null;
  accessExpiresAt = 0;
  refreshToken = null;
  const store = nativeStore(STORE);
  if (!store) return;
  const session = await store;
  await session.delete(KEY);
  await session.save();
}
