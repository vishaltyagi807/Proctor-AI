import { useState } from "react";
import { useNavigate } from "@tanstack/react-router";
import { useQueryClient } from "@tanstack/react-query";
import { Building2, LogOut, Monitor, Moon, ShieldCheck, Sun } from "lucide-react";
import { api } from "@/lib/api";
import { signOut } from "@/lib/session";
import { useAction, useApi } from "@/lib/query";
import { formatDate } from "@/lib/format";
import { useSession } from "@/components/providers/session";
import { useTheme } from "@/components/providers/theme";
import { useToast } from "@/components/ui/toast";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Avatar, PageHeader, Spinner } from "@/components/ui/misc";
import { Stagger, StaggerItem } from "@/components/ui/motion";
import { CustomFieldsForm, type CustomValues } from "@/components/custom-fields/custom-fields-form";
import { titleCase } from "@/lib/format";
import type { Department, User } from "@/lib/types";

export function ProfileView() {
  const navigate = useNavigate();
  const client = useQueryClient();
  const toast = useToast();
  const { session, user, role } = useSession();
  const { mode, setMode } = useTheme();
  const me = useApi<User>("/users/me");
  const departments = useApi<Department[]>("/users/me/departments");
  const [custom, setCustom] = useState<CustomValues | null>(null);
  const [expanded, setExpanded] = useState<string | null>(null);
  const [signingOut, setSigningOut] = useState(false);

  const values = custom ?? me.data?.customFields ?? {};
  const save = useAction(() => api.patch(`/users/${user.id}`, { customFields: values }), {
    invalidate: ["/users"],
    onSuccess: () => toast.success("Profile updated"),
    onError: (e) => toast.error("Could not save", e.message),
  });

  async function signOutAll() {
    setSigningOut(true);
    await signOut(client, true);
    await navigate({ to: "/login", replace: true });
    client.clear();
  }

  const themes = [
    { value: "light" as const, label: "Light", icon: Sun },
    { value: "dark" as const, label: "Dark", icon: Moon },
    { value: "system" as const, label: "System", icon: Monitor },
  ];

  return (
    <div className="mx-auto max-w-5xl">
      <PageHeader title="My profile" description="Your account, access and preferences." icon={<ShieldCheck className="size-5" />} />
      <Stagger className="grid gap-6 lg:grid-cols-[340px_minmax(0,1fr)]">
        <div className="space-y-6">
          <StaggerItem>
            <Card className="overflow-hidden">
              <div className="h-24 gradient-brand-animated" />
              <CardContent className="-mt-10 flex flex-col items-center pb-6 text-center">
                <Avatar name={user.name} size={80} className="ring-4 ring-card" />
                <h2 className="mt-3 text-lg font-semibold">{user.name}</h2>
                <p className="text-sm text-muted-foreground">{user.email}</p>
                <div className="mt-3 flex flex-wrap justify-center gap-2">
                  <Badge tone="brand" className="capitalize">{role}</Badge>
                  {user.verified && <Badge tone="success">Verified</Badge>}
                </div>
                <p className="mt-4 text-xs text-muted-foreground">Member since {formatDate(user.createdAt)}</p>
              </CardContent>
            </Card>
          </StaggerItem>

          <StaggerItem>
            <Card>
              <CardHeader>
                <CardTitle>Appearance</CardTitle>
              </CardHeader>
              <CardContent>
                <div className="grid grid-cols-3 gap-2">
                  {themes.map((theme) => (
                    <button key={theme.value} onClick={() => setMode(theme.value)} className={`flex flex-col items-center gap-1.5 rounded-xl border p-3 text-sm transition ${mode === theme.value ? "border-primary bg-primary/10 text-primary" : "hover:border-primary/40"}`}>
                      <theme.icon className="size-5" />
                      {theme.label}
                    </button>
                  ))}
                </div>
              </CardContent>
            </Card>
          </StaggerItem>

          <StaggerItem>
            <Card>
              <CardHeader>
                <div>
                  <CardTitle>Security</CardTitle>
                  <CardDescription>Sign out everywhere if a device is lost.</CardDescription>
                </div>
              </CardHeader>
              <CardContent>
                <Button variant="destructive" className="h-10 w-full" onClick={signOutAll} disabled={signingOut}>
                  {signingOut ? <Spinner /> : <LogOut />} Sign out of all devices
                </Button>
              </CardContent>
            </Card>
          </StaggerItem>
        </div>

        <div className="space-y-6">
          <StaggerItem>
            <Card>
              <CardHeader>
                <div>
                  <CardTitle>Roles &amp; permissions</CardTitle>
                  <CardDescription>What you can do, granted by your roles</CardDescription>
                </div>
              </CardHeader>
              <CardContent className="space-y-3">
                {session.roles.map((item) => {
                  const grouped = Object.entries(
                    item.permissions.reduce<Record<string, string[]>>((acc, permission) => {
                      (acc[permission.entity] ??= []).push(`${permission.action}:${permission.scope}`);
                      return acc;
                    }, {}),
                  );
                  const open = expanded === item.id;
                  return (
                    <div key={item.id} className="rounded-2xl border">
                      <button onClick={() => setExpanded(open ? null : item.id)} className="flex w-full items-center gap-3 p-4 text-left">
                        <span className="grid size-10 place-items-center rounded-xl gradient-brand text-white">
                          <ShieldCheck className="size-5" />
                        </span>
                        <span className="flex-1">
                          <span className="block font-medium capitalize">{item.name.replaceAll("_", " ")}</span>
                          <span className="block text-xs text-muted-foreground">{item.description ?? "No description"}</span>
                        </span>
                        {item.superuser ? <Badge tone="violet">Full access</Badge> : <Badge>{item.permissions.length} permissions</Badge>}
                        <Badge tone="neutral">level {item.level}</Badge>
                      </button>
                      {open && (
                        <div className="grid gap-x-6 gap-y-3 border-t p-4 sm:grid-cols-2">
                          {item.superuser ? (
                            <p className="text-sm text-muted-foreground sm:col-span-2">This role can do anything in the system.</p>
                          ) : (
                            grouped.map(([entity, actions]) => (
                              <div key={entity}>
                                <p className="text-xs font-medium uppercase tracking-wide text-muted-foreground">{titleCase(entity)}</p>
                                <div className="mt-1 flex flex-wrap gap-1.5">
                                  {actions.map((action) => (
                                    <Badge key={action} tone="brand">
                                      {action}
                                    </Badge>
                                  ))}
                                </div>
                              </div>
                            ))
                          )}
                        </div>
                      )}
                    </div>
                  );
                })}
              </CardContent>
            </Card>
          </StaggerItem>

          <StaggerItem>
            <Card>
              <CardHeader>
                <CardTitle>Departments</CardTitle>
              </CardHeader>
              <CardContent className="flex flex-wrap gap-2">
                {departments.data && departments.data.length > 0 ? (
                  departments.data.map((department) => (
                    <span key={department.id} className="inline-flex items-center gap-2 rounded-xl border px-3 py-2 text-sm">
                      <Building2 className="size-4 text-muted-foreground" />
                      {department.name}
                      <Badge>{department.code}</Badge>
                    </span>
                  ))
                ) : (
                  <p className="text-sm text-muted-foreground">You are not part of any department yet.</p>
                )}
              </CardContent>
            </Card>
          </StaggerItem>

          <StaggerItem>
            <Card>
              <CardHeader>
                <div>
                  <CardTitle>Additional details</CardTitle>
                  <CardDescription>Fields defined by your administrators for your role</CardDescription>
                </div>
              </CardHeader>
              <CardContent className="space-y-5">
                <CustomFieldsForm entity="users" roleIds={session.roles.map((item) => item.id)} departmentIds={departments.data?.map((item) => item.id) ?? []} value={values} onChange={setCustom} />
                <Button className="h-10 gradient-brand text-white" disabled={custom === null || save.isPending} onClick={() => save.mutate(undefined)}>
                  {save.isPending && <Spinner />} Save changes
                </Button>
              </CardContent>
            </Card>
          </StaggerItem>
        </div>
      </Stagger>
    </div>
  );
}
