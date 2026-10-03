import { useState } from "react";
import { motion } from "motion/react";
import { AnimatedNumber } from "./motion";

export type Slice = { label: string; value: number; color: string };

export function Donut({ slices, size = 180, centerLabel }: { slices: Slice[]; size?: number; centerLabel?: string }) {
  const total = slices.reduce((sum, slice) => sum + slice.value, 0);
  const radius = size / 2 - 14;
  const circumference = 2 * Math.PI * radius;
  let offset = 0;
  return (
    <div className="flex flex-wrap items-center gap-6">
      <div className="relative shrink-0" style={{ width: size, height: size }}>
        <svg width={size} height={size} viewBox={`0 0 ${size} ${size}`} className="-rotate-90">
          <circle cx={size / 2} cy={size / 2} r={radius} fill="none" stroke="var(--muted)" strokeWidth={16} />
          {total > 0 &&
            slices.map((slice, index) => {
              const length = (slice.value / total) * circumference;
              const start = offset;
              offset += length;
              if (slice.value <= 0) return null;
              const visible = Math.max(length - 4, 0.01);
              return (
                <motion.circle
                  key={slice.label}
                  cx={size / 2}
                  cy={size / 2}
                  r={radius}
                  fill="none"
                  stroke={slice.color}
                  strokeWidth={16}
                  strokeLinecap="round"
                  strokeDashoffset={-start}
                  initial={{ strokeDasharray: `0 ${circumference}`, opacity: 0 }}
                  animate={{ strokeDasharray: `${visible} ${circumference}`, opacity: 1 }}
                  transition={{ duration: 0.9, delay: 0.15 * index, ease: "easeOut" }}
                />
              );
            })}
        </svg>
        <div className="absolute inset-0 grid place-items-center text-center">
          <div>
            <AnimatedNumber value={total} className="text-3xl font-semibold" />
            <p className="text-xs text-muted-foreground">{centerLabel ?? "total"}</p>
          </div>
        </div>
      </div>
      <ul className="space-y-2 text-sm">
        {slices.map((slice) => (
          <li key={slice.label} className="flex items-center gap-2.5">
            <span className="size-2.5 rounded-full" style={{ background: slice.color }} />
            <span className="capitalize text-muted-foreground">{slice.label}</span>
            <span className="ml-auto pl-4 font-medium tabular-nums">{slice.value}</span>
          </li>
        ))}
      </ul>
    </div>
  );
}

export function Bars({ items }: { items: Slice[] }) {
  const max = Math.max(1, ...items.map((item) => item.value));
  return (
    <ul className="space-y-3.5">
      {items.map((item, index) => (
        <li key={item.label}>
          <div className="mb-1.5 flex justify-between text-sm">
            <span className="capitalize text-muted-foreground">{item.label}</span>
            <span className="font-medium tabular-nums">{item.value}</span>
          </div>
          <div className="h-2.5 overflow-hidden rounded-full bg-muted">
            <motion.div
              className="h-full rounded-full"
              style={{ background: item.color }}
              initial={{ width: 0 }}
              animate={{ width: `${(item.value / max) * 100}%` }}
              transition={{ duration: 0.8, delay: 0.08 * index, ease: "easeOut" }}
            />
          </div>
        </li>
      ))}
    </ul>
  );
}

export type TrendSeries = { key: string; label: string; color: string; dashed?: boolean; values: number[] };

export function TrendChart({ labels, series, height = 220, formatLabel }: { labels: string[]; series: TrendSeries[]; height?: number; formatLabel?: (label: string) => string }) {
  const [active, setActive] = useState<number | null>(null);
  const width = 640;
  const pad = { top: 16, right: 76, bottom: 28, left: 34 };
  const innerW = width - pad.left - pad.right;
  const innerH = height - pad.top - pad.bottom;
  const peak = Math.max(1, ...series.flatMap((item) => item.values));
  const step = peak <= 4 ? 1 : Math.ceil(peak / 4);
  const top = step * Math.ceil(peak / step);
  const ticks = Array.from({ length: Math.floor(top / step) + 1 }, (_, index) => index * step);
  const x = (index: number) => pad.left + (labels.length <= 1 ? innerW / 2 : (index / (labels.length - 1)) * innerW);
  const y = (value: number) => pad.top + innerH - (value / top) * innerH;
  const fmt = formatLabel ?? ((label: string) => label);
  const pick = (clientX: number, rect: DOMRect) => {
    const ratio = (clientX - rect.left) / rect.width;
    const svgX = ratio * width;
    const index = Math.round(((svgX - pad.left) / innerW) * (labels.length - 1));
    setActive(Math.max(0, Math.min(labels.length - 1, index)));
  };
  const endLabels = series
    .map((item) => ({ item, y: y(item.values[item.values.length - 1] ?? 0) }))
    .sort((a, b) => a.y - b.y)
    .map((entry, index, all) => ({ ...entry, y: index > 0 && entry.y - all[index - 1].y < 14 ? all[index - 1].y + 14 : entry.y }));

  return (
    <div className="space-y-3">
      <ul className="flex flex-wrap gap-4 text-xs text-muted-foreground" aria-label="Legend">
        {series.map((item) => (
          <li key={item.key} className="flex items-center gap-2">
            <svg width="18" height="6" aria-hidden>
              <line x1="0" y1="3" x2="18" y2="3" stroke={item.color} strokeWidth="2" strokeDasharray={item.dashed ? "4 3" : undefined} strokeLinecap="round" />
            </svg>
            {item.label}
          </li>
        ))}
      </ul>
      <div className="relative">
        <svg
          viewBox={`0 0 ${width} ${height}`}
          className="w-full touch-none select-none"
          role="img"
          aria-label={series.map((item) => `${item.label}: ${item.values.reduce((sum, value) => sum + value, 0)} over the period`).join(", ")}
          tabIndex={0}
          onPointerMove={(event) => pick(event.clientX, event.currentTarget.getBoundingClientRect())}
          onPointerLeave={() => setActive(null)}
          onKeyDown={(event) => {
            if (event.key === "ArrowRight") setActive((current) => Math.min(labels.length - 1, (current ?? -1) + 1));
            if (event.key === "ArrowLeft") setActive((current) => Math.max(0, (current ?? labels.length) - 1));
            if (event.key === "Escape") setActive(null);
          }}
          onBlur={() => setActive(null)}
        >
          {ticks.map((tick) => (
            <g key={tick}>
              <line x1={pad.left} x2={width - pad.right} y1={y(tick)} y2={y(tick)} stroke="var(--border)" strokeWidth="1" />
              <text x={pad.left - 8} y={y(tick)} dy="0.32em" textAnchor="end" className="fill-muted-foreground text-[11px] tabular-nums">
                {tick}
              </text>
            </g>
          ))}
          {labels.map((label, index) =>
            index % Math.max(1, Math.ceil(labels.length / 7)) === 0 || index === labels.length - 1 ? (
              <text key={label} x={x(index)} y={height - 8} textAnchor="middle" className="fill-muted-foreground text-[11px]">
                {fmt(label)}
              </text>
            ) : null,
          )}
          {active !== null && <line x1={x(active)} x2={x(active)} y1={pad.top} y2={pad.top + innerH} stroke="var(--muted-foreground)" strokeOpacity="0.5" strokeWidth="1" />}
          {series.map((item, seriesIndex) => (
            <motion.path
              key={item.key}
              d={item.values.map((value, index) => `${index === 0 ? "M" : "L"}${x(index)},${y(value)}`).join(" ")}
              fill="none"
              stroke={item.color}
              strokeWidth="2"
              strokeLinejoin="round"
              strokeLinecap="round"
              strokeDasharray={item.dashed ? "6 4" : undefined}
              initial={{ opacity: 0 }}
              animate={{ opacity: 1 }}
              transition={{ duration: 0.6, delay: 0.1 * seriesIndex }}
            />
          ))}
          {active !== null &&
            series.map((item) => <circle key={item.key} cx={x(active)} cy={y(item.values[active] ?? 0)} r="4.5" fill={item.color} stroke="var(--card)" strokeWidth="2" />)}
          {endLabels.map(({ item, y: labelY }) => (
            <text key={item.key} x={width - pad.right + 8} y={labelY} dy="0.32em" className="fill-foreground text-[11px] font-medium">
              {item.label} {item.values[item.values.length - 1] ?? 0}
            </text>
          ))}
        </svg>
        {active !== null && (
          <div
            className="pointer-events-none absolute top-2 z-10 min-w-36 rounded-xl border bg-popover px-3 py-2 text-xs shadow-lg"
            style={{ left: `${(x(active) / width) * 100}%`, transform: `translateX(${x(active) > width / 2 ? "calc(-100% - 12px)" : "12px"})` }}
          >
            <p className="mb-1 font-medium">{fmt(labels[active])}</p>
            {series.map((item) => (
              <p key={item.key} className="flex items-center justify-between gap-4 text-muted-foreground">
                <span className="flex items-center gap-1.5">
                  <span className="size-2 rounded-full" style={{ background: item.color }} />
                  {item.label}
                </span>
                <span className="font-medium tabular-nums text-foreground">{item.values[active] ?? 0}</span>
              </p>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}
