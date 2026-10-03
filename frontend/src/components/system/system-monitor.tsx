import { useEffect, useState } from "react";
import { Activity, ArrowDown, ArrowUp, Boxes, Cpu, Database, FolderArchive, Gauge, HardDrive, MemoryStick, Network, Server, TriangleAlert, Zap } from "lucide-react";
import { cn } from "@/lib/utils";
import { formatBytes } from "@/lib/format";
import {
  formatCompact,
  formatDuration,
  formatPercent,
  formatPerSecond,
  formatRate,
  severity,
  useSystemMonitor,
  type DatabaseView,
  type HistoryPoint,
  type HostStats,
  type RedisStats,
  type ServiceStats,
  type StorageStats,
} from "@/lib/system";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { Badge } from "@/components/ui/badge";
import { Hint } from "@/components/ui/hint";
import { EmptyState, PageHeader, Skeleton, Table, Td, Th } from "@/components/ui/misc";
import { CoreBars, LiveChart, Meter, Sparkline } from "./monitor-charts";

const INBOUND = "var(--series-inbound)";
const OUTBOUND = "var(--series-outbound)";

export function SystemMonitor() {
  const { latest, history, status } = useSystemMonitor();
  const header = (
    <PageHeader
      title="System monitor"
      description="Live resource usage across the host, every service, the database and the cache."
      icon={<Activity className="size-5" />}
      actions={<LiveIndicator status={status} timestamp={latest?.timestamp ?? null} />}
    />
  );

  if (!latest) {
    return (
      <div>
        {header}
        {status === "offline" ? (
          <EmptyState icon={<Server className="size-6" />} title="Monitoring is unavailable" description="The monitor service is not reachable right now. This page reconnects on its own." />
        ) : (
          <div className="space-y-6">
            <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-6">
              {Array.from({ length: 6 }, (_, index) => (
                <Skeleton key={index} className="h-32" />
              ))}
            </div>
            <div className="grid gap-6 lg:grid-cols-2">
              <Skeleton className="h-72" />
              <Skeleton className="h-72" />
            </div>
          </div>
        )}
      </div>
    );
  }

  const { host, services, database, redis, storage } = latest;
  const timestamps = history.map((point) => point.timestamp);

  return (
    <div className="space-y-6">
      {header}
      {latest.issues.length > 0 && (
        <Alert variant="destructive">
          <TriangleAlert />
          <AlertTitle>Some metrics are unavailable</AlertTitle>
          <AlertDescription>
            <ul className="list-disc pl-4">
              {latest.issues.map((issue) => (
                <li key={issue}>{issue}</li>
              ))}
            </ul>
          </AlertDescription>
        </Alert>
      )}

      <KpiRow host={host} services={services} history={history} />

      <div className="grid gap-6 lg:grid-cols-2 [&>*]:min-w-0">
        <ChartCard title="CPU and memory" description="Share of the host in use, last 5 minutes">
          <LiveChart
            label="CPU and memory"
            timestamps={timestamps}
            max={1}
            format={(value) => formatPercent(value)}
            series={[
              { key: "cpu", label: "CPU", color: INBOUND, values: history.map((point) => point.cpu) },
              { key: "memory", label: "Memory", color: OUTBOUND, values: history.map((point) => point.memory) },
            ]}
          />
        </ChartCard>
        <ChartCard title="Network throughput" description="All physical interfaces, last 5 minutes">
          <LiveChart
            label="Network throughput"
            timestamps={timestamps}
            format={formatRate}
            series={[
              { key: "rx", label: "Received", color: INBOUND, values: history.map((point) => point.networkRx) },
              { key: "tx", label: "Sent", color: OUTBOUND, values: history.map((point) => point.networkTx) },
            ]}
          />
        </ChartCard>
        <ChartCard title="API requests" description="Requests per second across all services" value={formatPerSecond(latest.point.requests)}>
          <LiveChart
            label="API requests per second"
            timestamps={timestamps}
            format={(value) => formatPerSecond(value)}
            series={[{ key: "requests", label: "Requests", color: INBOUND, values: history.map((point) => point.requests) }]}
          />
        </ChartCard>
        <ChartCard title="Disk I/O" description="Bytes read and written, all disks">
          <LiveChart
            label="Disk I/O"
            timestamps={timestamps}
            format={formatRate}
            series={[
              { key: "read", label: "Read", color: INBOUND, values: history.map((point) => point.diskRead) },
              { key: "write", label: "Write", color: OUTBOUND, values: history.map((point) => point.diskWrite) },
            ]}
          />
        </ChartCard>
      </div>

      {host && <HostRow host={host} />}

      <ServicesCard services={services} history={history} />

      <div className="grid gap-6 xl:grid-cols-2 [&>*]:min-w-0">
        <DatabaseCard database={database} history={history} timestamps={timestamps} now={latest.timestamp} />
        <div className="space-y-6">
          <RedisCard redis={redis} history={history} timestamps={timestamps} />
          <StorageCard storage={storage} />
        </div>
      </div>
    </div>
  );
}

function LiveIndicator({ status, timestamp }: { status: "connecting" | "live" | "offline"; timestamp: number | null }) {
  const [now, setNow] = useState(() => Date.now());
  useEffect(() => {
    const timer = window.setInterval(() => setNow(Date.now()), 1000);
    return () => window.clearInterval(timer);
  }, []);
  const age = timestamp === null ? null : Math.max(0, Math.round((now - timestamp) / 1000));
  const label = status === "live" ? "Live" : status === "offline" ? "Reconnecting…" : "Connecting…";
  return (
    <div className="flex items-center gap-2.5 rounded-xl border bg-card/60 px-3 py-2 text-sm" role="status" aria-live="polite">
      <span className="relative flex size-2.5">
        {status === "live" && <span className="absolute inline-flex size-full animate-ping rounded-full bg-success opacity-60" />}
        <span className={cn("relative inline-flex size-2.5 rounded-full", status === "live" ? "bg-success" : status === "offline" ? "bg-warning" : "bg-muted-foreground")} />
      </span>
      <span className="font-medium">{label}</span>
      {age !== null && <span className="text-muted-foreground">· updated {age}s ago</span>}
    </div>
  );
}

function ChartCard({ title, description, value, children }: { title: string; description: string; value?: string; children: React.ReactNode }) {
  return (
    <Card>
      <CardHeader>
        <div>
          <CardTitle>{title}</CardTitle>
          <CardDescription>{description}</CardDescription>
        </div>
        {value && <span className="text-lg font-semibold">{value}</span>}
      </CardHeader>
      <CardContent>{children}</CardContent>
    </Card>
  );
}

function StatTile({
  icon: Icon,
  label,
  value,
  detail,
  ratio,
  alert,
  trend,
}: {
  icon: React.ComponentType<{ className?: string }>;
  label: string;
  value: string;
  detail: string;
  ratio?: number | null;
  alert?: string | null;
  trend?: number[];
}) {
  const level = ratio === undefined ? "normal" : severity(ratio);
  const warning = alert ?? (level === "critical" ? "Critical" : level === "warning" ? "High" : null);
  return (
    <Card className="flex flex-col p-4">
      <div className="flex items-center justify-between gap-2">
        <span className="flex items-center gap-1.5 text-xs font-medium text-muted-foreground">
          <Icon className="size-3.5" /> {label}
        </span>
        {warning && (
          <Badge tone={level === "critical" ? "danger" : "warning"} className="gap-1 px-2 py-0">
            <TriangleAlert className="size-3" /> {warning}
          </Badge>
        )}
      </div>
      <div className="mt-2 flex flex-1 items-end justify-between gap-2">
        <div className="min-w-0">
          <p className="truncate text-2xl font-semibold tracking-tight">{value}</p>
          <p className="truncate text-xs text-muted-foreground">{detail}</p>
        </div>
        {trend && <Sparkline values={trend} label={label} />}
      </div>
      {ratio !== undefined && <Meter value={ratio} label={`${label} usage`} className="mt-3" />}
    </Card>
  );
}

function KpiRow({ host, services, history }: { host: HostStats | null; services: ServiceStats[]; history: HistoryPoint[] }) {
  const memory = host && host.memoryTotal > 0 ? host.memoryUsed / host.memoryTotal : null;
  const busiest = host?.disks.reduce<HostStats["disks"][number] | null>((top, disk) => (!top || disk.used / disk.total > top.used / top.total ? disk : top), null) ?? null;
  const up = services.filter((service) => service.status === "up").length;
  const silent = services.filter((service) => service.status === "silent").length;
  const stale = services.filter((service) => service.status === "stale").length;
  const latest = history[history.length - 1];
  const reporting = services.filter((service) => service.latencyMs !== null);
  const latency = reporting.length ? reporting.reduce((sum, service) => sum + (service.latencyMs ?? 0), 0) / reporting.length : null;
  return (
    <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3 2xl:grid-cols-6">
      <StatTile
        icon={Cpu}
        label="CPU"
        value={formatPercent(host?.cpu)}
        detail={host ? `${host.logicalCores} threads · load ${host.loadAverage[0]?.toFixed(2) ?? "—"}` : "Host metrics unavailable"}
        ratio={host?.cpu ?? null}
        trend={history.map((point) => point.cpu)}
      />
      <StatTile
        icon={MemoryStick}
        label="Memory"
        value={host ? formatBytes(host.memoryUsed) : "—"}
        detail={host ? `of ${formatBytes(host.memoryTotal)} · ${formatPercent(memory)}` : "Host metrics unavailable"}
        ratio={memory}
        trend={history.map((point) => point.memory)}
      />
      <StatTile
        icon={HardDrive}
        label="Disk"
        value={busiest ? formatPercent(busiest.used / busiest.total) : "—"}
        detail={busiest ? `${busiest.mount} · ${formatBytes(busiest.total - busiest.used)} free` : "No disks found"}
        ratio={busiest ? busiest.used / busiest.total : null}
      />
      <StatTile
        icon={Network}
        label="Network"
        value={host ? formatRate(host.networkRxRate) : "—"}
        detail={host ? `received · ${formatRate(host.networkTxRate)} sent` : "Host metrics unavailable"}
        trend={history.map((point) => point.networkRx + point.networkTx)}
      />
      <StatTile
        icon={Zap}
        label="Requests"
        value={formatPerSecond(latest?.requests ?? 0)}
        detail={`${formatPerSecond(latest?.errors ?? 0)} errors · ${latency === null ? "—" : `${Math.round(latency)} ms avg`}`}
        alert={latest && latest.errors > 0 ? "Errors" : null}
        trend={history.map((point) => point.requests)}
      />
      <StatTile
        icon={Boxes}
        label="Services"
        value={`${up}/${services.length}`}
        detail={silent + stale === 0 ? "All reporting" : [stale && `${stale} stale`, silent && `${silent} not reporting`].filter(Boolean).join(" · ")}
        alert={silent + stale > 0 ? "Attention" : null}
      />
    </div>
  );
}

function HostRow({ host }: { host: HostStats }) {
  const swap = host.swapTotal > 0 ? host.swapUsed / host.swapTotal : null;
  return (
    <div className="grid gap-6 lg:grid-cols-3 [&>*]:min-w-0">
      <Card className="lg:col-span-2">
        <CardHeader>
          <div className="min-w-0">
            <CardTitle>Processor</CardTitle>
            <CardDescription className="truncate">{host.cpuModel}</CardDescription>
          </div>
          <span className="text-lg font-semibold">{formatPercent(host.cpu)}</span>
        </CardHeader>
        <CardContent className="space-y-5">
          <CoreBars cores={host.cores} />
          <dl className="grid grid-cols-2 gap-x-6 gap-y-3 text-sm sm:grid-cols-4">
            <Fact label="Cores" value={`${host.physicalCores} physical · ${host.logicalCores} logical`} />
            <Fact label="Load average" value={host.loadAverage.map((value) => value.toFixed(2)).join(" · ")} />
            <Fact label="Processes" value={formatCompact(host.processes)} />
            <Fact label="Threads" value={formatCompact(host.threads)} />
          </dl>
        </CardContent>
      </Card>
      <Card>
        <CardHeader>
          <div className="min-w-0">
            <CardTitle>Host</CardTitle>
            <CardDescription className="truncate">
              {host.hostname} · {host.os}
            </CardDescription>
          </div>
        </CardHeader>
        <CardContent className="space-y-4 text-sm">
          <UsageRow label="Memory" used={host.memoryUsed} total={host.memoryTotal} />
          {swap !== null && <UsageRow label="Swap" used={host.swapUsed} total={host.swapTotal} />}
          {host.disks.map((disk) => (
            <UsageRow key={disk.mount} label={disk.mount} hint={disk.type} used={disk.used} total={disk.total} />
          ))}
          <div className="space-y-2 border-t pt-4">
            {host.networks.map((network) => (
              <div key={network.name} className="flex items-center justify-between gap-3">
                <span className="min-w-0 truncate">
                  <span className="font-medium">{network.name}</span>
                  {network.address && <span className="text-muted-foreground"> · {network.address}</span>}
                </span>
                <span className="flex shrink-0 items-center gap-2 text-xs tabular-nums text-muted-foreground">
                  <ArrowDown className="size-3" aria-label="Received" />
                  {formatRate(network.rxRate)}
                  <ArrowUp className="size-3" aria-label="Sent" />
                  {formatRate(network.txRate)}
                </span>
              </div>
            ))}
            <p className="text-xs text-muted-foreground">Up {formatDuration(host.uptimeSeconds)}</p>
          </div>
        </CardContent>
      </Card>
    </div>
  );
}

function Fact({ label, value }: { label: string; value: string }) {
  return (
    <div className="min-w-0">
      <dt className="text-xs text-muted-foreground">{label}</dt>
      <dd className="truncate font-medium">{value}</dd>
    </div>
  );
}

function UsageRow({ label, hint, used, total }: { label: string; hint?: string; used: number; total: number }) {
  const ratio = total > 0 ? used / total : null;
  return (
    <div>
      <div className="mb-1.5 flex items-baseline justify-between gap-3">
        <span className="min-w-0 truncate font-medium">
          {label}
          {hint && <span className="font-normal text-muted-foreground"> · {hint}</span>}
        </span>
        <span className="shrink-0 text-xs tabular-nums text-muted-foreground">
          {formatBytes(used)} of {formatBytes(total)} · <span className="font-medium text-foreground">{formatPercent(ratio)}</span>
        </span>
      </div>
      <Meter value={ratio} label={`${label} usage`} />
    </div>
  );
}

const STATUS: Record<ServiceStats["status"], { tone: "success" | "warning" | "neutral"; label: string; hint: string }> = {
  up: { tone: "success", label: "Up", hint: "Reporting metrics every 2 seconds" },
  stale: { tone: "warning", label: "Stale", hint: "No fresh metrics for more than 6 seconds" },
  silent: { tone: "neutral", label: "Not reporting", hint: "Registered in discovery but not sending metrics. Restart it on the latest build." },
};

function ServicesCard({ services, history }: { services: ServiceStats[]; history: HistoryPoint[] }) {
  return (
    <Card>
      <CardHeader>
        <div>
          <CardTitle>Services</CardTitle>
          <CardDescription>Every running instance with its process, JVM and traffic figures.</CardDescription>
        </div>
      </CardHeader>
      <CardContent className="px-0">
        {services.length === 0 ? (
          <p className="px-5 text-sm text-muted-foreground">No service has reported yet.</p>
        ) : (
          <Table>
            <thead>
              <tr className="border-b">
                <Th>Service</Th>
                <Th>Status</Th>
                <Th>CPU</Th>
                <Th>Heap</Th>
                <Th className="text-right">Memory</Th>
                <Th className="text-right">Threads</Th>
                <Th className="text-right">Requests</Th>
                <Th className="text-right">Latency</Th>
                <Th className="text-right">Errors</Th>
                <Th className="text-right">DB pool</Th>
                <Th className="text-right">Uptime</Th>
                <Th>CPU trend</Th>
              </tr>
            </thead>
            <tbody>
              {services.map((service) => {
                const status = STATUS[service.status];
                const heap = service.heapUsed !== null && service.heapMax ? service.heapUsed / service.heapMax : null;
                return (
                  <tr key={service.instanceId} className="border-b last:border-0">
                    <Td>
                      <p className="font-medium">{service.service}</p>
                      <p className="text-xs text-muted-foreground tabular-nums">
                        {service.host}:{service.port}
                        {!service.registered && service.status !== "silent" && service.service !== "discovery-server" && " · not in registry"}
                      </p>
                    </Td>
                    <Td>
                      <Hint label={status.hint}>
                        <span tabIndex={0} className="inline-flex rounded-full outline-none focus-visible:ring-2 focus-visible:ring-ring/50">
                          <Badge tone={status.tone} dot>
                            {status.label}
                          </Badge>
                        </span>
                      </Hint>
                    </Td>
                    <Td className="min-w-28">
                      {service.cpu === null ? (
                        <span className="text-muted-foreground">—</span>
                      ) : (
                        <div className="space-y-1">
                          <span className="tabular-nums">{formatPercent(service.cpu, 1)}</span>
                          <Meter value={service.cpu} label={`${service.service} CPU`} className="w-20" />
                        </div>
                      )}
                    </Td>
                    <Td className="min-w-36">
                      {service.heapUsed === null ? (
                        <span className="text-muted-foreground">—</span>
                      ) : (
                        <div className="space-y-1">
                          <span className="tabular-nums">
                            {formatBytes(service.heapUsed)}
                            {service.heapMax ? <span className="text-muted-foreground"> / {formatBytes(service.heapMax)}</span> : null}
                          </span>
                          {heap !== null && <Meter value={heap} label={`${service.service} heap`} className="w-28" />}
                        </div>
                      )}
                    </Td>
                    <Td className="text-right tabular-nums">{service.rss === null ? "—" : formatBytes(service.rss)}</Td>
                    <Td className="text-right tabular-nums">{service.threads ?? "—"}</Td>
                    <Td className="text-right tabular-nums">{formatPerSecond(service.requestRate)}</Td>
                    <Td className="text-right tabular-nums">{service.latencyMs === null ? "—" : `${Math.round(service.latencyMs)} ms`}</Td>
                    <Td className={cn("text-right tabular-nums", (service.errorRate ?? 0) > 0 && "font-medium text-destructive")}>{formatPerSecond(service.errorRate)}</Td>
                    <Td className="text-right tabular-nums">
                      {service.dbPoolMax === null ? (
                        <span className="text-muted-foreground">—</span>
                      ) : (
                        <>
                          {service.dbPoolAcquired}/{service.dbPoolMax}
                          {(service.dbPoolPending ?? 0) > 0 && <span className="text-warning"> · {service.dbPoolPending} waiting</span>}
                        </>
                      )}
                    </Td>
                    <Td className="text-right tabular-nums">{service.uptimeMs === null ? "—" : formatDuration(service.uptimeMs / 1000)}</Td>
                    <Td>{service.cpu === null ? null : <Sparkline values={history.map((point) => point.serviceCpu[service.instanceId] ?? 0)} label={`${service.service} CPU`} />}</Td>
                  </tr>
                );
              })}
            </tbody>
          </Table>
        )}
      </CardContent>
    </Card>
  );
}

function Stat({ label, value, hint }: { label: string; value: string; hint?: string }) {
  return (
    <div className="min-w-0 rounded-xl border bg-muted/20 px-3 py-2.5">
      <p className="text-xs text-muted-foreground">{label}</p>
      <p className="truncate text-base font-semibold">{value}</p>
      {hint && <p className="truncate text-[11px] text-muted-foreground">{hint}</p>}
    </div>
  );
}

function Unavailable({ icon, title }: { icon: React.ReactNode; title: string }) {
  return (
    <Card>
      <CardContent className="py-8">
        <EmptyState icon={icon} title={title} description="The latest sample could not reach it. This updates on its own." />
      </CardContent>
    </Card>
  );
}

function DatabaseCard({ database, history, timestamps, now }: { database: DatabaseView | null; history: HistoryPoint[]; timestamps: number[]; now: number }) {
  if (!database) return <Unavailable icon={<Database className="size-6" />} title="Database metrics unavailable" />;
  const connections = database.maxConnections > 0 ? database.connections / database.maxConnections : null;
  const uptime = (now - new Date(database.startedAt).getTime()) / 1000;
  const largest = Math.max(1, ...database.tables.map((table) => table.bytes));
  return (
    <Card>
      <CardHeader>
        <div className="min-w-0">
          <CardTitle className="flex items-center gap-2">
            <Database className="size-4 text-muted-foreground" /> PostgreSQL
          </CardTitle>
          <CardDescription className="truncate">
            Version {database.version.split(" ")[0]} · up {formatDuration(uptime)} · {formatBytes(database.sizeBytes)}
          </CardDescription>
        </div>
      </CardHeader>
      <CardContent className="space-y-5">
        <div>
          <div className="mb-1.5 flex items-baseline justify-between text-sm">
            <span className="font-medium">Connections</span>
            <span className="text-xs tabular-nums text-muted-foreground">
              <span className="font-medium text-foreground">{database.connections}</span> of {database.maxConnections}
            </span>
          </div>
          <Meter value={connections} label="Database connections" />
          <p className="mt-2 text-xs text-muted-foreground tabular-nums">
            {database.active} active · {database.idle} idle · {database.idleInTransaction} idle in transaction · {database.waiting} waiting on locks
          </p>
        </div>
        <div className="grid grid-cols-2 gap-3 sm:grid-cols-4">
          <Stat label="Transactions" value={formatPerSecond(database.transactionRate)} hint={`${formatPerSecond(database.rollbackRate)} rolled back`} />
          <Stat label="Cache hit" value={formatPercent(database.cacheHitRatio, 1)} />
          <Stat label="Rows read" value={formatPerSecond(database.rowsReadRate)} />
          <Stat label="Rows written" value={formatPerSecond(database.rowsWrittenRate)} />
          <Stat label="Longest query" value={database.longestQuerySeconds > 0 ? formatDuration(database.longestQuerySeconds) : "None running"} />
          <Stat label="Deadlocks" value={formatCompact(database.deadlocks)} hint="since stats reset" />
          <Stat label="Temp files" value={formatBytes(database.tempBytes)} hint="since stats reset" />
          <Stat label="Size" value={formatBytes(database.sizeBytes)} />
        </div>
        <div>
          <p className="mb-2 text-sm font-medium">Transactions per second</p>
          <LiveChart
            label="Database transactions per second"
            timestamps={timestamps}
            height={140}
            format={(value) => formatPerSecond(value)}
            series={[{ key: "tps", label: "Transactions", color: INBOUND, values: history.map((point) => point.dbTransactions) }]}
          />
        </div>
        <div>
          <p className="mb-3 text-sm font-medium">Largest tables</p>
          <ul className="space-y-3">
            {database.tables.map((table) => (
              <li key={table.name}>
                <div className="mb-1 flex items-baseline justify-between gap-3 text-sm">
                  <span className="truncate text-muted-foreground">{table.name}</span>
                  <span className="shrink-0 text-xs tabular-nums text-muted-foreground">
                    <span className="font-medium text-foreground">{formatBytes(table.bytes)}</span> · {formatCompact(table.rows)} rows
                  </span>
                </div>
                <div className="h-2 overflow-hidden rounded-full bg-muted">
                  <div className="h-full rounded-full transition-[width] duration-700" style={{ width: `${(table.bytes / largest) * 100}%`, background: INBOUND }} />
                </div>
              </li>
            ))}
          </ul>
        </div>
      </CardContent>
    </Card>
  );
}

function RedisCard({ redis, history, timestamps }: { redis: RedisStats | null; history: HistoryPoint[]; timestamps: number[] }) {
  if (!redis) return <Unavailable icon={<Gauge className="size-6" />} title="Cache metrics unavailable" />;
  const limit = redis.maxMemory > 0 ? redis.maxMemory : null;
  return (
    <Card>
      <CardHeader>
        <div className="min-w-0">
          <CardTitle className="flex items-center gap-2">
            <Gauge className="size-4 text-muted-foreground" /> Redis
          </CardTitle>
          <CardDescription className="truncate">
            Version {redis.version ?? "—"} · {redis.role ?? "—"} · up {formatDuration(redis.uptimeSeconds)}
          </CardDescription>
        </div>
      </CardHeader>
      <CardContent className="space-y-5">
        {limit ? (
          <UsageRow label="Memory" used={redis.usedMemory} total={limit} />
        ) : (
          <div className="flex items-baseline justify-between text-sm">
            <span className="font-medium">Memory</span>
            <span className="text-xs tabular-nums text-muted-foreground">
              <span className="font-medium text-foreground">{formatBytes(redis.usedMemory)}</span> used · peak {formatBytes(redis.peakMemory)} · no limit set
            </span>
          </div>
        )}
        <div className="grid grid-cols-2 gap-3 sm:grid-cols-3">
          <Stat label="Commands" value={formatPerSecond(redis.opsPerSecond)} />
          <Stat label="Hit ratio" value={formatPercent(redis.hitRatio, 1)} />
          <Stat label="Clients" value={formatCompact(redis.connectedClients)} hint={`${redis.blockedClients} blocked`} />
          <Stat label="Keys" value={formatCompact(redis.keys)} hint={`${formatCompact(redis.expiringKeys)} expiring`} />
          <Stat label="Evicted" value={formatCompact(redis.evictedKeys)} />
          <Stat label="Network" value={`${redis.inputKbps.toFixed(1)} KB/s`} hint={`${redis.outputKbps.toFixed(1)} KB/s out`} />
        </div>
        <div>
          <p className="mb-2 text-sm font-medium">Commands per second</p>
          <LiveChart
            label="Redis commands per second"
            timestamps={timestamps}
            height={140}
            format={(value) => formatPerSecond(value)}
            series={[{ key: "ops", label: "Commands", color: INBOUND, values: history.map((point) => point.redisOps) }]}
          />
        </div>
      </CardContent>
    </Card>
  );
}

function StorageCard({ storage }: { storage: StorageStats | null }) {
  return (
    <Card>
      <CardHeader>
        <div>
          <CardTitle className="flex items-center gap-2">
            <FolderArchive className="size-4 text-muted-foreground" /> File storage
          </CardTitle>
          <CardDescription>Complaint attachments and evidence. Refreshed every 30 seconds.</CardDescription>
        </div>
      </CardHeader>
      <CardContent>
        {storage ? (
          <div className="grid grid-cols-2 gap-3 sm:grid-cols-4">
            <Stat label="Stored" value={formatBytes(storage.bytes)} />
            <Stat label="Files" value={formatCompact(storage.files)} />
            <Stat label="Evidence" value={formatCompact(storage.evidenceFiles)} />
            <Stat label="Pending uploads" value={formatCompact(storage.pendingFiles)} />
          </div>
        ) : (
          <p className="text-sm text-muted-foreground">Storage figures are not available yet.</p>
        )}
      </CardContent>
    </Card>
  );
}
