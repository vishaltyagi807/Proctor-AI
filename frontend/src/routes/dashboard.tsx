import { createRoute, lazyRouteComponent } from "@tanstack/react-router";
import { appRoute } from "./app";
import { makeCan } from "@/lib/permissions";
import { prefetch } from "@/lib/prefetch";
import { DASHBOARD_PATHS } from "@/lib/paths";
import { title } from "@/lib/title";

export const dashboardRoute = createRoute({
  getParentRoute: () => appRoute,
  path: "/",
  head: () => title("Overview"),
  loader: ({ context }) => {
    const can = makeCan(context.session);
    const paths = [DASHBOARD_PATHS.notifications];
    if (can("dashboard", "read")) paths.push(DASHBOARD_PATHS.stats);
    if (can("complaints", "read")) paths.push(DASHBOARD_PATHS.recent);
    if (can("users", "read", ["department", "all"])) paths.push(DASHBOARD_PATHS.users);
    if (can("departments", "read", ["department", "all"])) paths.push(DASHBOARD_PATHS.departments);
    return prefetch(context.queryClient, ...paths);
  },
  component: lazyRouteComponent(() => import("@/components/dashboard/dashboard-view"), "DashboardView"),
});
