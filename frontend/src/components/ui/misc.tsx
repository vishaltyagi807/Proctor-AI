import { motion } from "motion/react";
import { ChevronLeft, ChevronRight, Loader2 } from "lucide-react";
import { cn } from "@/lib/utils";
import { initials } from "@/lib/format";
import { Button } from "./button";
import { Hint } from "@/components/ui/hint";

export function Skeleton({ className }: { className?: string }) {
  return <div className={cn("skeleton rounded-lg", className)} />;
}

export function Spinner({ className }: { className?: string }) {
  return <Loader2 className={cn("size-4 animate-spin", className)} />;
}

const avatarColors = ["from-indigo-500 to-violet-500", "from-sky-500 to-indigo-500", "from-emerald-500 to-teal-500", "from-amber-500 to-orange-500", "from-rose-500 to-pink-500", "from-fuchsia-500 to-purple-500"];

export function Avatar({ name, size = 36, className }: { name: string | null | undefined; size?: number; className?: string }) {
  const seed = (name ?? "?").split("").reduce((sum, char) => sum + char.charCodeAt(0), 0);
  return (
    <span
      style={{ width: size, height: size, fontSize: size * 0.38 }}
      className={cn("grid shrink-0 place-items-center rounded-full bg-gradient-to-br font-semibold text-white shadow-sm", avatarColors[seed % avatarColors.length], className)}
    >
      {initials(name)}
    </span>
  );
}

export function EmptyState({ icon, title, description, action }: { icon: React.ReactNode; title: string; description?: string; action?: React.ReactNode }) {
  return (
    <motion.div initial={{ opacity: 0, scale: 0.96 }} animate={{ opacity: 1, scale: 1 }} className="flex flex-col items-center justify-center px-6 py-14 text-center">
      <div className="relative mb-4 grid size-16 place-items-center rounded-2xl bg-primary/10 text-primary">
        <span className="absolute inset-0 animate-pulse-ring rounded-2xl bg-primary/20" />
        <span className="relative">{icon}</span>
      </div>
      <h3 className="font-semibold">{title}</h3>
      {description && <p className="mt-1 max-w-sm text-sm text-muted-foreground">{description}</p>}
      {action && <div className="mt-4">{action}</div>}
    </motion.div>
  );
}

export function Progress({ value, className }: { value: number; className?: string }) {
  return (
    <div className={cn("h-2 overflow-hidden rounded-full bg-muted", className)}>
      <motion.div className="h-full rounded-full gradient-brand" initial={{ width: 0 }} animate={{ width: `${Math.min(100, Math.max(0, value))}%` }} transition={{ duration: 0.5 }} />
    </div>
  );
}

export function Tabs<T extends string>({ tabs, value, onChange, id }: { tabs: { value: T; label: string; count?: number }[]; value: T; onChange: (value: T) => void; id: string }) {
  return (
    <div className="inline-flex max-w-full gap-1 overflow-x-auto rounded-xl bg-muted p-1 [scrollbar-width:none]" role="tablist">
      {tabs.map((tab) => (
        <button
          key={tab.value}
          role="tab"
          aria-selected={value === tab.value}
          onClick={() => onChange(tab.value)}
          className={cn("relative shrink-0 whitespace-nowrap rounded-lg px-3.5 py-1.5 text-sm font-medium transition-colors", value === tab.value ? "text-foreground" : "text-muted-foreground hover:text-foreground")}
        >
          {value === tab.value && <motion.span layoutId={`tab-${id}`} className="absolute inset-0 rounded-lg bg-card shadow-sm" transition={{ type: "spring", stiffness: 420, damping: 34 }} />}
          <span className="relative flex items-center gap-1.5">
            {tab.label}
            {tab.count !== undefined && <span className="rounded-full bg-primary/12 px-1.5 text-[11px] text-primary">{tab.count}</span>}
          </span>
        </button>
      ))}
    </div>
  );
}

export function Pagination({ page, totalPages, onChange }: { page: number; totalPages: number; onChange: (page: number) => void }) {
  if (totalPages <= 1) return null;
  return (
    <div className="flex items-center justify-between gap-3 pt-4 text-sm">
      <span className="text-muted-foreground">
        Page {page + 1} of {totalPages}
      </span>
      <div className="flex gap-2">
        <Hint label="Previous page">
          <Button variant="outline" size="icon" disabled={page === 0} onClick={() => onChange(page - 1)} aria-label="Previous page">
            <ChevronLeft />
          </Button>
        </Hint>
        <Hint label="Next page">
          <Button variant="outline" size="icon" disabled={page + 1 >= totalPages} onClick={() => onChange(page + 1)} aria-label="Next page">
            <ChevronRight />
          </Button>
        </Hint>
      </div>
    </div>
  );
}

export function Table({ className, ...props }: React.ComponentProps<"table">) {
  return (
    <div className="overflow-x-auto scrollbar-thin">
      <table className={cn("w-full text-sm", className)} {...props} />
    </div>
  );
}

export function Th({ className, ...props }: React.ComponentProps<"th">) {
  return <th className={cn("whitespace-nowrap px-4 py-2.5 text-left text-xs font-medium uppercase tracking-wide text-muted-foreground", className)} {...props} />;
}

export function Td({ className, ...props }: React.ComponentProps<"td">) {
  return <td className={cn("px-4 py-3 align-middle", className)} {...props} />;
}

export function PageHeader({ title, description, actions, icon }: { title: string; description?: string; actions?: React.ReactNode; icon?: React.ReactNode }) {
  return (
    <div className="mb-6 flex flex-wrap items-end justify-between gap-4">
      <div className="flex items-center gap-3.5">
        {icon && <div className="grid size-11 place-items-center rounded-2xl gradient-brand text-white shadow-lg shadow-primary/25">{icon}</div>}
        <div>
          <h1 className="text-2xl font-semibold tracking-tight">{title}</h1>
          {description && <p className="mt-0.5 text-sm text-muted-foreground">{description}</p>}
        </div>
      </div>
      {actions && <div className="flex flex-wrap items-center gap-2">{actions}</div>}
    </div>
  );
}
