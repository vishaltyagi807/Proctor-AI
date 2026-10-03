import { useState } from "react";
import { HrefLink } from "@/components/ui/href-link";
import { motion } from "motion/react";
import {
  AlarmClock,
  ArrowUpRight,
  Bell,
  Building2,
  CheckCircle2,
  ChevronRight,
  CircleCheck,
  Eye,
  Clock3,
  FilePlus2,
  Flame,
  Gauge,
  Inbox,
  KeyRound,
  Layers,
  LineChart,
  ListTodo,
  Lock,
  MessageSquareWarning,
  ScanFace,
  Search,
  Send,
  ShieldCheck,
  SlidersHorizontal,
  Table2,
  UserCheck,
  Users,
} from "lucide-react";
import { useApi } from "@/lib/query";
import { DASHBOARD_PATHS, FACES_PATH } from "@/lib/paths";
import { describeLevel, LOWEST_LEVEL } from "@/lib/permissions";
import { useSession } from "@/components/providers/session";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { AnimatedNumber, Hover, Stagger, StaggerItem } from "@/components/ui/motion";
import { Bars, Donut, TrendChart } from "@/components/ui/charts";
import { EmptyState, Skeleton, Table, Td, Th } from "@/components/ui/misc";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { ComplaintRow } from "@/components/complaints/complaint-row";
import { timeAgo } from "@/lib/format";
import { cn } from "@/lib/utils";
import type { AppNotification, Complaint, ComplaintStats, Department, FaceEnrollment, PageResponse, Role, UserInfo } from "@/lib/types";

type Persona = "admin" | "staff" | "member";
type Icon = React.ComponentType<{ className?: string }>;

function greeting() {
  const hour = new Date().getHours();
  return hour < 12 ? "Good morning" : hour < 18 ? "Good afternoon" : "Good evening";
}

function formatDuration(hours: number | null | undefined) {
  if (hours === null || hours === undefined) return "—";
  if (hours < 1) return `${Math.max(1, Math.round(hours * 60))} min`;
  if (hours < 48) return `${hours.toFixed(1)} h`;
  return `${(hours / 24).toFixed(1)} d`;
}

function percent(part: number, whole: number) {
  return whole > 0 ? Math.round((part / whole) * 100) : 0;
}

function shortDay(value: string) {
  return new Date(`${value}T00:00:00`).toLocaleDateString(undefined, { month: "short", day: "numeric" });
}

function Kpi({ label, value, display, hint, icon: IconComponent, tone, href }: { label: string; value: number | undefined; display?: string; hint?: string; icon: Icon; tone: string; href?: string }) {
  const body = (
    <Card className={cn("relative h-full overflow-hidden p-5", href && "transition hover:border-primary/40")}>
      <div className={`absolute -right-6 -top-6 size-28 rounded-full opacity-[0.12] blur-2xl ${tone}`} />
      <div className="flex items-start justify-between gap-3">
        <div className="min-w-0">
          <p className="text-sm text-muted-foreground">{label}</p>
          <div className="mt-2 text-3xl font-semibold tracking-tight tabular-nums">
            {value === undefined ? <Skeleton className="h-9 w-16" /> : display ?? <AnimatedNumber value={value} />}
          </div>
          {hint && <p className="mt-1 truncate text-xs text-muted-foreground">{hint}</p>}
        </div>
        <span className={`grid size-10 shrink-0 place-items-center rounded-xl text-white shadow-md ${tone}`}>
          <IconComponent className="size-5" />
        </span>
      </div>
    </Card>
  );
  return (
    <StaggerItem>
      <Hover lift={href ? 3 : 0}>{href ? <HrefLink href={href}>{body}</HrefLink> : body}</Hover>
    </StaggerItem>
  );
}

type Attention = { key: string; label: string; detail: string; href: string; icon: Icon; tone: "danger" | "warning" | "info" | "brand"; count?: number };

const ATTENTION_TONE: Record<Attention["tone"], string> = {
  danger: "bg-destructive/12 text-destructive",
  warning: "bg-warning/15 text-warning",
  info: "bg-info/12 text-info",
  brand: "bg-primary/10 text-primary",
};

function AttentionCard({ items, loading }: { items: Attention[]; loading: boolean }) {
  return (
    <Card>
      <CardHeader>
        <div>
          <CardTitle>Needs your attention</CardTitle>
          <CardDescription>What to look at first, based on your role</CardDescription>
        </div>
        <ListTodo className="size-4 text-muted-foreground" />
      </CardHeader>
      <CardContent className="space-y-2">
        {loading ? (
          [0, 1, 2].map((index) => <Skeleton key={index} className="h-14" />)
        ) : items.length === 0 ? (
          <div className="flex items-center gap-3 rounded-2xl border border-dashed p-4">
            <span className="grid size-10 place-items-center rounded-xl bg-success/12 text-success">
              <CircleCheck className="size-5" />
            </span>
            <div>
              <p className="text-sm font-medium">You&apos;re all caught up</p>
              <p className="text-xs text-muted-foreground">Nothing is waiting on you right now.</p>
            </div>
          </div>
        ) : (
          items.map((item, index) => (
            <motion.div key={item.key} initial={{ opacity: 0, y: 8 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: 0.05 * index }}>
              <HrefLink href={item.href} className="group flex items-center gap-3.5 rounded-2xl border p-3.5 transition hover:border-primary/40 hover:bg-muted/40">
                <span className={cn("grid size-10 shrink-0 place-items-center rounded-xl", ATTENTION_TONE[item.tone])}>
                  <item.icon className="size-5" />
                </span>
                <span className="min-w-0 flex-1">
                  <span className="flex items-center gap-2 text-sm font-medium">
                    {item.label}
                    {item.count !== undefined && <Badge tone={item.tone === "brand" ? "brand" : item.tone}>{item.count}</Badge>}
                  </span>
                  <span className="block truncate text-xs text-muted-foreground">{item.detail}</span>
                </span>
                <ChevronRight className="size-4 text-muted-foreground transition group-hover:translate-x-0.5 group-hover:text-primary" />
              </HrefLink>
            </motion.div>
          ))
        )}
      </CardContent>
    </Card>
  );
}

function TrendCard({ stats }: { stats: ComplaintStats | undefined }) {
  const [asTable, setAsTable] = useState(false);
  const trend = stats?.trend ?? [];
  const created = trend.reduce((sum, point) => sum + point.created, 0);
  const resolved = trend.reduce((sum, point) => sum + point.resolved, 0);
  return (
    <Card>
      <CardHeader>
        <div>
          <CardTitle>Last 14 days</CardTitle>
          <CardDescription>{stats ? `${created} filed · ${resolved} resolved` : "Loading"}</CardDescription>
        </div>
        <Button variant="ghost" size="sm" onClick={() => setAsTable((current) => !current)} aria-pressed={asTable}>
          {asTable ? <LineChart /> : <Table2 />} {asTable ? "Chart" : "Table"}
        </Button>
      </CardHeader>
      <CardContent>
        {!stats ? (
          <Skeleton className="h-56" />
        ) : asTable ? (
          <div className="max-h-64 overflow-y-auto scrollbar-thin">
            <Table>
              <thead className="border-b">
                <tr>
                  <Th>Day</Th>
                  <Th className="text-right">Filed</Th>
                  <Th className="text-right">Resolved</Th>
                </tr>
              </thead>
              <tbody>
                {[...trend].reverse().map((point) => (
                  <tr key={point.day} className="border-b last:border-0">
                    <Td>{shortDay(point.day)}</Td>
                    <Td className="text-right tabular-nums">{point.created}</Td>
                    <Td className="text-right tabular-nums">{point.resolved}</Td>
                  </tr>
                ))}
              </tbody>
            </Table>
          </div>
        ) : (
          <TrendChart
            labels={trend.map((point) => point.day)}
            formatLabel={shortDay}
            series={[
              { key: "created", label: "Filed", color: "var(--series-created)", values: trend.map((point) => point.created) },
              { key: "resolved", label: "Resolved", color: "var(--series-resolved)", dashed: true, values: trend.map((point) => point.resolved) },
            ]}
          />
        )}
      </CardContent>
    </Card>
  );
}

function StatusCard({ stats }: { stats: ComplaintStats | undefined }) {
  return (
    <Card>
      <CardHeader>
        <div>
          <CardTitle>By status</CardTitle>
          <CardDescription>Where complaints currently stand</CardDescription>
        </div>
      </CardHeader>
      <CardContent>
        {stats ? (
          <Donut
            centerLabel="complaints"
            slices={[
              { label: "pending", value: stats.pending, color: "var(--warning)" },
              { label: "in review", value: stats.reviewed, color: "var(--info)" },
              { label: "resolved", value: stats.resolved, color: "var(--success)" },
              { label: "rejected", value: stats.rejected, color: "var(--destructive)" },
            ]}
          />
        ) : (
          <Skeleton className="h-44" />
        )}
      </CardContent>
    </Card>
  );
}

function PriorityCard({ stats }: { stats: ComplaintStats | undefined }) {
  return (
    <Card>
      <CardHeader>
        <div>
          <CardTitle>By priority</CardTitle>
          <CardDescription>{stats ? `${stats.criticalOpen} high or urgent still open` : "Loading"}</CardDescription>
        </div>
      </CardHeader>
      <CardContent>
        {stats ? (
          <Bars
            items={[
              { label: "urgent", value: stats.urgent, color: "var(--destructive)" },
              { label: "high", value: stats.high, color: "var(--warning)" },
              { label: "medium", value: stats.medium, color: "var(--info)" },
              { label: "low", value: stats.low, color: "var(--chart-3)" },
            ]}
          />
        ) : (
          <Skeleton className="h-44" />
        )}
      </CardContent>
    </Card>
  );
}

function DepartmentCard({ stats }: { stats: ComplaintStats | undefined }) {
  const rows = stats?.departments ?? [];
  return (
    <Card>
      <CardHeader>
        <div>
          <CardTitle>Open complaints by department</CardTitle>
          <CardDescription>Busiest departments first</CardDescription>
        </div>
        <Building2 className="size-4 text-muted-foreground" />
      </CardHeader>
      <CardContent>
        {!stats ? (
          <Skeleton className="h-40" />
        ) : rows.length === 0 ? (
          <p className="text-sm text-muted-foreground">No complaints yet.</p>
        ) : (
          <Bars items={rows.map((row) => ({ label: `${row.name} · ${row.total} total`, value: row.open, color: "var(--series-created)" }))} />
        )}
      </CardContent>
    </Card>
  );
}

function AccessCard({ persona }: { persona: Persona }) {
  const { can, manage, session, superuser } = useSession();
  const roles = [...session.roles].sort((a, b) => a.level - b.level);
  const verbs = { read: "see", write: "assign", update: "edit", delete: "remove" } as const;
  const describe = (entity: string, actions: readonly ("read" | "write" | "update" | "delete")[]) =>
    actions
      .map((action) => {
        const scope = can(entity, action, ["all"]) ? "all" : can(entity, action, ["department"]) ? "department" : null;
        return scope ? `${verbs[action]}${scope === "department" ? " (your departments)" : ""}` : null;
      })
      .filter(Boolean)
      .join(", ");
  const sameRole = describe("same_role_peers", ["read", "write", "update", "delete"]);
  const parallel = describe("parallel_role_peers", ["read", "write", "update", "delete"]);
  const seniors = describe("senior_users", ["read"]);
  const faceScope = can("face_recognition", "read", ["all"]) ? "everyone" : can("face_recognition", "read", ["department"]) ? "your departments" : null;
  const lines: { icon: Icon; text: string; muted?: boolean }[] = superuser
    ? [
        { icon: KeyRound, text: "Unrestricted access to every area of the platform." },
        { icon: Users, text: "You can see and manage every account, including other administrators." },
      ]
    : [
        {
          icon: Layers,
          text: manage.level === LOWEST_LEVEL ? "You have no role yet, so you can only manage your own account." : `You can see and manage people and roles ranked below level ${manage.level}.`,
        },
        sameRole
          ? { icon: UserCheck, text: `People who share your role: ${sameRole}.` }
          : { icon: Lock, text: "People who share your role are hidden from you.", muted: true },
        parallel
          ? { icon: Users, text: `People in parallel roles at your level: ${parallel}.` }
          : { icon: Lock, text: "People in parallel roles at your level are hidden from you.", muted: true },
        seniors ? { icon: Eye, text: `People ranked above you: ${seniors}.` } : { icon: Lock, text: "People ranked above you are hidden from you.", muted: true },
        ...(faceScope ? [{ icon: ScanFace, text: `Face recognition identifies ${faceScope}.` }] : []),
      ];
  return (
    <Card>
      <CardHeader>
        <div>
          <CardTitle>Your access</CardTitle>
          <CardDescription>{persona === "member" ? "What your account can do" : "Your place in the hierarchy"}</CardDescription>
        </div>
        <ShieldCheck className="size-4 text-muted-foreground" />
      </CardHeader>
      <CardContent className="space-y-4">
        <div className="flex flex-wrap items-center gap-1.5">
          {roles.map((role) => (
            <Badge key={role.id} tone={role.superuser ? "violet" : "brand"} className="capitalize">
              {role.name.replaceAll("_", " ")}
            </Badge>
          ))}
          <Badge>{manage.level === LOWEST_LEVEL ? "No level" : `Level ${manage.level} · ${describeLevel(manage.level)}`}</Badge>
        </div>
        <ul className="space-y-2.5">
          {lines.map((line) => (
            <li key={line.text} className={cn("flex items-start gap-2.5 text-sm", line.muted && "text-muted-foreground")}>
              <line.icon className="mt-0.5 size-4 shrink-0 text-muted-foreground" />
              {line.text}
            </li>
          ))}
        </ul>
      </CardContent>
    </Card>
  );
}

function FaceStatusCard() {
  const { user, can } = useSession();
  const enrollments = useApi<FaceEnrollment[]>(FACES_PATH);
  const mine = enrollments.data?.find((entry) => entry.userId === user.id);
  const canEnroll = can("face_enrollments", "write") || can("face_enrollments", "update");
  return (
    <Card>
      <CardHeader>
        <div>
          <CardTitle>Face enrollment</CardTitle>
          <CardDescription>Used to recognize you on campus</CardDescription>
        </div>
        <ScanFace className="size-4 text-muted-foreground" />
      </CardHeader>
      <CardContent>
        {enrollments.isLoading ? (
          <Skeleton className="h-14" />
        ) : mine ? (
          <div className="flex items-center gap-3">
            <span className="grid size-10 place-items-center rounded-xl bg-success/12 text-success">
              <CheckCircle2 className="size-5" />
            </span>
            <div className="min-w-0 flex-1">
              <p className="text-sm font-medium">Enrolled {timeAgo(mine.enrolledAt)}</p>
              <p className="text-xs text-muted-foreground">{mine.selfEnrollLocked ? "Ask staff to update your photo." : "You can update your photo if needed."}</p>
            </div>
          </div>
        ) : (
          <div className="space-y-3">
            <p className="text-sm text-muted-foreground">You haven&apos;t enrolled your face yet.</p>
            {canEnroll && (
              <HrefLink href="/faces">
                <Button variant="outline" size="sm">
                  <ScanFace /> Enroll now
                </Button>
              </HrefLink>
            )}
          </div>
        )}
      </CardContent>
    </Card>
  );
}

export function DashboardView() {
  const { can, user, staff, superuser, role, manage } = useSession();
  const persona: Persona = superuser || (can("users", "update", ["all"]) && can("roles", "read", ["all"])) ? "admin" : staff ? "staff" : "member";
  const showStats = can("dashboard", "read");
  const canComplaints = can("complaints", "read");
  const canFile = can("complaints", "write");
  const canUsers = can("users", "read", ["department", "all"]);
  const canDepartments = can("departments", "read", ["department", "all"]);
  const canRoles = can("roles", "read", ["all"]);
  const canFaces = can("face_enrollments", "read");

  const stats = useApi<ComplaintStats>(showStats ? DASHBOARD_PATHS.stats : null);
  const recent = useApi<PageResponse<Complaint>>(canComplaints ? (persona === "member" ? DASHBOARD_PATHS.recent : DASHBOARD_PATHS.queue) : null);
  const notifications = useApi<PageResponse<AppNotification>>(DASHBOARD_PATHS.notifications);
  const users = useApi<PageResponse<UserInfo>>(persona !== "member" && canUsers ? DASHBOARD_PATHS.users : null);
  const departments = useApi<PageResponse<Department>>(persona === "admin" && canDepartments ? DASHBOARD_PATHS.departments : null);
  const roles = useApi<PageResponse<Role>>(persona === "admin" && canRoles ? DASHBOARD_PATHS.roles : null);
  const faces = useApi<FaceEnrollment[]>(persona === "admin" && canFaces ? FACES_PATH : null);

  const s = stats.data;
  const firstName = user.name.split(" ")[0];
  const unread = notifications.data?.content.filter((notification) => !notification.readAt).length ?? 0;

  const attention: Attention[] = [];
  if (s) {
    if (persona !== "member") {
      if (s.assignedToMe > 0) attention.push({ key: "assigned", label: "Assigned to you", detail: "Open complaints you are responsible for", href: "/complaints?mine=assigned", icon: UserCheck, tone: "brand", count: s.assignedToMe });
      if (s.criticalOpen > 0) attention.push({ key: "critical", label: "High-priority open", detail: "Urgent or high complaints not yet closed", href: "/complaints?priority=urgent", icon: Flame, tone: "danger", count: s.criticalOpen });
      if (s.overdue > 0) attention.push({ key: "overdue", label: "Waiting over 7 days", detail: "Open complaints older than a week", href: "/complaints?status=pending", icon: AlarmClock, tone: "warning", count: s.overdue });
      if (s.unassigned > 0) attention.push({ key: "unassigned", label: "Unassigned", detail: "Open complaints nobody owns yet", href: "/complaints?status=pending", icon: Inbox, tone: "info", count: s.unassigned });
    } else {
      if (s.pending > 0) attention.push({ key: "pending", label: "Awaiting review", detail: "Your complaints that staff haven't picked up yet", href: "/complaints?status=pending", icon: Clock3, tone: "warning", count: s.pending });
      if (s.reviewed > 0) attention.push({ key: "review", label: "In review", detail: "Staff are working on these", href: "/complaints?status=reviewed", icon: Search, tone: "info", count: s.reviewed });
    }
  }
  if (unread > 0) attention.push({ key: "unread", label: "Unread notifications", detail: "Updates you haven't seen yet", href: "/notifications", icon: Bell, tone: "brand", count: unread });

  const quick = [
    canFile && { href: "/complaints/new", label: "File a complaint", icon: FilePlus2, text: "Report an issue with evidence" },
    canComplaints && persona !== "member" && { href: "/complaints?status=pending", label: "Open the queue", icon: Inbox, text: "Review pending complaints" },
    canComplaints && persona === "member" && { href: "/complaints", label: "My complaints", icon: MessageSquareWarning, text: "Track what you've reported" },
    canUsers && { href: "/users", label: "Manage people", icon: Users, text: "Accounts, roles and departments" },
    canRoles && { href: "/roles", label: "Roles & access", icon: ShieldCheck, text: "Levels and permissions" },
    (can("face_recognition", "read") || can("face_live_recognition", "read") || can("face_enrollments", "write")) && { href: "/faces", label: "Face recognition", icon: ScanFace, text: "Identify or enroll faces" },
    (can("integrations", "read") || can("notifications", "write", ["all", "department"])) && { href: "/admin/notifications", label: "Broadcast", icon: Send, text: "Announcements and push" },
    persona === "admin" && can("custom_fields", "write") && { href: "/custom-fields", label: "Custom fields", icon: SlidersHorizontal, text: "Extend forms and records" },
  ].filter(Boolean) as { href: string; label: string; icon: Icon; text: string }[];

  const subtitle =
    persona === "admin"
      ? "Organization-wide health, people and access at a glance."
      : persona === "staff"
        ? "Your queue and how your departments are doing."
        : "Follow your complaints and stay up to date.";

  return (
    <div className="space-y-8">
      <motion.section initial={{ opacity: 0, y: 16 }} animate={{ opacity: 1, y: 0 }} className="relative overflow-hidden rounded-3xl p-7 text-white gradient-brand-animated shadow-xl shadow-primary/20 sm:p-9">
        <div className="bg-grid absolute inset-0 opacity-40" />
        <div className="absolute -right-10 -top-16 size-72 rounded-full bg-white/20 blur-3xl animate-float" />
        <div className="relative flex flex-wrap items-end justify-between gap-6">
          <div className="min-w-0">
            <div className="flex flex-wrap items-center gap-2 text-sm text-white/80">
              <span className="rounded-full bg-white/15 px-2.5 py-0.5 font-medium capitalize">{role}</span>
              {manage.level !== LOWEST_LEVEL && <span className="rounded-full bg-white/10 px-2.5 py-0.5">Level {manage.level}</span>}
              <span>{new Date().toLocaleDateString(undefined, { weekday: "long", month: "long", day: "numeric" })}</span>
            </div>
            <h1 className="mt-3 text-3xl font-semibold tracking-tight sm:text-4xl">
              {greeting()}, {firstName}
            </h1>
            <p className="mt-2 max-w-xl text-white/80">{subtitle}</p>
          </div>
          <div className="flex flex-wrap gap-3">
            {persona !== "member" && canComplaints && (
              <HrefLink href="/complaints?status=pending">
                <Button className="h-11 bg-white px-5 text-primary hover:bg-white/90">
                  <Inbox /> Open queue
                </Button>
              </HrefLink>
            )}
            {canFile && (
              <HrefLink href="/complaints/new">
                <Button className={cn("h-11 px-5", persona === "member" ? "bg-white text-primary hover:bg-white/90" : "border border-white/30 bg-white/10 text-white hover:bg-white/20")}>
                  <FilePlus2 /> New complaint
                </Button>
              </HrefLink>
            )}
          </div>
        </div>
      </motion.section>

      {showStats && persona === "admin" && (
        <Stagger className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
          <Kpi label="Open complaints" value={s?.open} hint={s ? `${s.unassigned} unassigned` : undefined} icon={MessageSquareWarning} tone="bg-primary" href="/complaints?status=pending" />
          <Kpi label="Resolved this week" value={s?.resolvedLast7Days} hint={s ? `${s.createdLast7Days} filed this week` : undefined} icon={CheckCircle2} tone="bg-success" />
          <Kpi label="Avg. time to resolve" value={s ? 0 : undefined} display={formatDuration(s?.avgResolutionHours)} hint={s ? `${percent(s.resolved, s.resolved + s.rejected + s.open)}% resolution rate` : undefined} icon={Gauge} tone="bg-info" />
          <Kpi label="Waiting over 7 days" value={s?.overdue} hint="Open and older than a week" icon={AlarmClock} tone="bg-warning" href="/complaints?status=pending" />
        </Stagger>
      )}

      {showStats && persona === "staff" && (
        <Stagger className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
          <Kpi label="Assigned to you" value={s?.assignedToMe} hint="Open and yours to handle" icon={UserCheck} tone="bg-primary" href="/complaints?mine=assigned" />
          <Kpi label="Unassigned" value={s?.unassigned} hint="Open with no owner" icon={Inbox} tone="bg-info" href="/complaints?status=pending" />
          <Kpi label="High priority open" value={s?.criticalOpen} hint={s ? `${s.overdue} waiting over 7 days` : undefined} icon={Flame} tone="bg-destructive" href="/complaints?priority=urgent" />
          <Kpi label="Resolved this week" value={s?.resolvedLast7Days} hint={s ? `Avg. ${formatDuration(s.avgResolutionHours)} to resolve` : undefined} icon={CheckCircle2} tone="bg-success" />
        </Stagger>
      )}

      {showStats && persona === "member" && (
        <Stagger className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
          <Kpi label="My complaints" value={s?.total} icon={MessageSquareWarning} tone="bg-primary" href="/complaints" />
          <Kpi label="Awaiting review" value={s?.pending} icon={Clock3} tone="bg-warning" href="/complaints?status=pending" />
          <Kpi label="In review" value={s?.reviewed} icon={Search} tone="bg-info" href="/complaints?status=reviewed" />
          <Kpi label="Resolved" value={s?.resolved} hint={s && s.avgResolutionHours !== null ? `Usually within ${formatDuration(s.avgResolutionHours)}` : undefined} icon={CheckCircle2} tone="bg-success" href="/complaints?status=resolved" />
        </Stagger>
      )}

      {persona === "admin" && (canUsers || canDepartments || canRoles || canFaces) && (
        <Stagger className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
          {canUsers && <Kpi label="People" value={users.data?.totalElements} icon={Users} tone="bg-brand-2" href="/users" />}
          {canDepartments && <Kpi label="Departments" value={departments.data?.totalElements} icon={Building2} tone="bg-chart-2" href="/departments" />}
          {canRoles && <Kpi label="Roles" value={roles.data?.totalElements} icon={ShieldCheck} tone="bg-chart-1" href="/roles" />}
          {canFaces && (
            <Kpi
              label="Face enrollment"
              value={faces.data && users.data ? percent(faces.data.length, users.data.totalElements) : undefined}
              display={faces.data && users.data ? `${percent(faces.data.length, users.data.totalElements)}%` : undefined}
              hint={faces.data ? `${faces.data.length} people enrolled` : undefined}
              icon={ScanFace}
              tone="bg-chart-3"
              href="/faces"
            />
          )}
        </Stagger>
      )}

      {persona === "staff" && canUsers && (
        <Stagger className="grid gap-4 sm:grid-cols-2">
          <Kpi label="People in your departments" value={users.data?.totalElements} icon={Users} tone="bg-brand-2" href="/users" />
          {s && <Kpi label="Open in your departments" value={s.open} hint={`${s.total} complaints overall`} icon={Building2} tone="bg-chart-2" href="/complaints" />}
        </Stagger>
      )}

      <div className="grid gap-6 xl:grid-cols-3">
        <div className="space-y-6 xl:col-span-2">
          {showStats && persona !== "member" && <TrendCard stats={s} />}

          {showStats && persona !== "member" && (
            <div className="grid gap-6 md:grid-cols-2">
              <StatusCard stats={s} />
              {persona === "admin" || (s?.departments.length ?? 0) > 1 ? <DepartmentCard stats={s} /> : <PriorityCard stats={s} />}
            </div>
          )}

          {showStats && persona === "admin" && <PriorityCard stats={s} />}

          {canComplaints && (
            <Card>
              <CardHeader>
                <div>
                  <CardTitle>{persona === "member" ? "Your recent complaints" : "Waiting in the queue"}</CardTitle>
                  <CardDescription>{persona === "member" ? "Latest first" : "Oldest pending complaints first"}</CardDescription>
                </div>
                <HrefLink href={persona === "member" ? "/complaints" : "/complaints?status=pending"} className="flex items-center gap-1 text-sm font-medium text-primary hover:underline">
                  View all <ArrowUpRight className="size-4" />
                </HrefLink>
              </CardHeader>
              <CardContent className="px-2">
                {recent.isLoading ? (
                  <div className="space-y-2 p-3">
                    {[0, 1, 2].map((index) => (
                      <Skeleton key={index} className="h-14" />
                    ))}
                  </div>
                ) : recent.data && recent.data.content.length > 0 ? (
                  <Stagger>
                    {recent.data.content.map((complaint) => (
                      <StaggerItem key={complaint.id}>
                        <ComplaintRow complaint={complaint} showStudent={persona !== "member"} />
                      </StaggerItem>
                    ))}
                  </Stagger>
                ) : (
                  <EmptyState
                    icon={<Inbox className="size-7" />}
                    title={persona === "member" ? "No complaints yet" : "The queue is clear"}
                    description={persona === "member" ? (canFile ? "When you file a complaint it will show up here." : "Nothing to show.") : "There are no pending complaints right now."}
                    action={
                      persona === "member" && canFile ? (
                        <HrefLink href="/complaints/new">
                          <Button className="gradient-brand text-white">
                            <FilePlus2 /> File a complaint
                          </Button>
                        </HrefLink>
                      ) : undefined
                    }
                  />
                )}
              </CardContent>
            </Card>
          )}
        </div>

        <div className="space-y-6">
          <AttentionCard items={attention} loading={showStats && stats.isLoading} />

          {persona === "member" && canFaces && <FaceStatusCard />}

          <Card>
            <CardHeader>
              <CardTitle>Quick actions</CardTitle>
            </CardHeader>
            <CardContent className="grid gap-2 sm:grid-cols-2 xl:grid-cols-1">
              {quick.map((action, index) => (
                <motion.div key={action.href + action.label} initial={{ opacity: 0, x: 16 }} animate={{ opacity: 1, x: 0 }} transition={{ delay: 0.05 + index * 0.04 }}>
                  <HrefLink href={action.href} className="group flex items-center gap-3.5 rounded-2xl border p-3 transition hover:border-primary/40 hover:bg-primary/5">
                    <span className="grid size-9 place-items-center rounded-xl gradient-brand text-white shadow-md shadow-primary/25 transition group-hover:scale-105">
                      <action.icon className="size-4" />
                    </span>
                    <span className="min-w-0 flex-1">
                      <span className="block text-sm font-medium">{action.label}</span>
                      <span className="block truncate text-xs text-muted-foreground">{action.text}</span>
                    </span>
                    <ArrowUpRight className="size-4 text-muted-foreground transition group-hover:text-primary" />
                  </HrefLink>
                </motion.div>
              ))}
            </CardContent>
          </Card>

          <AccessCard persona={persona} />

          <Card>
            <CardHeader>
              <div>
                <CardTitle>Latest notifications</CardTitle>
                <CardDescription>{unread > 0 ? `${unread} unread` : "Realtime updates for you"}</CardDescription>
              </div>
              <HrefLink href="/notifications" className="text-sm font-medium text-primary hover:underline">
                Inbox
              </HrefLink>
            </CardHeader>
            <CardContent className="space-y-1 px-3">
              {notifications.isLoading ? (
                <Skeleton className="h-24" />
              ) : notifications.data && notifications.data.content.length > 0 ? (
                notifications.data.content.map((notification) => (
                  <HrefLink key={notification.id} href={notification.data?.route ?? "/notifications"} className="flex gap-3 rounded-xl p-2.5 transition hover:bg-muted/60">
                    <span className={`mt-1.5 size-2 shrink-0 rounded-full ${notification.readAt ? "bg-muted-foreground/30" : "gradient-brand"}`} />
                    <span className="min-w-0">
                      <span className="block truncate text-sm font-medium">{notification.title}</span>
                      <span className="block truncate text-xs text-muted-foreground">{notification.body}</span>
                      <span className="text-[11px] text-muted-foreground/80">{timeAgo(notification.createdAt)}</span>
                    </span>
                  </HrefLink>
                ))
              ) : (
                <EmptyState icon={<Bell className="size-7" />} title="All caught up" description="New notifications will appear here." />
              )}
            </CardContent>
          </Card>
        </div>
      </div>
    </div>
  );
}
