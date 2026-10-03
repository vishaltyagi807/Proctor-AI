import { useEffect, useState } from "react";
import { AnimatePresence, motion } from "motion/react";
import { ArrowDownUp, BadgeCheck, Building2, CalendarDays, Search, ShieldCheck, X } from "lucide-react";
import { useApi } from "@/lib/query";
import { useSession } from "@/components/providers/session";
import { Input } from "@/components/ui/form";
import { Card } from "@/components/ui/card";
import { Tabs } from "@/components/ui/misc";
import { Hint } from "@/components/ui/hint";
import { Select, SelectContent, SelectGroup, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { cn } from "@/lib/utils";
import { DEPARTMENTS_PATH, ROLES_PATH, USER_JOINED, USER_SORTS, hasUserFilters, userCountPath, type UserFilters } from "@/lib/users";
import type { Department, PageResponse, Role, UserInfo } from "@/lib/types";

type Go = (next: Partial<UserFilters>, options?: { replace?: boolean }) => void;

const VERIFIED_ITEMS: Record<string, string> = { any: "Any verification", true: "Verified", false: "Unverified" };
const JOINED_ITEMS: Record<string, string> = { any: "Any time", ...Object.fromEntries(Object.entries(USER_JOINED).map(([key, value]) => [key, value.label])) };
const SORT_ITEMS: Record<string, string> = Object.fromEntries(Object.entries(USER_SORTS).map(([key, value]) => [key, value.label]));

function FilterSelect({
  icon: Icon,
  label,
  items,
  value,
  active,
  onChange,
}: {
  icon: React.ComponentType<{ className?: string }>;
  label: string;
  items: Record<string, string>;
  value: string;
  active: boolean;
  onChange: (value: string) => void;
}) {
  return (
    <Select items={items} value={value} onValueChange={(next) => next && onChange(next)}>
      <SelectTrigger
        aria-label={label}
        className={cn(
          "h-9 w-full gap-2 rounded-xl px-3 sm:w-auto",
          active ? "border-primary/40 bg-primary/8 text-primary dark:bg-primary/15" : "bg-background/60",
        )}
      >
        <Icon className={cn("size-4", active ? "text-primary" : "text-muted-foreground")} />
        <SelectValue />
      </SelectTrigger>
      <SelectContent alignItemWithTrigger={false} align="start" className="min-w-44">
        <SelectGroup>
          {Object.entries(items).map(([key, text]) => (
            <SelectItem key={key} value={key}>
              {text}
            </SelectItem>
          ))}
        </SelectGroup>
      </SelectContent>
    </Select>
  );
}

function useStatusCounts(filters: UserFilters) {
  const all = useApi<PageResponse<UserInfo>>(userCountPath(filters, undefined), { keepPrevious: true });
  const active = useApi<PageResponse<UserInfo>>(userCountPath(filters, true), { keepPrevious: true });
  const disabled = useApi<PageResponse<UserInfo>>(userCountPath(filters, false), { keepPrevious: true });
  return { all: all.data?.totalElements, active: active.data?.totalElements, disabled: disabled.data?.totalElements };
}

export function UsersToolbar({ filters, go, actions }: { filters: UserFilters; go: Go; actions?: React.ReactNode }) {
  const { can } = useSession();
  const departments = useApi<PageResponse<Department>>(DEPARTMENTS_PATH);
  const roles = useApi<PageResponse<Role>>(can("roles", "read") ? ROLES_PATH : null);
  const departmentItems: Record<string, string> = {
    any: "Any department",
    none: "No department",
    ...Object.fromEntries((departments.data?.content ?? []).map((department) => [department.id, department.name])),
  };
  const roleItems: Record<string, string> = {
    any: "Any role",
    none: "No role",
    ...Object.fromEntries((roles.data?.content ?? []).map((role) => [role.id, role.name.replaceAll("_", " ")])),
  };
  const [search, setSearch] = useState(filters.q ?? "");
  const [appliedQ, setAppliedQ] = useState(filters.q);
  if (appliedQ !== filters.q) {
    setAppliedQ(filters.q);
    setSearch(filters.q ?? "");
  }

  useEffect(() => {
    const next = search.trim() || undefined;
    if (next === filters.q) return;
    const timer = window.setTimeout(() => go({ q: next }, { replace: true }), 350);
    return () => window.clearTimeout(timer);
  }, [search, filters.q, go]);

  const counts = useStatusCounts(filters);
  const status = filters.enabled === true ? "active" : filters.enabled === false ? "disabled" : "all";

  const chips = [
    filters.q && { key: "q", label: `“${filters.q}”`, clear: () => go({ q: undefined }) },
    filters.enabled !== undefined && { key: "enabled", label: filters.enabled ? "Active" : "Disabled", clear: () => go({ enabled: undefined }) },
    filters.verified !== undefined && { key: "verified", label: filters.verified ? "Verified" : "Unverified", clear: () => go({ verified: undefined }) },
    filters.joined && { key: "joined", label: `Joined ${USER_JOINED[filters.joined].label.toLowerCase()}`, clear: () => go({ joined: undefined }) },
    filters.department && { key: "department", label: departmentItems[filters.department] ?? "Department", clear: () => go({ department: undefined }) },
    filters.role && { key: "role", label: filters.role === "none" ? "No role" : `Role: ${roleItems[filters.role] ?? "selected"}`, clear: () => go({ role: undefined }) },
  ].filter(Boolean) as { key: string; label: string; clear: () => void }[];

  return (
    <Card className="mb-5 overflow-hidden">
      <div className="flex flex-col gap-3 p-4 lg:flex-row lg:items-center">
        <div className="relative min-w-0 flex-1">
          <Search className="pointer-events-none absolute left-3.5 top-1/2 size-4 -translate-y-1/2 text-muted-foreground" />
          <Input
            value={search}
            onChange={(event) => setSearch(event.target.value)}
            onKeyDown={(event) => {
              if (event.key === "Enter") go({ q: search.trim() || undefined });
              if (event.key === "Escape") setSearch("");
            }}
            placeholder="Search by name or email…"
            aria-label="Search users"
            className="h-10 pl-10 pr-10"
          />
          {search && (
            <Hint label="Clear search">
              <button
                type="button"
                onClick={() => setSearch("")}
                className="absolute right-2.5 top-1/2 grid size-6 -translate-y-1/2 place-items-center rounded-md text-muted-foreground transition hover:bg-muted hover:text-foreground"
                aria-label="Clear search"
              >
                <X className="size-3.5" />
              </button>
            </Hint>
          )}
        </div>
        <Tabs
          id="user-status"
          value={status}
          onChange={(value) => go({ enabled: value === "active" ? true : value === "disabled" ? false : undefined })}
          tabs={[
            { value: "all", label: "All", count: counts.all },
            { value: "active", label: "Active", count: counts.active },
            { value: "disabled", label: "Disabled", count: counts.disabled },
          ]}
        />
      </div>

      <div className="flex flex-col gap-2 border-t bg-muted/25 px-4 py-3 sm:flex-row sm:flex-wrap sm:items-center">
        <span className="hidden text-xs font-medium uppercase tracking-[0.12em] text-muted-foreground sm:inline">Filters</span>
        <FilterSelect icon={BadgeCheck} label="Verification" items={VERIFIED_ITEMS} value={filters.verified === undefined ? "any" : String(filters.verified)} active={filters.verified !== undefined} onChange={(value) => go({ verified: value === "any" ? undefined : value === "true" })} />
        <FilterSelect icon={CalendarDays} label="Joined" items={JOINED_ITEMS} value={filters.joined ?? "any"} active={Boolean(filters.joined)} onChange={(value) => go({ joined: value === "any" ? undefined : (value as UserFilters["joined"]) })} />
        <FilterSelect icon={Building2} label="Department" items={departmentItems} value={filters.department ?? "any"} active={Boolean(filters.department)} onChange={(value) => go({ department: value === "any" ? undefined : value })} />
        {can("roles", "read") && (
          <FilterSelect icon={ShieldCheck} label="Role" items={roleItems} value={filters.role ?? "any"} active={Boolean(filters.role)} onChange={(value) => go({ role: value === "any" ? undefined : value })} />
        )}
        <div className="flex flex-col gap-2 sm:ml-auto sm:flex-row sm:items-center">
          {actions}
          <FilterSelect icon={ArrowDownUp} label="Sort" items={SORT_ITEMS} value={filters.sort ?? "newest"} active={false} onChange={(value) => go({ sort: value as UserFilters["sort"] })} />
        </div>
      </div>

      <AnimatePresence initial={false}>
        {hasUserFilters(filters) && (
          <motion.div initial={{ height: 0, opacity: 0 }} animate={{ height: "auto", opacity: 1 }} exit={{ height: 0, opacity: 0 }} transition={{ duration: 0.2 }} className="overflow-hidden">
            <div className="flex flex-wrap items-center gap-2 border-t px-4 py-3">
              <span className="text-xs text-muted-foreground">Active:</span>
              <AnimatePresence initial={false}>
                {chips.map((chip) => (
                  <motion.span
                    key={chip.key}
                    layout
                    initial={{ opacity: 0, scale: 0.85 }}
                    animate={{ opacity: 1, scale: 1 }}
                    exit={{ opacity: 0, scale: 0.85 }}
                    className="inline-flex max-w-full items-center gap-1 rounded-full border border-primary/25 bg-primary/8 py-0.5 pl-3 pr-1 text-xs font-medium text-primary"
                  >
                    <span className="truncate">{chip.label}</span>
                    <Hint label="Remove filter">
                      <button type="button" onClick={chip.clear} className="grid size-5 place-items-center rounded-full transition hover:bg-primary/15" aria-label={`Remove ${chip.label}`}>
                        <X className="size-3" />
                      </button>
                    </Hint>
                  </motion.span>
                ))}
              </AnimatePresence>
              <button
                type="button"
                onClick={() => go({ q: undefined, enabled: undefined, verified: undefined, joined: undefined })}
                className="ml-auto text-xs font-medium text-muted-foreground underline-offset-4 transition hover:text-foreground hover:underline"
              >
                Clear all
              </button>
            </div>
          </motion.div>
        )}
      </AnimatePresence>
    </Card>
  );
}
