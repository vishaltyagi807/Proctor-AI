import { useEffect, useMemo, useState } from "react";
import { useNavigate } from "@tanstack/react-router";
import { AnimatePresence, motion } from "motion/react";
import { CornerDownLeft, Moon, Search, LogOut } from "lucide-react";
import { NAV } from "./nav";
import { useSession } from "@/components/providers/session";
import { useTheme } from "@/components/providers/theme";
import { cn } from "@/lib/utils";

type Command = { id: string; label: string; hint: string; icon: React.ComponentType<{ className?: string }>; run: () => void; keywords?: string };

export function CommandPalette({ open, onClose, onSignOut }: { open: boolean; onClose: () => void; onSignOut: () => void }) {
  const navigate = useNavigate();
  const { can } = useSession();
  const { resolved, setMode } = useTheme();
  const [query, setQuery] = useState("");
  const [active, setActive] = useState(0);

  const commands = useMemo<Command[]>(() => {
    const pages = NAV.filter((item) => item.visible(can)).map<Command>((item) => ({
      id: item.href,
      label: item.label,
      hint: item.group,
      icon: item.icon,
      keywords: item.keywords,
      run: () => void navigate({ href: item.href }),
    }));
    return [
      ...pages,
      { id: "theme", label: resolved === "dark" ? "Switch to light theme" : "Switch to dark theme", hint: "Preferences", icon: Moon, run: () => setMode(resolved === "dark" ? "light" : "dark") },
      { id: "signout", label: "Sign out", hint: "Account", icon: LogOut, run: onSignOut },
    ];
  }, [can, navigate, resolved, setMode, onSignOut]);

  const results = useMemo(() => {
    const text = query.trim().toLowerCase();
    if (!text) return commands;
    return commands.filter((command) => `${command.label} ${command.hint} ${command.keywords ?? ""}`.toLowerCase().includes(text));
  }, [commands, query]);

  useEffect(() => {
    if (!open) return;
    const onKey = (event: KeyboardEvent) => {
      if (event.key === "Escape") onClose();
      if (event.key === "ArrowDown") {
        event.preventDefault();
        setActive((index) => Math.min(index + 1, results.length - 1));
      }
      if (event.key === "ArrowUp") {
        event.preventDefault();
        setActive((index) => Math.max(index - 1, 0));
      }
      if (event.key === "Enter" && results[active]) {
        results[active].run();
        onClose();
      }
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [open, results, active, onClose]);

  return (
    <AnimatePresence>
      {open && (
        <div className="fixed inset-0 z-[60] flex items-start justify-center p-4 pt-[14vh]">
          <motion.div className="absolute inset-0 bg-black/50 backdrop-blur-sm" initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }} onClick={onClose} />
          <motion.div
            initial={{ opacity: 0, y: -16, scale: 0.97 }}
            animate={{ opacity: 1, y: 0, scale: 1 }}
            exit={{ opacity: 0, y: -10, scale: 0.98 }}
            transition={{ type: "spring", stiffness: 400, damping: 32 }}
            className="relative w-full max-w-xl overflow-hidden rounded-3xl border bg-card shadow-2xl"
          >
            <div className="flex items-center gap-3 border-b px-5">
              <Search className="size-5 text-muted-foreground" />
              <input
                autoFocus
                value={query}
                onChange={(event) => {
                  setQuery(event.target.value);
                  setActive(0);
                }}
                placeholder="Jump to a page or run a command…"
                className="h-14 flex-1 bg-transparent text-[15px] outline-none placeholder:text-muted-foreground"
              />
              <kbd className="rounded-md border px-1.5 py-0.5 text-[11px] text-muted-foreground">esc</kbd>
            </div>
            <ul className="max-h-80 overflow-y-auto p-2 scrollbar-thin">
              {results.length === 0 && <li className="px-4 py-8 text-center text-sm text-muted-foreground">No matches</li>}
              {results.map((command, index) => (
                <li key={command.id}>
                  <button
                    onMouseEnter={() => setActive(index)}
                    onClick={() => {
                      command.run();
                      onClose();
                    }}
                    className={cn("flex w-full items-center gap-3 rounded-xl px-3 py-2.5 text-left text-sm transition", index === active ? "bg-primary/10 text-primary" : "hover:bg-muted")}
                  >
                    <command.icon className="size-4" />
                    <span className="flex-1 font-medium">{command.label}</span>
                    <span className="text-xs text-muted-foreground">{command.hint}</span>
                    {index === active && <CornerDownLeft className="size-3.5" />}
                  </button>
                </li>
              ))}
            </ul>
          </motion.div>
        </div>
      )}
    </AnimatePresence>
  );
}
