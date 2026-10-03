import { createContext, useContext, useEffect, useState } from "react";
import { useInvalidate } from "@/lib/query";
import { openStream } from "@/lib/stream";
import type { ProcessState } from "@/lib/types";

type ProcessEventData = {
  processId: string;
  kind: string;
  label: string;
  status?: ProcessState["status"];
  processed?: number;
  total?: number;
  message?: string;
};

const INVALIDATE_ON_DONE: Record<string, string[]> = {
  "user-import": ["/users"],
  "face-import": ["/faces/enrollments"],
};

type ProcessesContextValue = {
  processes: ProcessState[];
  dismiss: (processId: string) => void;
};

const ProcessesContext = createContext<ProcessesContextValue | null>(null);

export function ProcessesProvider({ children }: { children: React.ReactNode }) {
  const invalidate = useInvalidate();
  const [processes, setProcesses] = useState<Record<string, ProcessState>>({});
  useEffect(() => {
    const onEvent = (raw: MessageEvent) => {
      let data: ProcessEventData;
      try {
        data = JSON.parse(raw.data);
      } catch {
        return;
      }
      if (!data.processId) return;
      setProcesses((current) => {
        const existing = current[data.processId];
        const messages = data.message ? [...(existing?.messages ?? []), data.message].slice(-50) : existing?.messages ?? [];
        return {
          ...current,
          [data.processId]: {
            processId: data.processId,
            kind: data.kind ?? existing?.kind ?? "process",
            label: data.label ?? existing?.label ?? "Background process",
            status: data.status ?? existing?.status ?? "running",
            processed: data.processed ?? existing?.processed ?? 0,
            total: data.total ?? existing?.total ?? 0,
            messages,
            updatedAt: Date.now(),
          },
        };
      });
      if (data.status === "done") {
        const prefixes = INVALIDATE_ON_DONE[data.kind];
        if (prefixes) void invalidate(...prefixes);
      }
    };

    return openStream("/users/me/events", (events) => {
      events.addEventListener("process-progress", onEvent);
      events.addEventListener("process-done", onEvent);
      events.addEventListener("process-error", onEvent);
    });
  }, [invalidate]);

  const dismiss = (processId: string) =>
    setProcesses((current) => {
      const next = { ...current };
      delete next[processId];
      return next;
    });

  const list = Object.values(processes).sort((a, b) => b.updatedAt - a.updatedAt);

  return <ProcessesContext.Provider value={{ processes: list, dismiss }}>{children}</ProcessesContext.Provider>;
}

export function useProcesses() {
  const context = useContext(ProcessesContext);
  if (!context) throw new Error("useProcesses must be used inside ProcessesProvider");
  return context;
}
