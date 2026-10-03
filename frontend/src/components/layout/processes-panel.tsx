import { Activity, X } from "lucide-react";
import { useProcesses } from "@/components/providers/processes";
import { Progress } from "@/components/ui/misc";
import { Badge } from "@/components/ui/badge";
import { Hint } from "@/components/ui/hint";
import { DropdownMenu, DropdownMenuContent, DropdownMenuGroup, DropdownMenuLabel, DropdownMenuTrigger } from "@/components/ui/dropdown-menu";

export function ProcessesPanel() {
  const { processes, dismiss } = useProcesses();
  const running = processes.filter((process) => process.status === "running").length;

  return (
    <DropdownMenu>
      <Hint label={running > 0 ? `${running} running` : "Background processes"} side="bottom">
        <DropdownMenuTrigger
          render={
            <button
              className="relative grid size-10 place-items-center rounded-xl border bg-card/60 text-muted-foreground transition hover:border-ring/50 hover:text-foreground data-popup-open:text-foreground"
              aria-label="Background processes"
            >
              <Activity className="size-4.5" />
              {running > 0 && (
                <span className="absolute -right-1 -top-1 grid size-4.5 place-items-center rounded-full bg-primary text-[10px] font-semibold text-primary-foreground">
                  {running}
                </span>
              )}
            </button>
          }
        />
      </Hint>
      <DropdownMenuContent align="end" className="max-h-96 w-80 scrollbar-thin">
        <DropdownMenuGroup>
          <DropdownMenuLabel className="px-2.5 py-1.5 uppercase tracking-wide">Background processes</DropdownMenuLabel>
          {processes.length === 0 ? (
            <p className="px-2.5 py-3 text-sm text-muted-foreground">Nothing running right now.</p>
          ) : (
            processes.map((process) => {
              const percent = process.total > 0 ? Math.round((process.processed / process.total) * 100) : 0;
              const lastMessage = process.messages[process.messages.length - 1];
              return (
                <div key={process.processId} className="rounded-md p-2.5 hover:bg-muted/50">
                  <div className="flex items-center justify-between gap-2">
                    <p className="truncate text-sm font-medium">{process.label}</p>
                    {process.status !== "running" && (
                      <Hint label="Dismiss">
                        <button onClick={() => dismiss(process.processId)} className="rounded-md p-0.5 text-muted-foreground hover:bg-muted" aria-label="Dismiss">
                          <X className="size-3.5" />
                        </button>
                      </Hint>
                    )}
                  </div>
                  <div className="mt-2 flex items-center gap-2">
                    <Progress value={percent} className="flex-1" />
                    <Badge tone={process.status === "done" ? "success" : process.status === "error" ? "danger" : "info"} dot>
                      {process.status === "running" ? `${process.processed}/${process.total}` : process.status}
                    </Badge>
                  </div>
                  {lastMessage && (
                    <Hint label={lastMessage} side="bottom">
                      <p className="mt-1.5 truncate text-xs text-muted-foreground">{lastMessage}</p>
                    </Hint>
                  )}
                </div>
              );
            })
          )}
        </DropdownMenuGroup>
      </DropdownMenuContent>
    </DropdownMenu>
  );
}
