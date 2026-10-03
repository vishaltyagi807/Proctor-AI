import { createRoute, lazyRouteComponent, redirect } from "@tanstack/react-router";
import { appRoute } from "./app";
import { ROLES_PATH } from "@/lib/users";
import { catalogQuery } from "@/lib/catalog";
import { makeCan } from "@/lib/permissions";
import { prefetch } from "@/lib/prefetch";
import { title } from "@/lib/title";
import type { Session } from "@/lib/types";

function guard({ context }: { context: { session: Session } }) {
  if (!makeCan(context.session)("roles", "read", ["all"])) throw redirect({ to: "/" });
}

export const rolesRoute = createRoute({
  getParentRoute: () => appRoute,
  path: "roles",
  head: () => title("Roles & access"),
  beforeLoad: guard,
  loader: ({ context }) => prefetch(context.queryClient, ROLES_PATH),
  component: lazyRouteComponent(() => import("@/components/roles/roles-view"), "RolesView"),
});

export const roleRoute = createRoute({
  getParentRoute: () => appRoute,
  path: "roles/$id",
  head: () => title("Role"),
  beforeLoad: guard,
  loader: ({ context, params }) => Promise.all([prefetch(context.queryClient, `/roles/${params.id}`), context.queryClient.prefetchQuery(catalogQuery)]),
  component: lazyRouteComponent(() => import("@/pages/role"), "RolePage"),
});
