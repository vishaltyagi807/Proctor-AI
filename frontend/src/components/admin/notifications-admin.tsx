import { useState } from "react";
import { motion } from "motion/react";
import { Activity, BellRing, CheckCircle2, KeyRound, Megaphone, Send, ShieldAlert, Smartphone, Trash2, Zap } from "lucide-react";
import { api } from "@/lib/api";
import { useAction, useApi } from "@/lib/query";
import { ADMIN_PATHS } from "@/lib/paths";
import { formatDate, timeAgo } from "@/lib/format";
import { DEPARTMENTS_PATH, ROLES_PATH } from "@/lib/users";
import { useSession } from "@/components/providers/session";
import { useToast } from "@/components/ui/toast";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Chips, Field, Input, Select, Switch, Textarea } from "@/components/ui/form";
import { EmptyState, PageHeader, Pagination, Skeleton, Spinner, Table, Tabs, Td, Th } from "@/components/ui/misc";
import { AnimatedNumber, Stagger, StaggerItem } from "@/components/ui/motion";
import type { Delivery, DeliveryStats, Department, Integration, PageResponse, Role, UserInfo } from "@/lib/types";
import { PeoplePicker } from "@/components/users/person-picker";

type Tab = "send" | "integration" | "deliveries";

export function NotificationsAdmin() {
  const { can } = useSession();
  const canManage = can("integrations", "read");
  const canSend = can("notifications", "write", ["all", "department"]);
  const [tab, setTab] = useState<Tab>(canSend ? "send" : "integration");
  const tabs = [
    ...(canSend ? [{ value: "send" as const, label: "Send message" }] : []),
    ...(canManage ? [{ value: "integration" as const, label: "Push integration" }, { value: "deliveries" as const, label: "Delivery log" }] : []),
  ];
  return (
    <div>
      <PageHeader title="Broadcast & push" description="Message people by role, department or individually, and manage the push integration." icon={<Megaphone className="size-5" />} />
      <div className="mb-6">
        <Tabs id="admin-notifications" tabs={tabs} value={tab} onChange={setTab} />
      </div>
      <motion.div key={tab} initial={{ opacity: 0, y: 10 }} animate={{ opacity: 1, y: 0 }} transition={{ duration: 0.3 }}>
        {tab === "send" && <SendPanel />}
        {tab === "integration" && <IntegrationPanel />}
        {tab === "deliveries" && <DeliveriesPanel />}
      </motion.div>
    </div>
  );
}

function SendPanel() {
  const toast = useToast();
  const { can } = useSession();
  const roles = useApi<PageResponse<Role>>(can("roles", "read") ? ROLES_PATH : null);
  const departments = useApi<PageResponse<Department>>(DEPARTMENTS_PATH);
  const people = useApi<PageResponse<UserInfo>>(ADMIN_PATHS.people);
  const [roleIds, setRoleIds] = useState<string[]>([]);
  const [departmentIds, setDepartmentIds] = useState<string[]>([]);
  const [userIds, setUserIds] = useState<string[]>([]);
  const [type, setType] = useState("announcement");
  const [title, setTitle] = useState("");
  const [body, setBody] = useState("");
  const [priority, setPriority] = useState("normal");
  const [includeSender, setIncludeSender] = useState(false);

  const nothing = roleIds.length + departmentIds.length + userIds.length === 0;
  const send = useAction(() => api.post<{ message: string; recipients: number }>("/notifications/send", { roleIds, departmentIds, userIds, type, title, body: body || undefined, priority, includeSender }), {
    invalidate: ["/notifications"],
    onSuccess: (result) => {
      toast.success("Message queued", `Delivering to ${result.recipients} ${result.recipients === 1 ? "person" : "people"}.`);
      setTitle("");
      setBody("");
    },
    onError: (error) => toast.error("Could not send", error.message),
  });

  return (
    <div className="grid gap-6 xl:grid-cols-[minmax(0,1fr)_360px]">
      <Card>
        <CardHeader>
          <div>
            <CardTitle>Compose</CardTitle>
            <CardDescription>Recipients are combined and de-duplicated. Delivery is realtime plus push when enabled.</CardDescription>
          </div>
        </CardHeader>
        <CardContent className="space-y-5">
          {roles.data && (
            <Field label="Roles">
              <Chips options={roles.data.content.map((role) => ({ value: role.id, label: role.name.replaceAll("_", " ") }))} value={roleIds} onChange={setRoleIds} />
            </Field>
          )}
          <Field label="Departments">
            {departments.isLoading ? <Skeleton className="h-8" /> : <Chips options={(departments.data?.content ?? []).map((department) => ({ value: department.id, label: department.name }))} value={departmentIds} onChange={setDepartmentIds} empty="No departments" />}
          </Field>
          <Field label="Individual people">
            <PeoplePicker people={people.data?.content ?? []} value={userIds} onChange={setUserIds} aria-label="Individual people" />
          </Field>
          <div className="grid gap-4 sm:grid-cols-2">
            <Field label="Type" hint="Users can mute types they do not want.">
              <Input value={type} onChange={(event) => setType(event.target.value)} />
            </Field>
            <Field label="Priority">
              <Select
                value={priority}
                onValueChange={setPriority}
                options={[
                  { value: "low", label: "Low" },
                  { value: "normal", label: "Normal" },
                  { value: "high", label: "High" },
                ]}
              />
            </Field>
          </div>
          <Field label="Title">
            <Input value={title} maxLength={200} onChange={(event) => setTitle(event.target.value)} placeholder="What is this about?" />
          </Field>
          <Field label="Message">
            <Textarea rows={4} value={body} maxLength={2000} onChange={(event) => setBody(event.target.value)} />
          </Field>
          <div className="flex flex-wrap items-center justify-between gap-4 border-t pt-5">
            <label className="flex items-center gap-3 text-sm">
              <Switch checked={includeSender} onChange={setIncludeSender} label="Include me" /> Include me if I am in the audience
            </label>
            <Button className="h-11 gradient-brand px-6 text-white shadow-lg shadow-primary/25" disabled={nothing || !title.trim() || !type.trim() || send.isPending} onClick={() => send.mutate(undefined)}>
              {send.isPending ? <Spinner /> : <Send />} Send message
            </Button>
          </div>
        </CardContent>
      </Card>

      <Card className="self-start">
        <CardHeader>
          <CardTitle>Preview</CardTitle>
        </CardHeader>
        <CardContent>
          <motion.div layout className="rounded-2xl border bg-muted/40 p-4">
            <div className="flex items-start gap-3">
              <span className="grid size-10 shrink-0 place-items-center rounded-xl gradient-brand text-white">
                <BellRing className="size-5" />
              </span>
              <div className="min-w-0">
                <p className="text-sm font-semibold">{title || "Your title appears here"}</p>
                <p className="mt-0.5 text-sm text-muted-foreground">{body || "The message body shows below the title."}</p>
                <p className="mt-2 text-[11px] text-muted-foreground/80">just now · {type || "type"}</p>
              </div>
            </div>
          </motion.div>
          <p className="mt-4 text-xs text-muted-foreground">
            {can("notifications", "write", ["all"]) ? "You can reach anyone." : "You can only reach people in your own departments."}
          </p>
        </CardContent>
      </Card>
    </div>
  );
}

function IntegrationPanel() {
  const toast = useToast();
  const { data, isLoading } = useApi<Integration[]>(ADMIN_PATHS.integrations);
  const fcm = data?.find((item) => item.provider === "fcm");
  const [enabled, setEnabled] = useState<boolean | null>(null);
  const [json, setJson] = useState("");
  const effective = enabled ?? fcm?.enabled ?? false;

  const save = useAction(() => api.put<Integration>("/notifications/admin/integrations/fcm", { enabled: effective, serviceAccountJson: json.trim() || undefined }), {
    invalidate: ["/notifications/admin"],
    onSuccess: () => {
      toast.success("Integration saved", "Changes apply within 30 seconds.");
      setJson("");
      setEnabled(null);
    },
    onError: (error) => toast.error("Could not save", error.message),
  });
  const test = useAction(() => api.post<{ credentialsValid: boolean; devicesTried: number; sent: number; failed: number; detail: string }>("/notifications/admin/integrations/fcm/test", {}), {
    onSuccess: (result) => (result.credentialsValid ? toast.success("Credentials are valid", `${result.sent} of ${result.devicesTried} devices reached. ${result.detail}`) : toast.error("Google rejected the credentials", result.detail)),
    onError: (error) => toast.error("Test failed", error.message),
  });
  const clear = useAction(() => api.delete("/notifications/admin/integrations/fcm"), {
    invalidate: ["/notifications/admin"],
    onSuccess: () => toast.success("Credentials removed", "Push is disabled."),
    onError: (error) => toast.error("Could not remove", error.message),
  });

  if (isLoading) return <Skeleton className="h-96" />;
  return (
    <div className="grid gap-6 xl:grid-cols-[minmax(0,1fr)_360px]">
      <Card>
        <CardHeader>
          <div>
            <CardTitle>Firebase Cloud Messaging</CardTitle>
            <CardDescription>Mobile and web push notifications</CardDescription>
          </div>
          <div className="flex gap-2">
            <Badge tone={fcm?.enabled ? "success" : "neutral"} dot>
              {fcm?.enabled ? "Enabled" : "Disabled"}
            </Badge>
            <Badge tone={fcm?.configured ? "success" : "warning"}>{fcm?.configured ? "Credentials saved" : "No credentials"}</Badge>
          </div>
        </CardHeader>
        <CardContent className="space-y-5">
          {fcm?.configured && (
            <div className="rounded-2xl border bg-muted/40 p-4 text-sm">
              <p>
                <span className="text-muted-foreground">Project:</span> <span className="font-medium">{fcm.config.project_id}</span>
              </p>
              <p className="mt-1 break-all">
                <span className="text-muted-foreground">Service account:</span> <span className="font-medium">{fcm.config.client_email}</span>
              </p>
              {fcm.updatedAt && <p className="mt-1 text-xs text-muted-foreground">Updated {formatDate(fcm.updatedAt, true)}</p>}
            </div>
          )}
          <label className="flex items-center justify-between gap-3 rounded-2xl border p-4">
            <span>
              <span className="block text-sm font-medium">Send push notifications</span>
              <span className="block text-xs text-muted-foreground">Realtime in-app messages keep working either way.</span>
            </span>
            <Switch checked={effective} onChange={setEnabled} label="Push enabled" />
          </label>
          <Field label="Service account key (JSON)" hint="Write-only. Stored encrypted and never shown again. Leave empty to keep the saved key.">
            <Textarea rows={8} value={json} onChange={(event) => setJson(event.target.value)} placeholder='{ "type": "service_account", "project_id": "…", … }' spellCheck={false} autoComplete="off" className="font-mono text-xs" />
          </Field>
          <div className="flex flex-wrap gap-3">
            <Button className="h-10 gradient-brand text-white" disabled={save.isPending || (!json.trim() && enabled === null)} onClick={() => save.mutate(undefined)}>
              {save.isPending ? <Spinner /> : <KeyRound />} Save integration
            </Button>
            <Button variant="outline" className="h-10" disabled={!fcm?.configured || test.isPending} onClick={() => test.mutate(undefined)}>
              {test.isPending ? <Spinner /> : <Zap />} Verify &amp; send test push
            </Button>
            {fcm?.configured && (
              <Button variant="destructive" className="ml-auto h-10" disabled={clear.isPending} onClick={() => clear.mutate(undefined)}>
                <Trash2 /> Remove credentials
              </Button>
            )}
          </div>
        </CardContent>
      </Card>

      <Card className="self-start">
        <CardHeader>
          <CardTitle>How to connect</CardTitle>
        </CardHeader>
        <CardContent>
          <ol className="space-y-4 text-sm">
            {["Open the Firebase console for your project.", "Project settings → Service accounts → Generate new private key.", "Paste the downloaded JSON here and save.", "Register devices from the mobile app or browser, then run the test."].map((step, index) => (
              <li key={step} className="flex gap-3">
                <span className="grid size-6 shrink-0 place-items-center rounded-full gradient-brand text-xs font-semibold text-white">{index + 1}</span>
                <span className="text-muted-foreground">{step}</span>
              </li>
            ))}
          </ol>
          <div className="mt-5 flex gap-3 rounded-xl bg-warning/10 p-3.5 text-xs text-muted-foreground">
            <ShieldAlert className="mt-0.5 size-4 shrink-0 text-warning" />
            <span>The key can send push messages for your Firebase project. Only administrators can see or replace it, and it is encrypted at rest.</span>
          </div>
        </CardContent>
      </Card>
    </div>
  );
}

function DeliveriesPanel() {
  const [page, setPage] = useState(0);
  const stats = useApi<DeliveryStats>(ADMIN_PATHS.stats, { refetchInterval: 30_000 });
  const deliveries = useApi<PageResponse<Delivery>>(ADMIN_PATHS.deliveries(page), { keepPrevious: true });
  const cards = [
    { label: "Notifications (24h)", value: stats.data?.notificationsLast24h, icon: BellRing, tone: "bg-primary" },
    { label: "Realtime sent", value: stats.data?.realtimeSent, icon: Activity, tone: "bg-info" },
    { label: "Push sent", value: stats.data?.fcmSent, icon: CheckCircle2, tone: "bg-success" },
    { label: "Push failed", value: stats.data?.fcmFailed, icon: ShieldAlert, tone: "bg-destructive" },
    { label: "Active devices", value: stats.data?.activeDevices, icon: Smartphone, tone: "bg-brand-2" },
  ];
  const tone = (status: string) => (status === "sent" ? "success" : status === "failed" ? "danger" : "neutral");
  return (
    <div className="space-y-6">
      <Stagger className="grid gap-4 sm:grid-cols-2 xl:grid-cols-5">
        {cards.map((card) => (
          <StaggerItem key={card.label}>
            <Card className="p-4">
              <div className="flex items-center justify-between">
                <p className="text-xs text-muted-foreground">{card.label}</p>
                <span className={`grid size-8 place-items-center rounded-lg text-white ${card.tone}`}>
                  <card.icon className="size-4" />
                </span>
              </div>
              <div className="mt-2 text-2xl font-semibold">{card.value === undefined ? <Skeleton className="h-8 w-14" /> : <AnimatedNumber value={card.value} />}</div>
            </Card>
          </StaggerItem>
        ))}
      </Stagger>
      <Card>
        <CardHeader>
          <CardTitle>Recent deliveries</CardTitle>
        </CardHeader>
        <CardContent className="px-0">
          {deliveries.isLoading ? (
            <Skeleton className="m-5 h-48" />
          ) : deliveries.data && deliveries.data.content.length > 0 ? (
            <>
              <Table>
                <thead className="border-b">
                  <tr>
                    <Th>When</Th>
                    <Th>Channel</Th>
                    <Th>Status</Th>
                    <Th>Detail</Th>
                  </tr>
                </thead>
                <tbody>
                  {deliveries.data.content.map((delivery) => (
                    <tr key={delivery.id} className="border-b last:border-0">
                      <Td className="whitespace-nowrap text-muted-foreground">{timeAgo(delivery.createdAt)}</Td>
                      <Td>
                        <Badge tone={delivery.channel === "fcm" ? "violet" : "info"}>{delivery.channel}</Badge>
                      </Td>
                      <Td>
                        <Badge tone={tone(delivery.status)} dot>
                          {delivery.status}
                        </Badge>
                      </Td>
                      <Td className="max-w-md truncate text-muted-foreground">{delivery.detail ?? "—"}</Td>
                    </tr>
                  ))}
                </tbody>
              </Table>
              <div className="px-5 pb-3">
                <Pagination page={deliveries.data.number} totalPages={deliveries.data.totalPages} onChange={setPage} />
              </div>
            </>
          ) : (
            <EmptyState icon={<Activity className="size-7" />} title="No deliveries yet" description="Send a message to see how it was delivered." />
          )}
        </CardContent>
      </Card>
    </div>
  );
}
