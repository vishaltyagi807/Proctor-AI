import { useState } from "react";
import { Eye, EyeOff } from "lucide-react";
import { useApi } from "@/lib/query";
import { useSession } from "@/components/providers/session";
import { RELATION_LABEL, type ManageTarget } from "@/lib/permissions";
import { DEPARTMENTS_PATH, ROLES_PATH } from "@/lib/users";
import { Chips, Field, Input, Switch } from "@/components/ui/form";
import { Skeleton } from "@/components/ui/misc";
import { CustomFieldsForm, type CustomValues } from "@/components/custom-fields/custom-fields-form";
import type { Department, PageResponse, Role } from "@/lib/types";
import { Hint } from "@/components/ui/hint";

export type UserFormValues = {
  name: string;
  email: string;
  password: string;
  enabled: boolean;
  verified: boolean;
  roles: string[];
  departments: string[];
  customFields: CustomValues;
};

export function UserForm({ value, onChange, mode, target }: { value: UserFormValues; onChange: (value: UserFormValues) => void; mode: "create" | "edit"; target?: ManageTarget }) {
  const { manage } = useSession();
  const roles = useApi<PageResponse<Role>>(ROLES_PATH);
  const subject = target ?? { id: "", roles: [] };
  const canAdd = manage.rolesOf(subject, "write");
  const canRemove = manage.rolesOf(subject, "delete");
  const roleOptions = (roles.data?.content ?? []).map((role) => {
    const held = value.roles.includes(role.id);
    const allowed = held ? canRemove && manage.role(role, "delete") : canAdd && manage.role(role, "write");
    return {
      value: role.id,
      label: role.name.replaceAll("_", " "),
      disabled: !allowed,
      title: allowed
        ? undefined
        : subject.id === manage.selfId
          ? "You cannot change your own roles"
          : !manage.rolesOf(subject, held ? "delete" : "write")
            ? `${RELATION_LABEL[manage.relation(subject)]}: you cannot change their roles`
            : `${RELATION_LABEL[manage.roleRelation(role)]}: you cannot ${held ? "remove" : "assign"} this role`,
    };
  });
  const departments = useApi<PageResponse<Department>>(DEPARTMENTS_PATH);
  const [show, setShow] = useState(false);
  const set = <K extends keyof UserFormValues>(key: K, next: UserFormValues[K]) => onChange({ ...value, [key]: next });

  return (
    <div className="space-y-5">
      <div className="grid gap-4 sm:grid-cols-2">
        <Field label="Full name">
          <Input required value={value.name} onChange={(event) => set("name", event.target.value)} />
        </Field>
        <Field label="Email">
          <Input required type="email" value={value.email} onChange={(event) => set("email", event.target.value)} />
        </Field>
      </div>
      <Field label={mode === "create" ? "Password" : "New password"} hint={mode === "create" ? "At least 8 characters." : "Leave empty to keep the current password."}>
        <div className="relative">
          <Input type={show ? "text" : "password"} minLength={mode === "create" ? 8 : undefined} required={mode === "create"} autoComplete="new-password" value={value.password} onChange={(event) => set("password", event.target.value)} className="pr-11" />
          <Hint label="Toggle password visibility">
            <button type="button" onClick={() => setShow((current) => !current)} className="absolute right-3 top-1/2 -translate-y-1/2 text-muted-foreground" aria-label="Toggle password visibility">
              {show ? <EyeOff className="size-4" /> : <Eye className="size-4" />}
            </button>
          </Hint>
        </div>
      </Field>
      <Field label="Roles" hint="Access is the union of every assigned role.">
        {roles.isLoading ? <Skeleton className="h-8" /> : <Chips options={roleOptions} value={value.roles} onChange={(next) => set("roles", next)} empty="You cannot assign any roles" />}
      </Field>
      <Field label="Departments">
        {departments.isLoading ? <Skeleton className="h-8" /> : <Chips options={(departments.data?.content ?? []).map((department) => ({ value: department.id, label: department.name }))} value={value.departments} onChange={(next) => set("departments", next)} empty="No departments available" />}
      </Field>
      <div className="flex flex-wrap gap-8">
        <label className="flex items-center gap-3 text-sm">
          <Switch checked={value.enabled} onChange={(next) => set("enabled", next)} label="Enabled" /> Account enabled
        </label>
        <label className="flex items-center gap-3 text-sm">
          <Switch checked={value.verified} onChange={(next) => set("verified", next)} label="Verified" /> Verified
        </label>
      </div>
      <CustomFieldsForm entity="users" roleIds={value.roles} departmentIds={value.departments} value={value.customFields} onChange={(next) => set("customFields", next)} />
    </div>
  );
}

export const emptyUser: UserFormValues = { name: "", email: "", password: "", enabled: true, verified: true, roles: [], departments: [], customFields: {} };
