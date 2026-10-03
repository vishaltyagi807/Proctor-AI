import { Outlet, getRouteApi, useLocation, useRouter, type ErrorComponentProps } from "@tanstack/react-router";
import { motion } from "motion/react";
import { AlertTriangle, RotateCcw } from "lucide-react";
import { SessionProvider } from "@/components/providers/session";
import { ProcessesProvider } from "@/components/providers/processes";
import { AppShell } from "@/components/layout/app-shell";
import { Button } from "@/components/ui/button";
import { EmptyState } from "@/components/ui/misc";

const appApi = getRouteApi("/app");

export function RouteError({ error, reset }: ErrorComponentProps) {
  const router = useRouter();
  return (
    <EmptyState
      icon={<AlertTriangle className="size-7" />}
      title="Something went wrong"
      description={error instanceof Error && error.message ? error.message : "We could not load this page. Please try again."}
      action={
        <Button
          onClick={() => {
            reset();
            void router.invalidate();
          }}
          className="gradient-brand text-white"
        >
          <RotateCcw /> Try again
        </Button>
      }
    />
  );
}

export function AppLayout() {
  const { session } = appApi.useRouteContext();
  const pathname = useLocation({ select: (location) => location.pathname });
  return (
    <SessionProvider session={session}>
      <ProcessesProvider>
        <AppShell>
          <motion.div key={pathname} initial={{ opacity: 0, y: 12 }} animate={{ opacity: 1, y: 0 }} transition={{ duration: 0.35, ease: [0.22, 1, 0.36, 1] }}>
            <Outlet />
          </motion.div>
        </AppShell>
      </ProcessesProvider>
    </SessionProvider>
  );
}
