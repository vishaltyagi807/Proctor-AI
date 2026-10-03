import { createRoute, lazyRouteComponent, redirect } from "@tanstack/react-router";
import { appRoute } from "./app";
import { DEPARTMENTS_PATH, ROLES_PATH, parseUserFilters, userInfoPath, usersPath, type UserFilters } from "@/lib/users";
import { makeCan } from "@/lib/permissions";
import { prefetch } from "@/lib/prefetch";
import { title } from "@/lib/title";

export const usersRoute = createRoute({
  getParentRoute: () => appRoute,
  path: "users",
  head: () => title("Users"),
  validateSearch: (search: Record<string, unknown>): Partial<UserFilters> => {
    const { page, ...rest } = parseUserFilters(search);
    return page > 0 ? { page, ...rest } : rest;
  },
  beforeLoad: ({ context }) => {
    if (!makeCan(context.session)("users", "read", ["department", "all"])) throw redirect({ to: "/" });
  },
  loaderDeps: ({ search }) => ({ filters: { page: 0, ...search } }),
  loader: ({ context, deps }) => prefetch(context.queryClient, usersPath(deps.filters), ROLES_PATH, DEPARTMENTS_PATH),
  component: lazyRouteComponent(() => import("@/pages/users"), "UsersPage"),
});

export const userRoute = createRoute({
  getParentRoute: () => appRoute,
  path: "users/$id",
  head: () => title("User"),
  beforeLoad: ({ context, params }) => {
    if (!makeCan(context.session)("users", "read", ["department", "all"]) && params.id !== context.session.user.id) throw redirect({ to: "/" });
  },
  loader: ({ context, params }) => prefetch(context.queryClient, userInfoPath(params.id), `/users/${params.id}/permissions`, ROLES_PATH, DEPARTMENTS_PATH),
  component: lazyRouteComponent(() => import("@/pages/users"), "UserPage"),
});
