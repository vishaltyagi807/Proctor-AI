import { ChevronLeft, ChevronRight, ChevronsLeft, ChevronsRight } from "lucide-react";
import { motion } from "motion/react";
import { Button } from "@/components/ui/button";
import { Hint } from "@/components/ui/hint";
import { cn } from "@/lib/utils";

function pageWindow(page: number, totalPages: number): (number | "gap")[] {
  if (totalPages <= 7) return Array.from({ length: totalPages }, (_, index) => index);
  const pages = new Set([0, totalPages - 1, page - 1, page, page + 1].filter((value) => value >= 0 && value < totalPages));
  if (page <= 2) [1, 2, 3].forEach((value) => pages.add(value));
  if (page >= totalPages - 3) [totalPages - 4, totalPages - 3, totalPages - 2].forEach((value) => pages.add(value));
  const sorted = [...pages].sort((a, b) => a - b);
  return sorted.flatMap((value, index) => (index > 0 && value - sorted[index - 1] > 1 ? (["gap", value] as const) : [value]));
}

export function PageNav({ page, totalPages, onChange, className }: { page: number; totalPages: number; onChange: (page: number) => void; className?: string }) {
  if (totalPages <= 1) return null;
  const last = totalPages - 1;
  return (
    <nav aria-label="Pagination" className={cn("flex items-center gap-1", className)}>
      <Hint label="First page">
        <Button variant="ghost" size="icon-sm" disabled={page === 0} onClick={() => onChange(0)} aria-label="First page" className="hidden sm:inline-flex">
          <ChevronsLeft />
        </Button>
      </Hint>
      <Hint label="Previous page">
        <Button variant="ghost" size="icon-sm" disabled={page === 0} onClick={() => onChange(page - 1)} aria-label="Previous page">
          <ChevronLeft />
        </Button>
      </Hint>
      <div className="flex items-center gap-1">
        {pageWindow(page, totalPages).map((item, index) =>
          item === "gap" ? (
            <span key={`gap-${index}`} className="grid size-8 place-items-center text-xs text-muted-foreground">
              …
            </span>
          ) : (
            <button
              key={item}
              type="button"
              onClick={() => onChange(item)}
              aria-current={item === page ? "page" : undefined}
              className={cn(
                "relative grid h-8 min-w-8 place-items-center rounded-lg px-2 text-sm font-medium tabular-nums transition-colors",
                item === page ? "text-white" : "text-muted-foreground hover:bg-muted hover:text-foreground",
                Math.abs(item - page) > 1 && item !== 0 && item !== last && "hidden sm:grid",
              )}
            >
              {item === page && <motion.span layoutId="page-nav-active" className="absolute inset-0 rounded-lg gradient-brand shadow-md shadow-primary/25" transition={{ type: "spring", stiffness: 420, damping: 34 }} />}
              <span className="relative">{item + 1}</span>
            </button>
          ),
        )}
      </div>
      <Hint label="Next page">
        <Button variant="ghost" size="icon-sm" disabled={page >= last} onClick={() => onChange(page + 1)} aria-label="Next page">
          <ChevronRight />
        </Button>
      </Hint>
      <Hint label="Last page">
        <Button variant="ghost" size="icon-sm" disabled={page >= last} onClick={() => onChange(last)} aria-label="Last page" className="hidden sm:inline-flex">
          <ChevronsRight />
        </Button>
      </Hint>
    </nav>
  );
}
