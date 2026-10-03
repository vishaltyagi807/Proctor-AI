import { useEffect } from "react";
import { Link } from "@tanstack/react-router";
import { AnimatePresence, motion } from "motion/react";
import { Bell } from "lucide-react";
import { useApi, useInvalidate } from "@/lib/query";
import { openStream } from "@/lib/stream";
import { useToast } from "@/components/ui/toast";
import type { AppNotification } from "@/lib/types";
import { Hint } from "@/components/ui/hint";

export function NotificationBell() {
  const { data } = useApi<{ unread: number }>("/notifications/unread-count", { refetchInterval: 60_000 });
  const invalidate = useInvalidate();
  const toast = useToast();

  useEffect(
    () =>
      openStream("/notifications/stream", (source) => {
        source.addEventListener("notification", (event) => {
          try {
            const notification = JSON.parse((event as MessageEvent).data) as AppNotification;
            toast.info(notification.title, notification.body ?? undefined);
          } catch {
            toast.info("New notification");
          }
          invalidate("/notifications");
        });
      }),
    [invalidate, toast],
  );

  const unread = data?.unread ?? 0;
  return (
    <Hint label={unread > 0 ? `${unread} unread notification${unread === 1 ? "" : "s"}` : "Notifications"} side="bottom">
      <Link to="/notifications" aria-label={`Notifications (${unread} unread)`} className="relative grid size-10 place-items-center rounded-xl border bg-card/60 text-muted-foreground transition hover:text-foreground">
        <motion.span animate={unread > 0 ? { rotate: [0, -14, 12, -8, 0] } : {}} transition={{ duration: 0.8, repeat: unread > 0 ? Infinity : 0, repeatDelay: 6 }}>
          <Bell className="size-[18px]" />
        </motion.span>
        <AnimatePresence>
          {unread > 0 && (
            <motion.span
              key={unread}
              initial={{ scale: 0 }}
              animate={{ scale: 1 }}
              exit={{ scale: 0 }}
              className="absolute -right-1 -top-1 grid min-w-5 place-items-center rounded-full gradient-brand px-1 text-[10px] font-semibold text-white shadow"
            >
              {unread > 99 ? "99+" : unread}
            </motion.span>
          )}
        </AnimatePresence>
      </Link>
    </Hint>
  );
}
