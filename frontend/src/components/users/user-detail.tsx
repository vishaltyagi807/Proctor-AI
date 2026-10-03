import { useState } from "react";
import { Link, useNavigate } from "@tanstack/react-router";
import { ArrowLeft, Building2, Save, ShieldCheck, Trash2, UserX } from "lucide-react";
import { api } from "@/lib/api";
import { useAction, useApi } from "@/lib/query";
import { formatDate } from "@/lib/format";
import { userInfoPath } from "@/lib/users";
import { useSession } from "@/components/providers/session";
import { useToast } from "@/components/ui/toast";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Avatar, EmptyState, Skeleton, Spinner } from "@/components/ui/misc";
import { Modal } from "@/components/ui/modal";
import { FadeIn } from "@/components/ui/motion";
import { RelationBadge, RelationNotice } from "@/components/access/relation";
import { UserForm, type UserFormValues } from "./user-form";
import type { PageResponse, Permission, UserInfo } from "@/lib/types";

export function UserDetail({ id }: { id: string }) {
  const { data, isLoading } = useApi<PageResponse<UserInfo>>(userInfoPath(id));
  const user = data?.content[0];
  if (isLoading) return <Skeleton className="h-96" />;
  if (!user) {
    return (
      <EmptyState
        icon={<UserX className="size-7" />}
        title="User not found"
        description="They may have been removed, or are outside your access."
        action={
          <Link to="/users">
            <Button variant="outline">Back to users</Button>
          </Link>
        }
      />
    );
  }
  return <Editor key={user.updatedAt ?? user.id} user={user} />;
}

function Editor({ user }: { user: UserInfo }) {
  const navigate = useNavigate();
  const toast = useToast();
  const { can, manage, user: me } = useSession();
  const [form, setForm] = useState<UserFormValues>({
    name: user.name,
    email: user.email,
    password: "",
    enabled: user.enabled,
    verified: user.verified,
    roles: user.roles.map((role) => role.id),
    departments: user.departments.map((department) => department.id),
    customFields: user.customFields ?? {},
  });
  const [confirmDelete, setConfirmDelete] = useState(false);
  const permissions = useApi<Permission[]>(`/users/${user.id}/permissions`);
  const manageable = manage.user(user, "update");
  const canUpdate = can("users", "update") && manageable;
  const canDelete = can("users", "delete") && user.id !== me.id && manage.user(user, "delete");
  const relation = manage.relation(user);

  const save = useAction(
    () =>
      api.put(`/users/${user.id}`, {
        name: form.name,
        email: form.email,
        password: form.password || undefined,
        enabled: form.enabled,
        verified: form.verified,
        roles: form.roles,
        departments: form.departments,
        customFields: form.customFields,
      }),
    {
      invalidate: ["/users"],
      onSuccess: () => {
        toast.success("Changes saved");
        setForm((current) => ({ ...current, password: "" }));
      },
      onError: (error) => toast.error("Could not save", error.message),
    },
  );
  const remove = useAction(() => api.delete(`/users/${user.id}`), {
    invalidate: ["/users"],
    onSuccess: () => {
      toast.success("User deleted");
      navigate({ to: "/users" });
    },
    onError: (error) => toast.error("Could not delete", error.message),
  });

  const grouped = Object.entries(
    (permissions.data ?? []).reduce<Record<string, string[]>>((acc, permission) => {
      (acc[permission.entity] ??= []).push(`${permission.action}:${permission.scope}`);
      return acc;
    }, {}),
  );

  return (
    <div className="space-y-6">
      <FadeIn>
        <Link to="/users" className="inline-flex items-center gap-1.5 text-sm text-muted-foreground transition hover:text-foreground">
          <ArrowLeft className="size-4" /> All users
        </Link>
        <div className="mt-4 flex flex-wrap items-center gap-5">
          <Avatar name={user.name} size={64} />
          <div className="min-w-0 flex-1">
            <h1 className="text-2xl font-semibold tracking-tight">{user.name}</h1>
            <p className="text-sm text-muted-foreground">
              {user.email} · joined {formatDate(user.createdAt)}
            </p>
            <div className="mt-2 flex flex-wrap gap-1.5">
              {user.roles.map((role) => (
                <Badge key={role.id} tone={role.superuser ? "violet" : "brand"} className="capitalize">
                  {role.name.replaceAll("_", " ")}
                </Badge>
              ))}
              <Badge tone={user.enabled ? "success" : "danger"} dot>
                {user.enabled ? "Active" : "Disabled"}
              </Badge>
              <RelationBadge relation={relation} />
            </div>
          </div>
          {canDelete && (
            <Button variant="destructive" className="h-10" onClick={() => setConfirmDelete(true)}>
              <Trash2 /> Delete
            </Button>
          )}
        </div>
      </FadeIn>

      {!manageable && can("users", "update") && <RelationNotice relation={relation} kind="person" />}

      <div className="grid gap-6 xl:grid-cols-[minmax(0,1fr)_380px]">
        <Card>
          <CardHeader>
            <div>
              <CardTitle>Account</CardTitle>
              <CardDescription>{canUpdate ? "Edit the details, access and custom fields." : "You have read-only access to this user."}</CardDescription>
            </div>
          </CardHeader>
          <CardContent className="space-y-6">
            <div className={canUpdate ? "" : "pointer-events-none opacity-70"}>
              <UserForm mode="edit" value={form} onChange={setForm} target={user} />
            </div>
            {canUpdate && (
              <div className="flex justify-end border-t pt-5">
                <Button className="h-10 gradient-brand px-5 text-white" disabled={save.isPending} onClick={() => save.mutate(undefined)}>
                  {save.isPending ? <Spinner /> : <Save />} Save changes
                </Button>
              </div>
            )}
          </CardContent>
        </Card>

        <div className="space-y-6">
          <Card>
            <CardHeader>
              <CardTitle>Departments</CardTitle>
            </CardHeader>
            <CardContent className="space-y-2">
              {user.departments.length ? (
                user.departments.map((department) => (
                  <div key={department.id} className="flex items-center gap-3 rounded-xl border p-3">
                    <Building2 className="size-4 text-muted-foreground" />
                    <span className="flex-1 text-sm font-medium">{department.name}</span>
                    <Badge>{department.code}</Badge>
                  </div>
                ))
              ) : (
                <p className="text-sm text-muted-foreground">Not part of any department.</p>
              )}
            </CardContent>
          </Card>

          <Card>
            <CardHeader>
              <div>
                <CardTitle>Effective permissions</CardTitle>
                <CardDescription>Combined from every role</CardDescription>
              </div>
              <ShieldCheck className="size-4 text-muted-foreground" />
            </CardHeader>
            <CardContent className="space-y-3">
              {permissions.isLoading ? (
                <Skeleton className="h-28" />
              ) : grouped.length ? (
                grouped.map(([entity, actions]) => (
                  <div key={entity}>
                    <p className="text-xs font-medium uppercase tracking-wide text-muted-foreground">{entity.replaceAll("_", " ")}</p>
                    <div className="mt-1 flex flex-wrap gap-1.5">
                      {actions.map((action) => (
                        <Badge key={action} tone="brand">
                          {action}
                        </Badge>
                      ))}
                    </div>
                  </div>
                ))
              ) : (
                <p className="text-sm text-muted-foreground">No permissions visible to you.</p>
              )}
            </CardContent>
          </Card>
        </div>
      </div>

      <Modal
        open={confirmDelete}
        onClose={() => setConfirmDelete(false)}
        title={`Delete ${user.name}?`}
        description="Their complaints, comments and sessions are removed."
        size="sm"
        footer={
          <>
            <Button variant="outline" onClick={() => setConfirmDelete(false)}>
              Cancel
            </Button>
            <Button variant="destructive" disabled={remove.isPending} onClick={() => remove.mutate(undefined)}>
              {remove.isPending && <Spinner />} Delete user
            </Button>
          </>
        }
      >
        <p className="text-sm text-muted-foreground">This cannot be undone.</p>
      </Modal>
    </div>
  );
}
