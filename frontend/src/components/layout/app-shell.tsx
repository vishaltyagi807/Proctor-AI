import { useCallback, useEffect, useState } from "react";
import { useNavigate } from "@tanstack/react-router";
import { useQueryClient } from "@tanstack/react-query";
import { LogOut, Menu as MenuIcon, Search, UserRound } from "lucide-react";
import { Sidebar, SidebarContent } from "./sidebar";
import { ThemeToggle } from "./theme-toggle";
import { NotificationBell } from "./notification-bell";
import { ProcessesPanel } from "./processes-panel";
import { CommandPalette } from "./command-palette";
import { useSession } from "@/components/providers/session";
import { signOut } from "@/lib/session";
import { Sheet } from "@/components/ui/modal";
import { Avatar } from "@/components/ui/misc";
import { Hint } from "@/components/ui/hint";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuGroup,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";

export function AppShell({ children }: { children: React.ReactNode }) {
  const navigate = useNavigate();
  const client = useQueryClient();
  const { user, role } = useSession();
  const [drawer, setDrawer] = useState(false);
  const [palette, setPalette] = useState(false);

  const handleSignOut = useCallback(async () => {
    await signOut(client);
    await navigate({ to: "/login", replace: true });
    client.clear();
  }, [client, navigate]);

  useEffect(() => {
    const onKey = (event: KeyboardEvent) => {
      if ((event.metaKey || event.ctrlKey) && event.key.toLowerCase() === "k") {
        event.preventDefault();
        setPalette((open) => !open);
      }
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, []);

  return (
    <div className="min-h-screen">
      <Sidebar />
      <Sheet open={drawer} onClose={() => setDrawer(false)} title="Menu" side="left">
        <SidebarContent onNavigate={() => setDrawer(false)} />
      </Sheet>
      <CommandPalette open={palette} onClose={() => setPalette(false)} onSignOut={handleSignOut} />

      <div className="lg:pl-[264px]">
        <header className="glass sticky top-0 z-20 flex h-16 items-center gap-3 border-x-0 border-t-0 px-4 sm:px-8">
          <Hint label="Menu" side="bottom">
            <button onClick={() => setDrawer(true)} className="grid size-10 place-items-center rounded-xl border bg-card/60 lg:hidden" aria-label="Open menu">
              <MenuIcon className="size-5" />
            </button>
          </Hint>
          <button
            onClick={() => setPalette(true)}
            className="flex h-10 min-w-0 flex-1 items-center gap-3 rounded-xl border bg-card/60 px-3.5 text-sm text-muted-foreground transition hover:border-ring/50 sm:max-w-md"
          >
            <Search className="size-4 shrink-0" />
            <span className="sr-only flex-1 truncate text-left sm:not-sr-only">Search or jump to…</span>
            <kbd className="hidden rounded-md border px-1.5 py-0.5 text-[11px] sm:block">⌘K</kbd>
          </button>
          <div className="ml-auto flex items-center gap-2">
            <ThemeToggle />
            <ProcessesPanel />
            <NotificationBell />
            <DropdownMenu>
              <Hint label="Account" side="bottom">
                <DropdownMenuTrigger
                  render={
                    <button className="flex items-center gap-2.5 rounded-xl border bg-card/60 py-1 pl-1 pr-3 transition hover:border-ring/50 data-popup-open:border-ring/50" aria-label="Account menu">
                      <Avatar name={user.name} size={32} />
                      <span className="hidden text-left leading-tight sm:block">
                        <span className="block max-w-32 truncate text-[13px] font-medium">{user.name}</span>
                        <span className="block text-[11px] capitalize text-muted-foreground">{role}</span>
                      </span>
                    </button>
                  }
                />
              </Hint>
              <DropdownMenuContent align="end" className="w-56">
                <DropdownMenuGroup>
                  <DropdownMenuLabel className="px-2 py-1.5">
                    <span className="block truncate text-sm font-medium text-foreground">{user.name}</span>
                    <span className="block truncate text-xs font-normal">{user.email}</span>
                  </DropdownMenuLabel>
                </DropdownMenuGroup>
                <DropdownMenuSeparator />
                <DropdownMenuItem onClick={() => void navigate({ to: "/profile" })}>
                  <UserRound /> My profile
                </DropdownMenuItem>
                <DropdownMenuItem variant="destructive" onClick={() => void handleSignOut()}>
                  <LogOut /> Sign out
                </DropdownMenuItem>
              </DropdownMenuContent>
            </DropdownMenu>
          </div>
        </header>
        <main className="mx-auto w-full max-w-[1400px] px-4 py-8 sm:px-8">{children}</main>
      </div>
    </div>
  );
}
