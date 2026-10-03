import { cva, type VariantProps } from "class-variance-authority";
import { cn } from "@/lib/utils";
import type { ComplaintPriority, ComplaintStatus } from "@/lib/types";

const badge = cva("inline-flex items-center gap-1.5 rounded-full px-2.5 py-0.5 text-xs font-medium whitespace-nowrap", {
  variants: {
    tone: {
      neutral: "bg-muted text-muted-foreground",
      brand: "bg-primary/12 text-primary",
      success: "bg-success/15 text-success",
      warning: "bg-warning/20 text-[color-mix(in_oklch,var(--warning),var(--foreground)_35%)]",
      danger: "bg-destructive/12 text-destructive",
      info: "bg-info/15 text-info",
      violet: "bg-brand-2/15 text-brand-2",
    },
  },
  defaultVariants: { tone: "neutral" },
});

export function Badge({ className, tone, dot, ...props }: React.ComponentProps<"span"> & VariantProps<typeof badge> & { dot?: boolean }) {
  return (
    <span className={cn(badge({ tone }), className)} {...props}>
      {dot && <span className="size-1.5 rounded-full bg-current" />}
      {props.children}
    </span>
  );
}

const statusTone: Record<ComplaintStatus, VariantProps<typeof badge>["tone"]> = {
  pending: "warning",
  reviewed: "info",
  resolved: "success",
  rejected: "danger",
};

export function StatusBadge({ status }: { status: ComplaintStatus }) {
  return (
    <Badge tone={statusTone[status]} dot>
      {status}
    </Badge>
  );
}

const priorityTone: Record<ComplaintPriority, VariantProps<typeof badge>["tone"]> = {
  low: "neutral",
  medium: "info",
  high: "warning",
  urgent: "danger",
};

export function PriorityBadge({ priority }: { priority: ComplaintPriority }) {
  return <Badge tone={priorityTone[priority]}>{priority}</Badge>;
}
