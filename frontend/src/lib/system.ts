import { useEffect, useState } from "react";
import { openStream } from "./stream";
import { formatBytes } from "./format";

export type HostStats = {
  hostname: string;
  os: string;
  cpuModel: string;
  physicalCores: number;
  logicalCores: number;
  uptimeSeconds: number;
  processes: number;
  threads: number;
  cpu: number;
  cores: number[];
  loadAverage: number[];
  memoryTotal: number;
  memoryUsed: number;
  swapTotal: number;
  swapUsed: number;
  disks: { mount: string; type: string; total: number; used: number }[];
  diskReadRate: number;
  diskWriteRate: number;
  networks: { name: string; address: string | null; rxRate: number; txRate: number; speed: number }[];
  networkRxRate: number;
  networkTxRate: number;
};

export type ServiceStatus = "up" | "stale" | "silent";

export type ServiceStats = {
  service: string;
  instanceId: string;
  host: string;
  port: number;
  status: ServiceStatus;
  registered: boolean;
  lastSeenMs: number | null;
  uptimeMs: number | null;
  cpu: number | null;
  heapUsed: number | null;
  heapMax: number | null;
  nonHeapUsed: number | null;
  rss: number | null;
  threads: number | null;
  requestRate: number | null;
  errorRate: number | null;
  latencyMs: number | null;
  gcRate: number | null;
  dbPoolAcquired: number | null;
  dbPoolPending: number | null;
  dbPoolMax: number | null;
};

export type DatabaseView = {
  version: string;
  startedAt: string;
  sizeBytes: number;
  maxConnections: number;
  connections: number;
  active: number;
  idle: number;
  idleInTransaction: number;
  waiting: number;
  longestQuerySeconds: number;
  transactionRate: number;
  rollbackRate: number;
  cacheHitRatio: number;
  rowsReadRate: number;
  rowsWrittenRate: number;
  deadlocks: number;
  tempBytes: number;
  tables: { name: string; bytes: number; rows: number }[];
};

export type RedisStats = {
  version: string | null;
  role: string | null;
  uptimeSeconds: number;
  connectedClients: number;
  blockedClients: number;
  usedMemory: number;
  peakMemory: number;
  maxMemory: number;
  opsPerSecond: number;
  hitRatio: number;
  keys: number;
  expiringKeys: number;
  evictedKeys: number;
  inputKbps: number;
  outputKbps: number;
};

export type StorageStats = { files: number; bytes: number; pendingFiles: number; evidenceFiles: number };

export type HistoryPoint = {
  timestamp: number;
  cpu: number;
  memory: number;
  networkRx: number;
  networkTx: number;
  diskRead: number;
  diskWrite: number;
  requests: number;
  errors: number;
  dbConnections: number;
  dbTransactions: number;
  redisOps: number;
  serviceCpu: Record<string, number>;
  serviceHeap: Record<string, number>;
};

export type SystemSnapshot = {
  timestamp: number;
  host: HostStats | null;
  services: ServiceStats[];
  database: DatabaseView | null;
  redis: RedisStats | null;
  storage: StorageStats | null;
  point: HistoryPoint;
  issues: string[];
};

type SystemOverview = { intervalSeconds: number; latest: SystemSnapshot | null; history: HistoryPoint[] };

export type MonitorState = {
  latest: SystemSnapshot | null;
  history: HistoryPoint[];
  status: "connecting" | "live" | "offline";
  intervalSeconds: number;
};

const MAX_POINTS = 150;

function parse<T>(event: Event): T | null {
  try {
    return JSON.parse((event as MessageEvent).data) as T;
  } catch {
    return null;
  }
}

export function useSystemMonitor(): MonitorState {
  const [state, setState] = useState<MonitorState>({ latest: null, history: [], status: "connecting", intervalSeconds: 2 });

  useEffect(
    () =>
      openStream("/system/stream", (source) => {
        source.addEventListener("overview", (event) => {
          const overview = parse<SystemOverview>(event);
          if (!overview) return;
          setState({ latest: overview.latest, history: overview.history.slice(-MAX_POINTS), status: "live", intervalSeconds: overview.intervalSeconds });
        });
        source.addEventListener("snapshot", (event) => {
          const snapshot = parse<SystemSnapshot>(event);
          if (!snapshot) return;
          setState((current) => ({ ...current, latest: snapshot, status: "live", history: [...current.history, snapshot.point].slice(-MAX_POINTS) }));
        });
        source.addEventListener("error", () => setState((current) => ({ ...current, status: "offline" })));
      }),
    [],
  );

  return state;
}

export function formatPercent(ratio: number | null | undefined, digits = 0): string {
  if (ratio === null || ratio === undefined || !Number.isFinite(ratio)) return "—";
  return `${(ratio * 100).toFixed(digits)}%`;
}

export function formatRate(bytesPerSecond: number): string {
  return `${formatBytes(Math.max(0, Math.round(bytesPerSecond)))}/s`;
}

const compact = new Intl.NumberFormat("en", { notation: "compact", maximumFractionDigits: 1 });

export function formatCompact(value: number | null | undefined): string {
  if (value === null || value === undefined || !Number.isFinite(value)) return "—";
  return compact.format(value);
}

export function formatPerSecond(value: number | null | undefined): string {
  if (value === null || value === undefined || !Number.isFinite(value)) return "—";
  return `${value < 10 ? value.toFixed(1) : compact.format(value)}/s`;
}

export function formatDuration(seconds: number | null | undefined): string {
  if (seconds === null || seconds === undefined || !Number.isFinite(seconds)) return "—";
  const total = Math.max(0, Math.floor(seconds));
  const days = Math.floor(total / 86400);
  const hours = Math.floor((total % 86400) / 3600);
  const minutes = Math.floor((total % 3600) / 60);
  if (days > 0) return `${days}d ${hours}h`;
  if (hours > 0) return `${hours}h ${minutes}m`;
  if (minutes > 0) return `${minutes}m ${total % 60}s`;
  return `${total}s`;
}

export type Severity = "normal" | "warning" | "critical";

export function severity(ratio: number | null | undefined): Severity {
  if (ratio === null || ratio === undefined || !Number.isFinite(ratio)) return "normal";
  if (ratio >= 0.9) return "critical";
  if (ratio >= 0.75) return "warning";
  return "normal";
}
