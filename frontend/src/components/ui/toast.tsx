import { createContext, useCallback, useContext, useMemo, useState } from "react";
import { AnimatePresence, motion } from "motion/react";
import { AlertCircle, CheckCircle2, Info, X } from "lucide-react";
import { cn } from "@/lib/utils";

type Kind = "success" | "error" | "info";
type Toast = { id: number; kind: Kind; title: string; description?: string };
type ToastApi = {
  success: (title: string, description?: string) => void;
  error: (title: string, description?: string) => void;
  info: (title: string, description?: string) => void;
};

const ToastContext = createContext<ToastApi | null>(null);
let counter = 0;

const icons = { success: CheckCircle2, error: AlertCircle, info: Info };
const colors = { success: "text-success", error: "text-destructive", info: "text-info" };

export function ToastProvider({ children }: { children: React.ReactNode }) {
  const [toasts, setToasts] = useState<Toast[]>([]);

  const dismiss = useCallback((id: number) => setToasts((current) => current.filter((toast) => toast.id !== id)), []);
  const push = useCallback(
    (kind: Kind, title: string, description?: string) => {
      const id = ++counter;
      setToasts((current) => [...current.slice(-3), { id, kind, title, description }]);
      window.setTimeout(() => dismiss(id), kind === "error" ? 6000 : 4000);
    },
    [dismiss],
  );

  const api = useMemo<ToastApi>(
    () => ({
      success: (title, description) => push("success", title, description),
      error: (title, description) => push("error", title, description),
      info: (title, description) => push("info", title, description),
    }),
    [push],
  );

  return (
    <ToastContext.Provider value={api}>
      {children}
      <div className="pointer-events-none fixed right-[calc(1rem+var(--safe-right))] top-[calc(1rem+var(--safe-top))] z-[100] flex w-[min(92vw,380px)] flex-col gap-2" aria-live="polite">
        <AnimatePresence initial={false}>
          {toasts.map((toast) => {
            const Icon = icons[toast.kind];
            return (
              <motion.div
                key={toast.id}
                layout
                initial={{ opacity: 0, x: 60, scale: 0.95 }}
                animate={{ opacity: 1, x: 0, scale: 1 }}
                exit={{ opacity: 0, x: 60, scale: 0.9 }}
                transition={{ type: "spring", stiffness: 380, damping: 30 }}
                className="glass pointer-events-auto flex items-start gap-3 rounded-2xl p-3.5 shadow-xl shadow-black/10"
                role="status"
              >
                <Icon className={cn("mt-0.5 size-5 shrink-0", colors[toast.kind])} />
                <div className="min-w-0 flex-1">
                  <p className="text-sm font-medium leading-5">{toast.title}</p>
                  {toast.description && <p className="mt-0.5 text-xs text-muted-foreground">{toast.description}</p>}
                </div>
                <button onClick={() => dismiss(toast.id)} className="rounded-md p-1 text-muted-foreground hover:bg-muted" aria-label="Dismiss">
                  <X className="size-4" />
                </button>
              </motion.div>
            );
          })}
        </AnimatePresence>
      </div>
    </ToastContext.Provider>
  );
}

export function useToast() {
  const context = useContext(ToastContext);
  if (!context) throw new Error("useToast must be used inside ToastProvider");
  return context;
}
