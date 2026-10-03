import { useMemo, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { Link, useNavigate } from "@tanstack/react-router";
import { ArrowLeft, Lock, RotateCcw, Save, ShieldCheck, Sparkles, Trash2 } from "lucide-react";
import { api } from "@/lib/api";
import { useAction, useApi } from "@/lib/query";
import { catalogQuery } from "@/lib/catalog";
import { useSession } from "@/components/providers/session";
import { useToast } from "@/components/ui/toast";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Field, Input, Switch, Textarea } from "@/components/ui/form";
import { EmptyState, Skeleton, Spinner } from "@/components/ui/misc";
import { Modal } from "@/components/ui/modal";
import { FadeIn } from "@/components/ui/motion";
import { RelationBadge, RelationNotice } from "@/components/access/relation";
import { CustomFieldsForm, type CustomValues } from "@/components/custom-fields/custom-fields-form";
import { PermissionMatrix } from "./permission-matrix";
import type { Permission, RoleWithPermissions } from "@/lib/types";

export function RoleDetail({ id }: { id: string }) {
  const { data: role, isLoading } = useApi<RoleWithPermissions>(`/roles/${id}`);
  const catalog = useQuery(catalogQuery);
  if (isLoading || catalog.isLoading) return <Skeleton className="h-96" />;
  if (!role || !catalog.data) {
    return (
      <EmptyState
        icon={<ShieldCheck className="size-7" />}
        title="Role not found"
        action={
          <Link to="/roles">
            <Button variant="outline">Back to roles</Button>
          </Link>
        }
      />
    );
  }
  return <Editor key={`${role.id}-${role.permissions.length}`} role={role} catalog={catalog.data} />;
}

function Editor({ role, catalog }: { role: RoleWithPermissions; catalog: Permission[] }) {
  const navigate = useNavigate();
  const toast = useToast();
  const { can, manage } = useSession();
  const original = useMemo(() => new Set(role.permissions.map((permission) => permission.id)), [role]);
  const [selected, setSelected] = useState<Set<string>>(new Set(original));
  const [name, setName] = useState(role.name);
  const [description, setDescription] = useState(role.description ?? "");
  const [level, setLevel] = useState(role.level);
  const [reveal, setReveal] = useState(role.revealIdentity);
  const [custom, setCustom] = useState<CustomValues>(role.customFields ?? {});
  const [confirmDelete, setConfirmDelete] = useState(false);

  const manageable = manage.role(role, "update");
  const editable = can("roles", "update") && manageable;
  const canGrant = can("role_permission", "write") && !role.superuser && manageable;
  const canDelete = can("roles", "delete") && !role.system && manage.role(role, "delete");
  const minLevel = manage.minLevelFor(role);
  const relation = manage.roleRelation(role);
  const changes = useMemo(() => {
    let count = 0;
    selected.forEach((id) => !original.has(id) && count++);
    original.forEach((id) => !selected.has(id) && count++);
    return count;
  }, [selected, original]);

  const saveMeta = useAction(() => api.patch(`/roles/${role.id}`, { name: role.system ? undefined : name, description, level: role.system ? undefined : level, revealIdentity: reveal, customFields: custom }), {
    invalidate: ["/roles"],
    onSuccess: () => toast.success("Role details saved"),
    onError: (error) => toast.error("Could not save", error.message),
  });
  const savePermissions = useAction(() => api.put(`/roles/${role.id}/permissions`, Array.from(selected)), {
    invalidate: ["/roles", "/users"],
    onSuccess: () => toast.success("Permissions updated", "Everyone with this role is affected immediately."),
    onError: (error) => toast.error("Could not update permissions", error.message),
  });
  const remove = useAction(() => api.delete(`/roles/${role.id}`), {
    invalidate: ["/roles", "/users"],
    onSuccess: () => {
      toast.success("Role deleted");
      navigate({ to: "/roles" });
    },
    onError: (error) => toast.error("Could not delete role", error.message),
  });

  return (
    <div className="space-y-6">
      <FadeIn>
        <Link to="/roles" className="inline-flex items-center gap-1.5 text-sm text-muted-foreground transition hover:text-foreground">
          <ArrowLeft className="size-4" /> All roles
        </Link>
        <div className="mt-4 flex flex-wrap items-center gap-4">
          <span className="grid size-14 place-items-center rounded-2xl gradient-brand text-white shadow-lg shadow-primary/25">{role.superuser ? <Sparkles className="size-6" /> : <ShieldCheck className="size-6" />}</span>
          <div className="flex-1">
            <h1 className="text-2xl font-semibold capitalize tracking-tight">{role.name.replaceAll("_", " ")}</h1>
            <div className="mt-1.5 flex flex-wrap gap-1.5">
              <Badge tone="brand">Level {role.level}</Badge>
              {role.system && (
                <Badge>
                  <Lock className="size-3" /> System role
                </Badge>
              )}
              {role.superuser && <Badge tone="violet">Full access</Badge>}
              <RelationBadge relation={relation} />
            </div>
          </div>
          {canDelete && (
            <Button variant="destructive" className="h-10" onClick={() => setConfirmDelete(true)}>
              <Trash2 /> Delete role
            </Button>
          )}
        </div>
      </FadeIn>

      {!manageable && can("roles", "update") && <RelationNotice relation={relation} kind="role" />}

      <div className="grid gap-6 xl:grid-cols-[360px_minmax(0,1fr)]">
        <Card className="self-start">
          <CardHeader>
            <div>
              <CardTitle>Details</CardTitle>
              <CardDescription>{role.system ? "System roles keep their name and level." : "Name, level and visibility."}</CardDescription>
            </div>
          </CardHeader>
          <CardContent className="space-y-4">
            <Field label="Name">
              <Input value={name} onChange={(event) => setName(event.target.value)} disabled={role.system || !editable} />
            </Field>
            <Field label="Description">
              <Textarea rows={2} value={description} onChange={(event) => setDescription(event.target.value)} disabled={!editable} />
            </Field>
            <Field label={`Level: ${level}`} hint={`Lower is more privileged. You can set ${minLevel} or higher.`}>
              <input type="range" min={Math.min(minLevel, level)} max={200} value={level} onChange={(event) => setLevel(Number(event.target.value))} disabled={role.system || !editable} className="w-full accent-[var(--brand)]" />
            </Field>
            <label className="flex items-start gap-3 rounded-xl border p-3.5 text-sm">
              <Switch checked={reveal} onChange={setReveal} disabled={!editable} label="Reveal identity" />
              <span>
                <span className="block font-medium">Reveal reporter identity</span>
                <span className="block text-xs text-muted-foreground">Complaints raised by members of this role show who raised them to the student concerned.</span>
              </span>
            </label>
            <CustomFieldsForm entity="roles" roleIds={[role.id]} value={custom} onChange={setCustom} />
            {editable && (
              <Button className="h-10 w-full gradient-brand text-white" disabled={saveMeta.isPending} onClick={() => saveMeta.mutate(undefined)}>
                {saveMeta.isPending ? <Spinner /> : <Save />} Save details
              </Button>
            )}
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <div>
              <CardTitle>Permissions</CardTitle>
              <CardDescription>{role.superuser ? "This role bypasses permission checks entirely." : "Pick the widest scope each action is allowed at: own records, the department, or everything."}</CardDescription>
            </div>
            {canGrant && (
              <div className="flex items-center gap-2">
                {changes > 0 && <Badge tone="warning">{changes} unsaved</Badge>}
                <Button variant="outline" size="sm" disabled={changes === 0} onClick={() => setSelected(new Set(original))}>
                  <RotateCcw /> Reset
                </Button>
                <Button size="sm" className="gradient-brand text-white" disabled={changes === 0 || savePermissions.isPending} onClick={() => savePermissions.mutate(undefined)}>
                  {savePermissions.isPending ? <Spinner /> : <Save />} Save
                </Button>
              </div>
            )}
          </CardHeader>
          <CardContent>
            {role.superuser ? (
              <EmptyState icon={<Sparkles className="size-7" />} title="Unrestricted" description="System administrators can do everything, so there is nothing to configure." />
            ) : (
              <PermissionMatrix catalog={catalog} selected={selected} onChange={setSelected} readOnly={!canGrant} />
            )}
          </CardContent>
        </Card>
      </div>

      <Modal
        open={confirmDelete}
        onClose={() => setConfirmDelete(false)}
        title={`Delete ${role.name}?`}
        description="Members lose the access this role gave them."
        size="sm"
        footer={
          <>
            <Button variant="outline" onClick={() => setConfirmDelete(false)}>
              Cancel
            </Button>
            <Button variant="destructive" disabled={remove.isPending} onClick={() => remove.mutate(undefined)}>
              {remove.isPending && <Spinner />} Delete
            </Button>
          </>
        }
      >
        <p className="text-sm text-muted-foreground">This cannot be undone.</p>
      </Modal>
    </div>
  );
}
