import { useId, useState } from "react";
import { Table2, LineChart } from "lucide-react";
import { cn } from "@/lib/utils";
import { severity } from "@/lib/system";
import { Hint } from "@/components/ui/hint";

export type LiveSeries = { key: string; label: string; color: string; values: number[] };

const WIDTH = 640;
const GAP_MS = 7_000;

function niceTop(value: number): number {
  if (value <= 0) return 1;
  const magnitude = 10 ** Math.floor(Math.log10(value));
  const scaled = value / magnitude;
  const step = scaled <= 1 ? 1 : scaled <= 2 ? 2 : scaled <= 2.5 ? 2.5 : scaled <= 5 ? 5 : 10;
  return step * magnitude;
}

function clock(timestamp: number): string {
  return new Date(timestamp).toLocaleTimeString([], { hour: "2-digit", minute: "2-digit", second: "2-digit" });
}

export function LiveChart({
  label,
  timestamps,
  series,
  format,
  max,
  height = 180,
  windowMs = 300_000,
}: {
  label: string;
  timestamps: number[];
  series: LiveSeries[];
  format: (value: number) => string;
  max?: number;
  height?: number;
  windowMs?: number;
}) {
  const [active, setActive] = useState<number | null>(null);
  const [table, setTable] = useState(false);
  const gradient = useId();
  const pad = { top: 12, right: 14, bottom: 26, left: 70 };
  const innerW = WIDTH - pad.left - pad.right;
  const innerH = height - pad.top - pad.bottom;
  const end = timestamps[timestamps.length - 1] ?? 0;
  const start = end - windowMs;
  const peak = Math.max(0, ...series.flatMap((item) => item.values));
  const top = max ?? niceTop(peak * 1.15);
  const x = (timestamp: number) => pad.left + ((timestamp - start) / windowMs) * innerW;
  const y = (value: number) => pad.top + innerH - (Math.min(Math.max(value, 0), top) / top) * innerH;
  const visible = timestamps.map((timestamp, index) => ({ timestamp, index })).filter((point) => point.timestamp >= start);
  const ticks = [0, top / 2, top];
  const minutes = Array.from({ length: Math.floor(windowMs / 60_000) + 1 }, (_, index) => end - index * 60_000).filter((timestamp) => timestamp >= start);
  const single = series.length === 1;
  const last = timestamps.length - 1;

  const path = (values: number[]) => {
    let d = "";
    let previous: number | null = null;
    for (const { timestamp, index } of visible) {
      const command = previous === null || timestamp - previous > GAP_MS ? "M" : "L";
      d += `${command}${x(timestamp).toFixed(1)},${y(values[index] ?? 0).toFixed(1)}`;
      previous = timestamp;
    }
    return d;
  };

  const area = (values: number[]) => {
    if (visible.length < 2) return "";
    const first = visible[0];
    const final = visible[visible.length - 1];
    return `${path(values)}L${x(final.timestamp).toFixed(1)},${y(0)}L${x(first.timestamp).toFixed(1)},${y(0)}Z`;
  };

  const pick = (clientX: number, rect: DOMRect) => {
    if (visible.length === 0) return;
    const svgX = ((clientX - rect.left) / rect.width) * WIDTH;
    const target = start + ((svgX - pad.left) / innerW) * windowMs;
    let best = visible[0];
    for (const point of visible) if (Math.abs(point.timestamp - target) < Math.abs(best.timestamp - target)) best = point;
    setActive(best.index);
  };

  const move = (delta: number) => {
    if (visible.length === 0) return;
    const indexes = visible.map((point) => point.index);
    const position = active === null ? indexes.length - 1 : Math.max(0, Math.min(indexes.length - 1, indexes.indexOf(active) + delta));
    setActive(indexes[position]);
  };

  if (timestamps.length === 0) {
    return <p className="grid place-items-center text-sm text-muted-foreground" style={{ height }}>Waiting for the first samples…</p>;
  }

  return (
    <div className="space-y-2">
      <div className="flex flex-wrap items-center justify-between gap-2">
        {single ? (
          <span />
        ) : (
          <ul className="flex flex-wrap gap-x-4 gap-y-1 text-xs" aria-label="Legend">
            {series.map((item) => (
              <li key={item.key} className="flex items-center gap-2 text-muted-foreground">
                <svg width="16" height="6" aria-hidden>
                  <line x1="1" y1="3" x2="15" y2="3" stroke={item.color} strokeWidth="2" strokeLinecap="round" />
                </svg>
                {item.label}
                <span className="font-medium tabular-nums text-foreground">{format(item.values[last] ?? 0)}</span>
              </li>
            ))}
          </ul>
        )}
        <Hint label={table ? "Show chart" : "Show as table"}>
          <button
            type="button"
            onClick={() => setTable((current) => !current)}
            className="grid size-7 place-items-center rounded-lg text-muted-foreground transition hover:bg-muted hover:text-foreground"
            aria-label={table ? `Show ${label} chart` : `Show ${label} as table`}
            aria-pressed={table}
          >
            {table ? <LineChart className="size-4" /> : <Table2 className="size-4" />}
          </button>
        </Hint>
      </div>
      {table ? (
        <div className="max-h-[220px] overflow-y-auto rounded-xl border scrollbar-thin">
          <table className="w-full text-xs">
            <caption className="sr-only">{label}, most recent samples first</caption>
            <thead className="sticky top-0 bg-card">
              <tr>
                <th className="px-3 py-2 text-left font-medium text-muted-foreground">Time</th>
                {series.map((item) => (
                  <th key={item.key} className="px-3 py-2 text-right font-medium text-muted-foreground">
                    {item.label}
                  </th>
                ))}
              </tr>
            </thead>
            <tbody>
              {timestamps
                .map((timestamp, index) => ({ timestamp, index }))
                .slice(-30)
                .reverse()
                .map(({ timestamp, index }) => (
                  <tr key={timestamp} className="border-t">
                    <td className="px-3 py-1.5 tabular-nums text-muted-foreground">{clock(timestamp)}</td>
                    {series.map((item) => (
                      <td key={item.key} className="px-3 py-1.5 text-right tabular-nums">
                        {format(item.values[index] ?? 0)}
                      </td>
                    ))}
                  </tr>
                ))}
            </tbody>
          </table>
        </div>
      ) : (
        <div className="relative">
          <svg
            viewBox={`0 0 ${WIDTH} ${height}`}
            className="w-full touch-none select-none outline-none focus-visible:ring-2 focus-visible:ring-ring/50 rounded-lg"
            role="img"
            aria-label={`${label}: ${series.map((item) => `${item.label} ${format(item.values[last] ?? 0)}`).join(", ")}. Use arrow keys to inspect samples.`}
            tabIndex={0}
            onPointerMove={(event) => pick(event.clientX, event.currentTarget.getBoundingClientRect())}
            onPointerLeave={() => setActive(null)}
            onKeyDown={(event) => {
              if (event.key === "ArrowLeft") move(-1);
              if (event.key === "ArrowRight") move(1);
              if (event.key === "Escape") setActive(null);
            }}
            onBlur={() => setActive(null)}
          >
            <defs>
              {single && (
                <linearGradient id={gradient} x1="0" x2="0" y1="0" y2="1">
                  <stop offset="0%" stopColor={series[0].color} stopOpacity="0.14" />
                  <stop offset="100%" stopColor={series[0].color} stopOpacity="0.04" />
                </linearGradient>
              )}
            </defs>
            {ticks.map((tick) => (
              <g key={tick}>
                <line x1={pad.left} x2={WIDTH - pad.right} y1={y(tick)} y2={y(tick)} stroke="var(--border)" strokeWidth="1" />
                <text x={pad.left - 8} y={y(tick)} dy="0.32em" textAnchor="end" className="fill-muted-foreground text-[11px] tabular-nums">
                  {format(tick)}
                </text>
              </g>
            ))}
            {minutes.map((timestamp, index) => (
              <text key={timestamp} x={x(timestamp)} y={height - 7} textAnchor={index === 0 ? "end" : "middle"} className="fill-muted-foreground text-[11px]">
                {index === 0 ? "now" : `-${index}m`}
              </text>
            ))}
            {single && <path d={area(series[0].values)} fill={`url(#${gradient})`} />}
            {series.map((item) => (
              <path key={item.key} d={path(item.values)} fill="none" stroke={item.color} strokeWidth="2" strokeLinejoin="round" strokeLinecap="round" />
            ))}
            {active === null &&
              last >= 0 &&
              series.map((item) => <circle key={item.key} cx={x(timestamps[last])} cy={y(item.values[last] ?? 0)} r="4" fill={item.color} stroke="var(--card)" strokeWidth="2" />)}
            {active !== null && (
              <>
                <line x1={x(timestamps[active])} x2={x(timestamps[active])} y1={pad.top} y2={pad.top + innerH} stroke="var(--muted-foreground)" strokeOpacity="0.5" strokeWidth="1" />
                {series.map((item) => (
                  <circle key={item.key} cx={x(timestamps[active])} cy={y(item.values[active] ?? 0)} r="4.5" fill={item.color} stroke="var(--card)" strokeWidth="2" />
                ))}
              </>
            )}
          </svg>
          {active !== null && (
            <div
              className="pointer-events-none absolute top-1 z-10 min-w-36 rounded-xl border bg-popover px-3 py-2 text-xs shadow-lg"
              style={{
                left: `${(x(timestamps[active]) / WIDTH) * 100}%`,
                transform: `translateX(${x(timestamps[active]) > WIDTH / 2 ? "calc(-100% - 12px)" : "12px"})`,
              }}
            >
              <p className="mb-1 text-muted-foreground tabular-nums">{clock(timestamps[active])}</p>
              {series.map((item) => (
                <p key={item.key} className="flex items-center justify-between gap-4">
                  <span className="text-sm font-semibold tabular-nums">{format(item.values[active] ?? 0)}</span>
                  <span className="flex items-center gap-1.5 text-muted-foreground">
                    <svg width="12" height="6" aria-hidden>
                      <line x1="1" y1="3" x2="11" y2="3" stroke={item.color} strokeWidth="2" strokeLinecap="round" />
                    </svg>
                    {item.label}
                  </span>
                </p>
              ))}
            </div>
          )}
        </div>
      )}
    </div>
  );
}

export function Sparkline({ values, label, className }: { values: number[]; label: string; className?: string }) {
  const width = 96;
  const height = 28;
  const points = values.slice(-30);
  if (points.length < 2) return <span className={cn("block h-7 w-24", className)} aria-hidden />;
  const top = Math.max(...points) || 1;
  const x = (index: number) => 2 + (index / (points.length - 1)) * (width - 6);
  const y = (value: number) => 3 + (height - 6) - (Math.max(0, value) / top) * (height - 6);
  const d = points.map((value, index) => `${index === 0 ? "M" : "L"}${x(index).toFixed(1)},${y(value).toFixed(1)}`).join("");
  const final = points.length - 1;
  return (
    <svg viewBox={`0 0 ${width} ${height}`} className={cn("h-7 w-24 shrink-0", className)} role="img" aria-label={`${label} trend over the last minute`}>
      <path d={d} fill="none" stroke="var(--muted-foreground)" strokeOpacity="0.55" strokeWidth="1.5" strokeLinejoin="round" strokeLinecap="round" />
      <circle cx={x(final)} cy={y(points[final])} r="2.5" fill="var(--primary)" stroke="var(--card)" strokeWidth="1.5" />
    </svg>
  );
}

const METER_TONES = {
  normal: { fill: "bg-primary", track: "bg-primary/15" },
  warning: { fill: "bg-warning", track: "bg-warning/20" },
  critical: { fill: "bg-destructive", track: "bg-destructive/15" },
} as const;

export function Meter({ value, label, className }: { value: number | null | undefined; label: string; className?: string }) {
  const ratio = value === null || value === undefined || !Number.isFinite(value) ? 0 : Math.min(Math.max(value, 0), 1);
  const tone = METER_TONES[severity(value)];
  return (
    <div
      role="meter"
      aria-label={label}
      aria-valuemin={0}
      aria-valuemax={100}
      aria-valuenow={Math.round(ratio * 100)}
      className={cn("h-1.5 w-full overflow-hidden rounded-full", tone.track, className)}
    >
      <div className={cn("h-full rounded-full transition-[width] duration-700 ease-out", tone.fill)} style={{ width: `${ratio * 100}%` }} />
    </div>
  );
}

export function CoreBars({ cores }: { cores: number[] }) {
  const height = 120;
  return (
    <div>
      <div className="flex items-end gap-0.5" style={{ height }} role="list" aria-label="Load per CPU core">
        {cores.map((load, index) => (
          <Hint key={index} label={`Core ${index + 1} · ${Math.round(load * 100)}%`}>
            <div role="listitem" tabIndex={0} aria-label={`Core ${index + 1}: ${Math.round(load * 100)}%`} className="group flex h-full flex-1 cursor-default items-end justify-center rounded-md outline-none focus-visible:ring-2 focus-visible:ring-ring/50">
              <div
                className="w-full max-w-6 rounded-t-[4px] transition-[height] duration-700 ease-out group-hover:brightness-110"
                style={{ height: `${Math.max(2, load * 100)}%`, background: "var(--series-inbound)" }}
              />
            </div>
          </Hint>
        ))}
      </div>
      <div className="mt-1.5 flex justify-between text-[11px] text-muted-foreground tabular-nums">
        <span>Core 1</span>
        <span>Core {cores.length}</span>
      </div>
    </div>
  );
}
