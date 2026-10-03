import { qs } from "./api";

export const USER_SORTS = {
  newest: { field: "createdAt", direction: "desc", label: "Newest first" },
  oldest: { field: "createdAt", direction: "asc", label: "Oldest first" },
  name: { field: "name", direction: "asc", label: "Name A–Z" },
  "name-desc": { field: "name", direction: "desc", label: "Name Z–A" },
  email: { field: "email", direction: "asc", label: "Email A–Z" },
  "email-desc": { field: "email", direction: "desc", label: "Email Z–A" },
  updated: { field: "updatedAt", direction: "desc", label: "Recently updated" },
  "updated-asc": { field: "updatedAt", direction: "asc", label: "Least recently updated" },
} as const;

export const USER_JOINED = {
  "7d": { days: 7, label: "Last 7 days" },
  "30d": { days: 30, label: "Last 30 days" },
  "90d": { days: 90, label: "Last 90 days" },
  "365d": { days: 365, label: "Past year" },
} as const;

export const USER_PAGE_SIZES = [10, 20, 50, 100] as const;
export const DEFAULT_USER_PAGE_SIZE = 10;

export type UserSort = keyof typeof USER_SORTS;
export type UserSortField = (typeof USER_SORTS)[UserSort]["field"];

export function nextUserSort(current: UserSort, field: UserSortField): UserSort {
  const entries = Object.entries(USER_SORTS) as [UserSort, (typeof USER_SORTS)[UserSort]][];
  const active = USER_SORTS[current];
  const match = active.field === field ? entries.find(([, sort]) => sort.field === field && sort.direction !== active.direction) : entries.find(([, sort]) => sort.field === field);
  return match?.[0] ?? current;
}
export type UserJoined = keyof typeof USER_JOINED;

export type UserFilters = {
  page: number;
  q?: string;
  enabled?: boolean;
  verified?: boolean;
  joined?: UserJoined;
  department?: string;
  role?: string;
  sort?: UserSort;
  size?: number;
};

function pick<T extends string>(value: string | undefined, allowed: readonly T[]): T | undefined {
  return allowed.find((item) => item === value);
}

const MEMBER_ID = /^(none|[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})$/i;

function member(value: string | undefined): string | undefined {
  return value && MEMBER_ID.test(value) ? value.toLowerCase() : undefined;
}

function memberFilter(value: string | undefined): string | undefined {
  return value === "none" ? "is.null" : value;
}

function flag(value: string | undefined): boolean | undefined {
  return value === "true" ? true : value === "false" ? false : undefined;
}

export function parseUserFilters(params: Record<string, unknown>): UserFilters {
  const one = (key: string) => {
    const value = params[key];
    return typeof value === "string" ? value : typeof value === "number" || typeof value === "boolean" ? String(value) : undefined;
  };
  const page = Number(one("page") ?? 0);
  const size = Number(one("size"));
  const sort = pick(one("sort"), Object.keys(USER_SORTS) as UserSort[]);
  return {
    page: Number.isFinite(page) && page > 0 ? Math.floor(page) : 0,
    q: one("q")?.trim().slice(0, 80) || undefined,
    enabled: flag(one("enabled")),
    verified: flag(one("verified")),
    joined: pick(one("joined"), Object.keys(USER_JOINED) as UserJoined[]),
    department: member(one("department")),
    role: member(one("role")),
    sort: sort === "newest" ? undefined : sort,
    size: USER_PAGE_SIZES.some((item) => item === size) && size !== DEFAULT_USER_PAGE_SIZE ? size : undefined,
  };
}

function joinedSince(joined: UserJoined): string {
  const since = new Date();
  since.setDate(since.getDate() - USER_JOINED[joined].days);
  return since.toISOString().slice(0, 10);
}

function searchParams(filters: Omit<UserFilters, "page">) {
  return {
    enabled: filters.enabled,
    verified: filters.verified,
    createdAt: filters.joined ? `gte.${joinedSince(filters.joined)}` : undefined,
    departmentId: memberFilter(filters.department),
    roleId: memberFilter(filters.role),
    or: filters.q ? `(name.ilike.%${filters.q}%,email.ilike.%${filters.q}%)` : undefined,
  };
}

export function usersPath(filters: UserFilters): string {
  const sort = USER_SORTS[filters.sort ?? "newest"];
  return `/users${qs({
    page: filters.page,
    size: filters.size ?? DEFAULT_USER_PAGE_SIZE,
    sortBy: sort.field,
    direction: sort.direction,
    ...searchParams(filters),
  })}`;
}

export function userCountPath(filters: UserFilters, enabled: boolean | undefined): string {
  return `/users${qs({ page: 0, size: 1, ...searchParams({ ...filters, enabled }) })}`;
}

export function usersHref(filters: Partial<UserFilters>): string {
  return `/users${qs({
    page: filters.page || undefined,
    q: filters.q,
    enabled: filters.enabled,
    verified: filters.verified,
    joined: filters.joined,
    department: filters.department,
    role: filters.role,
    sort: filters.sort === "newest" ? undefined : filters.sort,
    size: filters.size === DEFAULT_USER_PAGE_SIZE ? undefined : filters.size,
  })}`;
}

export function hasUserFilters(filters: UserFilters): boolean {
  return Boolean(filters.q || filters.enabled !== undefined || filters.verified !== undefined || filters.joined || filters.department || filters.role);
}

export const ROLES_PATH = "/roles?size=100&sortBy=level&direction=asc";
export const DEPARTMENTS_PATH = "/departments?size=100&sortBy=name&direction=asc";
export const userInfoPath = (id: string) => `/users?id=${id}&size=1`;
