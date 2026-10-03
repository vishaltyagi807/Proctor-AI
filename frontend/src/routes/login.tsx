import { createRoute, lazyRouteComponent, redirect } from "@tanstack/react-router";
import { rootRoute } from "./root";
import { sessionQuery } from "@/lib/session";
import { title } from "@/lib/title";

export const loginRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: "login",
  head: () => title("Sign in"),
  validateSearch: (search: Record<string, unknown>): { next?: string; expired?: boolean } => ({
    next: typeof search.next === "string" ? search.next : undefined,
    expired: String(search.expired) === "1" || search.expired === true ? true : undefined,
  }),
  beforeLoad: async ({ context }) => {
    if (context.queryClient.getQueryState(sessionQuery.queryKey)?.status === "error") return;
    const session = await context.queryClient.ensureQueryData(sessionQuery).catch(() => null);
    if (session) throw redirect({ to: "/" });
  },
  component: lazyRouteComponent(() => import("@/pages/login"), "LoginPage"),
});
