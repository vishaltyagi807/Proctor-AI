import { Link, useLocation } from "@tanstack/react-router";
import { motion } from "motion/react";
import { ShieldCheck } from "lucide-react";
import { NAV } from "./nav";
import { useSession } from "@/components/providers/session";
import { Avatar } from "@/components/ui/misc";
import { HrefLink } from "@/components/ui/href-link";
import { cn } from "@/lib/utils";

export function SidebarContent({ onNavigate }: { onNavigate?: () => void }) {
  const pathname = useLocation({ select: (location) => location.pathname });
  const { can, user, role } = useSession();
  const items = NAV.filter((item) => item.visible(can));
  const groups = ["Workspace", "People", "Configure", "Account"] as const;

  const isActive = (href: string) => (href === "/" ? pathname === "/" : pathname === href || (pathname.startsWith(`${href}/`) && !items.some((other) => other.href !== href && other.href.startsWith(`${href}/`) && (pathname === other.href || pathname.startsWith(`${other.href}/`)))));

  return (
    <div className="flex h-full flex-col bg-sidebar text-sidebar-foreground">
      <Link to="/" onClick={onNavigate} className="flex items-center gap-3 px-6 pb-4 pt-6">
        <span className="relative grid size-10 place-items-center rounded-2xl gradient-brand-animated shadow-lg shadow-primary/40">
          <ShieldCheck className="size-5 text-white" />
        </span>
        <span>
          <span className="block text-[17px] font-semibold leading-5 tracking-tight">ProctorAI</span>
          <span className="block text-[11px] uppercase tracking-[0.18em] text-sidebar-foreground/50">Complaints</span>
        </span>
      </Link>

      <nav className="flex-1 space-y-5 overflow-y-auto px-3 py-3 scrollbar-thin">
        {groups.map((group) => {
          const groupItems = items.filter((item) => item.group === group);
          if (groupItems.length === 0) return null;
          return (
            <div key={group}>
              <p className="mb-1.5 px-3 text-[11px] font-medium uppercase tracking-[0.14em] text-sidebar-foreground/40">{group}</p>
              <ul className="space-y-0.5">
                {groupItems.map((item) => {
                  const active = isActive(item.href);
                  return (
                    <li key={item.href}>
                      <HrefLink
                        href={item.href}
                        onClick={onNavigate}
                        className={cn("group relative flex items-center gap-3 rounded-xl px-3 py-2.5 text-sm font-medium transition-colors", active ? "text-white" : "text-sidebar-foreground/70 hover:text-white")}
                      >
                        {active && <motion.span layoutId="nav-active" className="absolute inset-0 rounded-xl gradient-brand shadow-lg shadow-primary/30" transition={{ type: "spring", stiffness: 420, damping: 34 }} />}
                        {!active && <span className="absolute inset-0 rounded-xl bg-white/0 transition group-hover:bg-white/[0.06]" />}
                        <item.icon className="relative size-[18px]" />
                        <span className="relative">{item.label}</span>
                      </HrefLink>
                    </li>
                  );
                })}
              </ul>
            </div>
          );
        })}
      </nav>

      <div className="m-3 rounded-2xl border border-white/10 bg-white/[0.04] p-3">
        <div className="flex items-center gap-3">
          <Avatar name={user.name} size={38} />
          <div className="min-w-0">
            <p className="truncate text-sm font-medium">{user.name}</p>
            <p className="truncate text-xs capitalize text-sidebar-foreground/55">{role}</p>
          </div>
        </div>
      </div>
    </div>
  );
}

export function Sidebar() {
  return (
    <aside className="fixed inset-y-0 left-0 z-30 hidden w-[264px] border-r border-sidebar-border lg:block">
      <SidebarContent />
    </aside>
  );
}
