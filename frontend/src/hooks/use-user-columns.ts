import { useMemo } from "react";
import { qs } from "@/lib/api";
import { useApi } from "@/lib/query";
import { customFieldsPath } from "@/lib/paths";
import { formatDate } from "@/lib/format";
import { useLocalStorage } from "@/hooks/use-local-storage";
import type { UserSortField } from "@/lib/users";
import type { CustomFieldDefinition, PageResponse } from "@/lib/types";

export type BuiltInColumn = "user" | "email" | "roles" | "departments" | "status" | "verified" | "joined" | "updated" | "id";

export type UserColumn =
  | { id: BuiltInColumn; kind: "builtin"; label: string; sortField?: UserSortField; defaultVisible: boolean; locked?: boolean }
  | { id: `cf:${string}`; kind: "custom"; label: string; definition: CustomFieldDefinition; defaultVisible: boolean };

const BUILT_IN: UserColumn[] = [
  { id: "user", kind: "builtin", label: "User", sortField: "name", defaultVisible: true, locked: true },
  { id: "email", kind: "builtin", label: "Email", sortField: "email", defaultVisible: true },
  { id: "roles", kind: "builtin", label: "Roles", defaultVisible: true },
  { id: "departments", kind: "builtin", label: "Departments", defaultVisible: true },
  { id: "status", kind: "builtin", label: "Status", defaultVisible: true },
  { id: "verified", kind: "builtin", label: "Verified", defaultVisible: true },
  { id: "joined", kind: "builtin", label: "Joined", sortField: "createdAt", defaultVisible: true },
  { id: "updated", kind: "builtin", label: "Last updated", sortField: "updatedAt", defaultVisible: true },
  { id: "id", kind: "builtin", label: "User ID", defaultVisible: false },
];

const STORAGE_KEY = "pai.users.columns";

function useUserFieldDefinitions() {
  const list = useApi<PageResponse<CustomFieldDefinition>>(customFieldsPath("users"));
  const applicable = useApi<CustomFieldDefinition[]>(`/custom-fields/applicable${qs({ entity: "users" })}`, { enabled: list.isError });
  const definitions = list.data?.content ?? applicable.data ?? [];
  return definitions.filter((definition) => definition.active);
}

export function useUserColumns() {
  const definitions = useUserFieldDefinitions();
  const [overrides, setOverrides] = useLocalStorage<Record<string, boolean>>(STORAGE_KEY, {});

  const columns = useMemo<UserColumn[]>(
    () => [
      ...BUILT_IN,
      ...definitions.map((definition): UserColumn => ({ id: `cf:${definition.key}`, kind: "custom", label: definition.label, definition, defaultVisible: true })),
    ],
    [definitions],
  );

  const isVisible = (column: UserColumn) => (column.kind === "builtin" && column.locked) || (overrides[column.id] ?? column.defaultVisible);
  const visible = columns.filter(isVisible);

  return {
    columns,
    visible,
    isVisible,
    toggle: (column: UserColumn, show: boolean) => setOverrides((current) => ({ ...current, [column.id]: show })),
    showAll: () => setOverrides(Object.fromEntries(columns.map((column) => [column.id, true]))),
    reset: () => setOverrides({}),
    hiddenCount: columns.length - visible.length,
  };
}

export function formatCustomValue(definition: CustomFieldDefinition, raw: unknown): string {
  if (raw === undefined || raw === null || raw === "") return "—";
  if (Array.isArray(raw)) return raw.length ? raw.join(", ") : "—";
  if (typeof raw === "boolean") return raw ? "Yes" : "No";
  if (definition.dataType === "date" && typeof raw === "string") return formatDate(raw);
  return String(raw);
}
