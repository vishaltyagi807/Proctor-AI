import { createRoute, redirect } from "@tanstack/react-router";
import { rootRoute } from "./root";
import { AppLayout } from "@/pages/app-layout";
import { isUnauthenticated, sessionQuery } from "@/lib/session";
import { prefetch } from "@/lib/prefetch";

export const appRoute = createRoute({
  getParentRoute: () => rootRoute,
  id: "app",
  beforeLoad: async ({ context, location }) => {
    try {
      const session = await context.queryClient.ensureQueryData({ ...sessionQuery, revalidateIfStale: true });
      return { session };
    } catch (error) {
      if (isUnauthenticated(error)) {
        const next = location.pathname === "/" ? undefined : location.href;
        throw redirect({ to: "/login", search: { next } });
      }
      throw error;
    }
  },
  loader: ({ context }) => prefetch(context.queryClient, "/notifications/unread-count"),
  component: AppLayout,
});
