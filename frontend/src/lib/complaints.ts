import { qs } from "./api";
import type { Complaint } from "./types";

export type ComplaintFilters = {
  page: number;
  status?: string;
  priority?: string;
  q?: string;
  mine?: string;
};

export const COMPLAINT_STATUSES = ["pending", "reviewed", "resolved", "rejected"] as const;
export const COMPLAINT_PRIORITIES = ["low", "medium", "high", "urgent"] as const;

export function parseComplaintFilters(params: Record<string, unknown>): ComplaintFilters {
  const one = (key: string) => {
    const value = params[key];
    return typeof value === "string" ? value : typeof value === "number" || typeof value === "boolean" ? String(value) : undefined;
  };
  const page = Number(one("page") ?? 0);
  return {
    page: Number.isFinite(page) && page > 0 ? Math.floor(page) : 0,
    status: COMPLAINT_STATUSES.includes(one("status") as never) ? one("status") : undefined,
    priority: COMPLAINT_PRIORITIES.includes(one("priority") as never) ? one("priority") : undefined,
    q: one("q")?.slice(0, 80),
    mine: one("mine") === "assigned" ? "assigned" : undefined,
  };
}

export function complaintsPath(filters: ComplaintFilters, assignedTo?: string): string {
  return `/complaints${qs({
    page: filters.page,
    size: 10,
    sortBy: "createdAt",
    direction: "desc",
    status: filters.status,
    priority: filters.priority,
    title: filters.q ? `ilike.%${filters.q}%` : undefined,
    assignedTo: filters.mine === "assigned" ? assignedTo : undefined,
  })}`;
}

export function complaintsHref(filters: Partial<ComplaintFilters>): string {
  const query = qs({ page: filters.page || undefined, status: filters.status, priority: filters.priority, q: filters.q, mine: filters.mine });
  return `/complaints${query}`;
}

export function subjectNames(complaint: Pick<Complaint, "subjects" | "studentName">): string[] {
  const names = (complaint.subjects ?? []).map((subject) => subject.name).filter((name): name is string => Boolean(name));
  return names.length > 0 ? names : complaint.studentName ? [complaint.studentName] : [];
}

export function subjectSummary(complaint: Pick<Complaint, "subjects" | "studentName">, max = 2): string | null {
  const names = subjectNames(complaint);
  if (names.length === 0) return null;
  return names.length <= max ? names.join(", ") : `${names.slice(0, max).join(", ")} +${names.length - max} more`;
}
