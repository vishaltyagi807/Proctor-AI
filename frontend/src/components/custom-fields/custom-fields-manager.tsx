import { useState } from "react";
import { AnimatePresence, motion } from "motion/react";
import { Pencil, Plus, SlidersHorizontal, Trash2, X } from "lucide-react";
import { api } from "@/lib/api";
import { useAction, useApi } from "@/lib/query";
import { customFieldsPath } from "@/lib/paths";
import { titleCase } from "@/lib/format";
import { DEPARTMENTS_PATH, ROLES_PATH } from "@/lib/users";
import { useSession } from "@/components/providers/session";
import { useToast } from "@/components/ui/toast";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { Chips, Field, Input, Select, Switch } from "@/components/ui/form";
import { EmptyState, PageHeader, Skeleton, Spinner, Tabs } from "@/components/ui/misc";
import { Modal } from "@/components/ui/modal";
import { Stagger, StaggerItem } from "@/components/ui/motion";
import type { CustomFieldDefinition, CustomFieldEntity, CustomFieldType, Department, PageResponse, Role } from "@/lib/types";
import { Hint } from "@/components/ui/hint";

const ENTITIES: { value: CustomFieldEntity; label: string; hint: string }[] = [
  { value: "users", label: "Users", hint: "Fields on accounts, optionally for one role or department" },
  { value: "complaints", label: "Complaints", hint: "Extra details on complaints, optionally per department" },
  { value: "departments", label: "Departments", hint: "Extra details on departments" },
  { value: "roles", label: "Roles", hint: "Extra details on roles" },
];
const TYPES: { value: CustomFieldType; label: string }[] = [
  { value: "text", label: "Text" },
  { value: "number", label: "Number" },
  { value: "bool", label: "Yes / No" },
  { value: "date", label: "Date" },
  { value: "select", label: "Single choice" },
  { value: "multi_select", label: "Multiple choice" },
];

const listPath = customFieldsPath;

type Draft = {
  id?: string;
  entity: CustomFieldEntity;
  key: string;
  label: string;
  helpText: string;
  dataType: CustomFieldType;
  required: boolean;
  options: string[];
  minValue: string;
  maxValue: string;
  maxLength: string;
  pattern: string;
  appliesToRoleId: string;
  appliesToDepartmentId: string;
  sortOrder: string;
  active: boolean;
};

const blank = (entity: CustomFieldEntity): Draft => ({ entity, key: "", label: "", helpText: "", dataType: "text", required: false, options: [], minValue: "", maxValue: "", maxLength: "", pattern: "", appliesToRoleId: "", appliesToDepartmentId: "", sortOrder: "0", active: true });

export function CustomFieldsManager() {
  const toast = useToast();
  const { can } = useSession();
  const [entity, setEntity] = useState<CustomFieldEntity>("users");
  const [draft, setDraft] = useState<Draft | null>(null);
  const [deleting, setDeleting] = useState<CustomFieldDefinition | null>(null);
  const { data, isLoading } = useApi<PageResponse<CustomFieldDefinition>>(listPath(entity));
  const roles = useApi<PageResponse<Role>>(ROLES_PATH);
  const departments = useApi<PageResponse<Department>>(DEPARTMENTS_PATH);

  const roleName = (id: string | null) => roles.data?.content.find((role) => role.id === id)?.name ?? "role";
  const departmentName = (id: string | null) => departments.data?.content.find((department) => department.id === id)?.name ?? "department";

  const save = useAction(
    (value: Draft) => {
      const body = {
        label: value.label,
        helpText: value.helpText || undefined,
        required: value.required,
        options: value.options.length ? value.options : undefined,
        minValue: value.minValue === "" ? undefined : Number(value.minValue),
        maxValue: value.maxValue === "" ? undefined : Number(value.maxValue),
        maxLength: value.maxLength === "" ? undefined : Number(value.maxLength),
        pattern: value.pattern || undefined,
        appliesToRoleId: value.appliesToRoleId || undefined,
        appliesToDepartmentId: value.appliesToDepartmentId || undefined,
        sortOrder: Number(value.sortOrder) || 0,
        active: value.active,
      };
      return value.id ? api.put(`/custom-fields/${value.id}`, body) : api.post("/custom-fields", { ...body, entity: value.entity, key: value.key, dataType: value.dataType });
    },
    {
      invalidate: ["/custom-fields"],
      onSuccess: () => {
        toast.success("Custom field saved");
        setDraft(null);
      },
      onError: (error) => toast.error("Could not save field", error.message),
    },
  );
  const remove = useAction((id: string) => api.delete(`/custom-fields/${id}`), {
    invalidate: ["/custom-fields"],
    onSuccess: () => {
      toast.success("Field deleted", "Its stored values were removed too.");
      setDeleting(null);
    },
    onError: (error) => toast.error("Could not delete field", error.message),
  });

  const edit = (definition: CustomFieldDefinition) =>
    setDraft({
      id: definition.id,
      entity: definition.entity,
      key: definition.key,
      label: definition.label,
      helpText: definition.helpText ?? "",
      dataType: definition.dataType,
      required: definition.required,
      options: definition.options ?? [],
      minValue: definition.minValue?.toString() ?? "",
      maxValue: definition.maxValue?.toString() ?? "",
      maxLength: definition.maxLength?.toString() ?? "",
      pattern: definition.pattern ?? "",
      appliesToRoleId: definition.appliesToRoleId ?? "",
      appliesToDepartmentId: definition.appliesToDepartmentId ?? "",
      sortOrder: String(definition.sortOrder),
      active: definition.active,
    });

  const active = ENTITIES.find((item) => item.value === entity)!;
  return (
    <div>
      <PageHeader
        title="Custom fields"
        description="Add your own fields to users, complaints, departments and roles. No code needed."
        icon={<SlidersHorizontal className="size-5" />}
        actions={
          can("custom_fields", "write") && (
            <Button className="h-10 gradient-brand text-white shadow-md shadow-primary/25" onClick={() => setDraft(blank(entity))}>
              <Plus /> New field
            </Button>
          )
        }
      />
      <div className="mb-5">
        <Tabs id="entity" tabs={ENTITIES.map((item) => ({ value: item.value, label: item.label }))} value={entity} onChange={setEntity} />
        <p className="mt-2 text-sm text-muted-foreground">{active.hint}</p>
      </div>

      {isLoading ? (
        <div className="grid gap-4 md:grid-cols-2">
          {[0, 1].map((index) => (
            <Skeleton key={index} className="h-32" />
          ))}
        </div>
      ) : data && data.content.length > 0 ? (
        <Stagger key={entity} className="grid gap-4 md:grid-cols-2">
          {data.content.map((definition) => (
            <StaggerItem key={definition.id}>
              <Card className={`p-5 ${definition.active ? "" : "opacity-60"}`}>
                <div className="flex items-start justify-between gap-3">
                  <div className="min-w-0">
                    <div className="flex flex-wrap items-center gap-2">
                      <h3 className="font-semibold">{definition.label}</h3>
                      {definition.required && <Badge tone="danger">Required</Badge>}
                      {!definition.active && <Badge>Inactive</Badge>}
                    </div>
                    <p className="mt-0.5 font-mono text-xs text-muted-foreground">{definition.key}</p>
                  </div>
                  <div className="flex gap-1">
                    {can("custom_fields", "update") && (
                      <Hint label="Edit field">
                        <button onClick={() => edit(definition)} className="rounded-lg p-2 text-muted-foreground hover:bg-muted hover:text-foreground" aria-label="Edit field">
                          <Pencil className="size-4" />
                        </button>
                      </Hint>
                    )}
                    {can("custom_fields", "delete") && (
                      <Hint label="Delete field">
                        <button onClick={() => setDeleting(definition)} className="rounded-lg p-2 text-muted-foreground hover:bg-destructive/10 hover:text-destructive" aria-label="Delete field">
                          <Trash2 className="size-4" />
                        </button>
                      </Hint>
                    )}
                  </div>
                </div>
                <div className="mt-3 flex flex-wrap gap-1.5">
                  <Badge tone="brand">{TYPES.find((type) => type.value === definition.dataType)?.label}</Badge>
                  {definition.appliesToRoleId && <Badge tone="violet">Role: {roleName(definition.appliesToRoleId)}</Badge>}
                  {definition.appliesToDepartmentId && <Badge tone="info">Dept: {departmentName(definition.appliesToDepartmentId)}</Badge>}
                  {!definition.appliesToRoleId && !definition.appliesToDepartmentId && <Badge>Applies to all</Badge>}
                  {definition.options?.slice(0, 4).map((option) => (
                    <Badge key={option}>{option}</Badge>
                  ))}
                </div>
                {definition.helpText && <p className="mt-3 text-sm text-muted-foreground">{definition.helpText}</p>}
              </Card>
            </StaggerItem>
          ))}
        </Stagger>
      ) : (
        <Card>
          <EmptyState icon={<SlidersHorizontal className="size-7" />} title={`No custom fields for ${active.label.toLowerCase()}`} description="Create one and it shows up in forms immediately." />
        </Card>
      )}

      <Modal
        open={draft !== null}
        onClose={() => setDraft(null)}
        title={draft?.id ? "Edit field" : "New custom field"}
        description={draft?.id ? "The key and type are locked once created." : undefined}
        size="lg"
        footer={
          <>
            <Button variant="outline" onClick={() => setDraft(null)}>
              Cancel
            </Button>
            <Button className="gradient-brand text-white" disabled={!draft || !draft.label.trim() || (!draft.id && !/^[a-z][a-z0-9_]{0,49}$/.test(draft.key)) || save.isPending} onClick={() => draft && save.mutate(draft)}>
              {save.isPending && <Spinner />} Save field
            </Button>
          </>
        }
      >
        {draft && <DraftForm draft={draft} onChange={setDraft} roles={roles.data?.content ?? []} departments={departments.data?.content ?? []} />}
      </Modal>

      <Modal
        open={deleting !== null}
        onClose={() => setDeleting(null)}
        title={`Delete "${deleting?.label}"?`}
        description="Every stored value of this field is deleted too."
        size="sm"
        footer={
          <>
            <Button variant="outline" onClick={() => setDeleting(null)}>
              Cancel
            </Button>
            <Button variant="destructive" disabled={remove.isPending} onClick={() => deleting && remove.mutate(deleting.id)}>
              {remove.isPending && <Spinner />} Delete
            </Button>
          </>
        }
      >
        <p className="text-sm text-muted-foreground">Consider deactivating the field instead to keep the data.</p>
      </Modal>
    </div>
  );
}

function DraftForm({ draft, onChange, roles, departments }: { draft: Draft; onChange: (draft: Draft) => void; roles: Role[]; departments: Department[] }) {
  const [option, setOption] = useState("");
  const set = <K extends keyof Draft>(key: K, value: Draft[K]) => onChange({ ...draft, [key]: value });
  const locked = Boolean(draft.id);
  const choice = draft.dataType === "select" || draft.dataType === "multi_select";
  const addOption = () => {
    const value = option.trim();
    if (value && !draft.options.includes(value)) set("options", [...draft.options, value]);
    setOption("");
  };
  return (
    <div className="space-y-5">
      <div className="grid gap-4 sm:grid-cols-2">
        <Field label="Label" hint="What people see on forms.">
          <Input value={draft.label} onChange={(event) => set("label", event.target.value)} onBlur={() => !locked && !draft.key && set("key", draft.label.toLowerCase().replace(/[^a-z0-9]+/g, "_").replace(/^_+|_+$/g, "").slice(0, 50))} />
        </Field>
        <Field label="Key" hint="Lower snake_case, used in the API and filters.">
          <Input value={draft.key} disabled={locked} onChange={(event) => set("key", event.target.value.toLowerCase())} className="font-mono" />
        </Field>
      </div>
      <div className="grid gap-4 sm:grid-cols-2">
        <Field label="Type">
          <Select value={draft.dataType} disabled={locked} onValueChange={(value) => set("dataType", value as CustomFieldType)} options={TYPES} />
        </Field>
        <Field label="Order">
          <Input type="number" value={draft.sortOrder} onChange={(event) => set("sortOrder", event.target.value)} />
        </Field>
      </div>
      <Field label="Help text">
        <Input value={draft.helpText} onChange={(event) => set("helpText", event.target.value)} />
      </Field>

      <AnimatePresence initial={false}>
        {choice && (
          <motion.div initial={{ opacity: 0, height: 0 }} animate={{ opacity: 1, height: "auto" }} exit={{ opacity: 0, height: 0 }} className="overflow-hidden">
            <Field label="Options">
              <div className="space-y-2">
                <div className="flex gap-2">
                  <Input value={option} onChange={(event) => setOption(event.target.value)} onKeyDown={(event) => event.key === "Enter" && (event.preventDefault(), addOption())} placeholder="Add an option and press Enter" />
                  <Button type="button" variant="outline" className="h-10" onClick={addOption}>
                    Add
                  </Button>
                </div>
                <div className="flex flex-wrap gap-2">
                  {draft.options.map((item) => (
                    <span key={item} className="inline-flex items-center gap-1.5 rounded-full border bg-muted/60 py-1 pl-3 pr-1.5 text-xs font-medium">
                      {item}
                      <Hint label={`Remove ${item}`}>
                        <button type="button" onClick={() => set("options", draft.options.filter((current) => current !== item))} className="rounded-full p-0.5 hover:bg-background" aria-label={`Remove ${item}`}>
                          <X className="size-3" />
                        </button>
                      </Hint>
                    </span>
                  ))}
                </div>
              </div>
            </Field>
          </motion.div>
        )}
        {draft.dataType === "number" && (
          <motion.div initial={{ opacity: 0, height: 0 }} animate={{ opacity: 1, height: "auto" }} exit={{ opacity: 0, height: 0 }} className="grid gap-4 overflow-hidden sm:grid-cols-2">
            <Field label="Minimum">
              <Input type="number" value={draft.minValue} onChange={(event) => set("minValue", event.target.value)} />
            </Field>
            <Field label="Maximum">
              <Input type="number" value={draft.maxValue} onChange={(event) => set("maxValue", event.target.value)} />
            </Field>
          </motion.div>
        )}
        {draft.dataType === "text" && (
          <motion.div initial={{ opacity: 0, height: 0 }} animate={{ opacity: 1, height: "auto" }} exit={{ opacity: 0, height: 0 }} className="grid gap-4 overflow-hidden sm:grid-cols-2">
            <Field label="Max length">
              <Input type="number" value={draft.maxLength} onChange={(event) => set("maxLength", event.target.value)} />
            </Field>
            <Field label="Format (regex)" hint="e.g. ^[A-Z]{2}[0-9]{4}$">
              <Input value={draft.pattern} onChange={(event) => set("pattern", event.target.value)} className="font-mono" />
            </Field>
          </motion.div>
        )}
      </AnimatePresence>

      {(draft.entity === "users" || draft.entity === "complaints") && (
        <Field label="Only for these roles" hint={draft.entity === "users" ? "Leave empty to apply to everyone." : "The role of the person who raised the complaint."}>
          <Chips multiple={false} options={roles.map((role) => ({ value: role.id, label: role.name.replaceAll("_", " ") }))} value={draft.appliesToRoleId ? [draft.appliesToRoleId] : []} onChange={(value) => set("appliesToRoleId", value[0] ?? "")} />
        </Field>
      )}
      {draft.entity !== "roles" && (
        <Field label="Only for this department" hint="Leave empty to apply to all departments.">
          <Chips multiple={false} options={departments.map((department) => ({ value: department.id, label: department.name }))} value={draft.appliesToDepartmentId ? [draft.appliesToDepartmentId] : []} onChange={(value) => set("appliesToDepartmentId", value[0] ?? "")} />
        </Field>
      )}
      <div className="flex flex-wrap gap-8">
        <label className="flex items-center gap-3 text-sm">
          <Switch checked={draft.required} onChange={(value) => set("required", value)} label="Required" /> Required
        </label>
        <label className="flex items-center gap-3 text-sm">
          <Switch checked={draft.active} onChange={(value) => set("active", value)} label="Active" /> Active
        </label>
      </div>
      <p className="text-xs text-muted-foreground">{titleCase(draft.entity)} field · values are validated on the server for every write.</p>
    </div>
  );
}
