import { AnimatePresence, motion } from "motion/react";
import { useApi } from "@/lib/query";
import { qs } from "@/lib/api";
import { Chips, Field, Input, Select, Switch } from "@/components/ui/form";
import { Skeleton } from "@/components/ui/misc";
import type { CustomFieldDefinition, CustomFieldEntity } from "@/lib/types";

export type CustomValues = Record<string, unknown>;

export function useApplicableFields(entity: CustomFieldEntity, roleIds: string[] = [], departmentIds: string[] = []) {
  const path = `/custom-fields/applicable${qs({ entity, roleId: roleIds, departmentId: departmentIds })}`;
  return useApi<CustomFieldDefinition[]>(path, { keepPrevious: true });
}

export function CustomFieldsForm({
  entity,
  roleIds,
  departmentIds,
  value,
  onChange,
  errors,
}: {
  entity: CustomFieldEntity;
  roleIds?: string[];
  departmentIds?: string[];
  value: CustomValues;
  onChange: (value: CustomValues) => void;
  errors?: Record<string, string>;
}) {
  const { data, isLoading } = useApplicableFields(entity, roleIds, departmentIds);
  if (isLoading) return <Skeleton className="h-20" />;
  if (!data || data.length === 0) return null;

  const set = (key: string, next: unknown) => onChange({ ...value, [key]: next });

  return (
    <div className="space-y-4">
      <p className="text-[11px] font-medium uppercase tracking-[0.14em] text-muted-foreground">Additional details</p>
      <AnimatePresence initial={false}>
        {data.map((definition) => {
          const current = value[definition.key];
          const label = definition.required ? `${definition.label} *` : definition.label;
          return (
            <motion.div key={definition.key} layout initial={{ opacity: 0, height: 0 }} animate={{ opacity: 1, height: "auto" }} exit={{ opacity: 0, height: 0 }}>
              <Field label={label} hint={definition.helpText ?? undefined} error={errors?.[definition.key]}>
                {definition.dataType === "text" && <Input maxLength={definition.maxLength ?? undefined} value={(current as string) ?? ""} onChange={(event) => set(definition.key, event.target.value)} />}
                {definition.dataType === "number" && (
                  <Input type="number" min={definition.minValue ?? undefined} max={definition.maxValue ?? undefined} step="any" value={(current as number | string) ?? ""} onChange={(event) => set(definition.key, event.target.value === "" ? undefined : Number(event.target.value))} />
                )}
                {definition.dataType === "date" && <Input type="date" value={(current as string) ?? ""} onChange={(event) => set(definition.key, event.target.value || undefined)} />}
                {definition.dataType === "bool" && (
                  <div className="flex h-10 items-center">
                    <Switch checked={Boolean(current)} onChange={(next) => set(definition.key, next)} label={definition.label} />
                  </div>
                )}
                {definition.dataType === "select" && (
                  <Select
                    value={(current as string) ?? ""}
                    onValueChange={(value) => set(definition.key, value || undefined)}
                    placeholder="Select…"
                    aria-label={definition.label}
                    options={[{ value: "", label: "None" }, ...(definition.options ?? []).map((option) => ({ value: option, label: option }))]}
                  />
                )}
                {definition.dataType === "multi_select" && (
                  <Chips options={(definition.options ?? []).map((option) => ({ value: option, label: option }))} value={(current as string[]) ?? []} onChange={(next) => set(definition.key, next)} />
                )}
              </Field>
            </motion.div>
          );
        })}
      </AnimatePresence>
    </div>
  );
}

export function CustomFieldsDisplay({ entity, values, roleIds, departmentIds }: { entity: CustomFieldEntity; values: CustomValues | undefined; roleIds?: string[]; departmentIds?: string[] }) {
  const { data } = useApplicableFields(entity, roleIds, departmentIds);
  const entries = data?.filter((definition) => values?.[definition.key] !== undefined && values?.[definition.key] !== null) ?? [];
  if (entries.length === 0) return null;
  return (
    <dl className="grid gap-x-8 gap-y-3 sm:grid-cols-2">
      {entries.map((definition) => {
        const raw = values?.[definition.key];
        const text = Array.isArray(raw) ? raw.join(", ") : typeof raw === "boolean" ? (raw ? "Yes" : "No") : String(raw);
        return (
          <div key={definition.key}>
            <dt className="text-xs text-muted-foreground">{definition.label}</dt>
            <dd className="text-sm font-medium">{text}</dd>
          </div>
        );
      })}
    </dl>
  );
}
