import { useState } from "react";
import { useNavigate } from "@tanstack/react-router";
import { AnimatePresence, motion } from "motion/react";
import { Bell, BellOff, Check, CheckCheck, Laptop, Smartphone, Trash2 } from "lucide-react";
import { api } from "@/lib/api";
import { useAction, useApi } from "@/lib/query";
import { NOTIFICATION_PATHS, inboxPath } from "@/lib/paths";
import { timeAgo, titleCase } from "@/lib/format";
import { useToast } from "@/components/ui/toast";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Chips, Switch } from "@/components/ui/form";
import { EmptyState, PageHeader, Pagination, Skeleton, Tabs } from "@/components/ui/misc";
import type { AppNotification, Device, PageResponse, Preferences } from "@/lib/types";
import { Hint } from "@/components/ui/hint";


const KNOWN_TYPES = ["complaint.created", "complaint.status_changed", "complaint.assigned", "complaint.comment", "announcement"];

export function NotificationsView() {
  const navigate = useNavigate();
  const toast = useToast();
  const [tab, setTab] = useState<"all" | "unread">("all");
  const [page, setPage] = useState(0);
  const { data, isLoading } = useApi<PageResponse<AppNotification>>(inboxPath(page, tab === "unread"), { keepPrevious: true });
  const prefs = useApi<Preferences>(NOTIFICATION_PATHS.preferences);
  const devices = useApi<Device[]>(NOTIFICATION_PATHS.devices);

  const read = useAction((id: string) => api.patch(`/notifications/${id}/read`), { invalidate: ["/notifications"] });
  const readAll = useAction(() => api.post("/notifications/read-all"), { invalidate: ["/notifications"], onSuccess: () => toast.success("All caught up") });
  const remove = useAction((id: string) => api.delete(`/notifications/${id}`), { invalidate: ["/notifications"] });
  const savePrefs = useAction((next: Preferences) => api.put(NOTIFICATION_PATHS.preferences, next), {
    invalidate: [NOTIFICATION_PATHS.preferences],
    onSuccess: () => toast.success("Preferences saved"),
    onError: (e) => toast.error("Could not save", e.message),
  });
  const removeDevice = useAction((id: string) => api.delete(`${NOTIFICATION_PATHS.devices}/${id}`), { invalidate: [NOTIFICATION_PATHS.devices] });

  return (
    <div>
      <PageHeader
        title="Notifications"
        description="Realtime updates about your complaints and announcements."
        icon={<Bell className="size-5" />}
        actions={
          <Button variant="outline" className="h-10" onClick={() => readAll.mutate(undefined)} disabled={readAll.isPending}>
            <CheckCheck /> Mark all read
          </Button>
        }
      />
      <div className="grid gap-6 xl:grid-cols-[minmax(0,1fr)_380px]">
        <div>
          <div className="mb-4">
            <Tabs
              id="inbox"
              tabs={[
                { value: "all", label: "All" },
                { value: "unread", label: "Unread" },
              ]}
              value={tab}
              onChange={(value) => {
                setTab(value);
                setPage(0);
              }}
            />
          </div>
          <Card>
            {isLoading ? (
              <div className="space-y-2 p-4">
                {[0, 1, 2, 3].map((index) => (
                  <Skeleton key={index} className="h-16" />
                ))}
              </div>
            ) : data && data.content.length > 0 ? (
              <div className="p-2">
                <ul>
                  <AnimatePresence initial={false}>
                    {data.content.map((notification) => (
                      <motion.li key={notification.id} layout initial={{ opacity: 0, x: -16 }} animate={{ opacity: 1, x: 0 }} exit={{ opacity: 0, x: 40, height: 0 }} className="group flex items-start gap-3 rounded-2xl p-3.5 transition hover:bg-muted/60">
                        <span className={`mt-1.5 size-2.5 shrink-0 rounded-full ${notification.readAt ? "bg-muted-foreground/25" : "gradient-brand shadow shadow-primary/40"}`} />
                        <button
                          className="min-w-0 flex-1 text-left"
                          onClick={() => {
                            if (!notification.readAt) read.mutate(notification.id);
                            const route = notification.data?.route;
                            if (route) void navigate({ href: route });
                          }}
                        >
                          <span className="flex flex-wrap items-center gap-2">
                            <span className={`text-sm ${notification.readAt ? "font-medium" : "font-semibold"}`}>{notification.title}</span>
                            {notification.priority === "high" && <Badge tone="danger">Important</Badge>}
                            <Badge>{titleCase(notification.type.replace(".", " "))}</Badge>
                          </span>
                          {notification.body && <span className="mt-0.5 block text-sm text-muted-foreground">{notification.body}</span>}
                          <span className="mt-1 block text-xs text-muted-foreground/80">{timeAgo(notification.createdAt)}</span>
                        </button>
                        <div className="flex gap-1 opacity-0 transition group-hover:opacity-100 focus-within:opacity-100">
                          {!notification.readAt && (
                            <Hint label="Mark as read">
                              <button onClick={() => read.mutate(notification.id)} className="rounded-lg p-1.5 text-muted-foreground hover:bg-background hover:text-foreground" aria-label="Mark as read">
                                <Check className="size-4" />
                              </button>
                            </Hint>
                          )}
                          <Hint label="Delete">
                            <button onClick={() => remove.mutate(notification.id)} className="rounded-lg p-1.5 text-muted-foreground hover:bg-destructive/10 hover:text-destructive" aria-label="Delete">
                              <Trash2 className="size-4" />
                            </button>
                          </Hint>
                        </div>
                      </motion.li>
                    ))}
                  </AnimatePresence>
                </ul>
                <div className="px-3 pb-2">
                  <Pagination page={data.number} totalPages={data.totalPages} onChange={setPage} />
                </div>
              </div>
            ) : (
              <EmptyState icon={<BellOff className="size-7" />} title={tab === "unread" ? "No unread notifications" : "No notifications yet"} description="Updates about your complaints will appear here in realtime." />
            )}
          </Card>
        </div>

        <div className="space-y-6">
          <Card>
            <CardHeader>
              <div>
                <CardTitle>Delivery preferences</CardTitle>
                <CardDescription>Choose how you get notified</CardDescription>
              </div>
            </CardHeader>
            <CardContent className="space-y-5">
              {prefs.data ? (
                <>
                  <label className="flex items-center justify-between gap-3">
                    <span>
                      <span className="block text-sm font-medium">Realtime in-app</span>
                      <span className="block text-xs text-muted-foreground">Live toasts while you are using the app</span>
                    </span>
                    <Switch checked={prefs.data.realtimeEnabled} onChange={(value) => savePrefs.mutate({ ...prefs.data!, realtimeEnabled: value })} label="Realtime" />
                  </label>
                  <label className="flex items-center justify-between gap-3">
                    <span>
                      <span className="block text-sm font-medium">Push to my devices</span>
                      <span className="block text-xs text-muted-foreground">Mobile and browser push messages</span>
                    </span>
                    <Switch checked={prefs.data.pushEnabled} onChange={(value) => savePrefs.mutate({ ...prefs.data!, pushEnabled: value })} label="Push" />
                  </label>
                  <div>
                    <p className="mb-2 text-sm font-medium">Mute these types</p>
                    <Chips
                      options={KNOWN_TYPES.map((type) => ({ value: type, label: titleCase(type.replace(".", " ")) }))}
                      value={prefs.data.mutedTypes}
                      onChange={(value) => savePrefs.mutate({ ...prefs.data!, mutedTypes: value })}
                    />
                    <p className="mt-2 text-xs text-muted-foreground">Muted types still land in your inbox, silently.</p>
                  </div>
                </>
              ) : (
                <Skeleton className="h-40" />
              )}
            </CardContent>
          </Card>

          <Card>
            <CardHeader>
              <div>
                <CardTitle>Your devices</CardTitle>
                <CardDescription>Devices that receive push notifications</CardDescription>
              </div>
            </CardHeader>
            <CardContent className="space-y-2">
              {devices.data && devices.data.length > 0 ? (
                devices.data.map((device) => (
                  <div key={device.id} className="flex items-center gap-3 rounded-xl border p-3">
                    <span className="grid size-9 place-items-center rounded-lg bg-muted text-muted-foreground">{device.platform === "web" ? <Laptop className="size-4" /> : <Smartphone className="size-4" />}</span>
                    <div className="min-w-0 flex-1">
                      <p className="truncate text-sm font-medium">{device.deviceName ?? titleCase(device.platform)}</p>
                      <p className="text-xs text-muted-foreground">
                        {device.active ? "Active" : "Inactive"} · seen {timeAgo(device.lastSeenAt)}
                      </p>
                    </div>
                    <Hint label="Remove device">
                      <button onClick={() => removeDevice.mutate(device.id)} className="rounded-lg p-1.5 text-muted-foreground hover:bg-destructive/10 hover:text-destructive" aria-label="Remove device">
                        <Trash2 className="size-4" />
                      </button>
                    </Hint>
                  </div>
                ))
              ) : (
                <p className="py-3 text-center text-sm text-muted-foreground">No devices registered. Install the mobile app or enable browser push to add one.</p>
              )}
            </CardContent>
          </Card>
        </div>
      </div>
    </div>
  );
}
