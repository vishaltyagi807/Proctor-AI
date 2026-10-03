import { createRoute, lazyRouteComponent, redirect } from "@tanstack/react-router";
import { appRoute } from "./app";
import { DEPARTMENTS_PATH } from "@/lib/users";
import { makeCan } from "@/lib/permissions";
import { prefetch } from "@/lib/prefetch";
import { title } from "@/lib/title";
import type { Session } from "@/lib/types";

function guard({ context }: { context: { session: Session } }) {
  if (!makeCan(context.session)("departments", "read", ["department", "all"])) throw redirect({ to: "/" });
}

export const departmentsRoute = createRoute({
  getParentRoute: () => appRoute,
  path: "departments",
  head: () => title("Departments"),
  beforeLoad: guard,
  loader: ({ context }) => prefetch(context.queryClient, DEPARTMENTS_PATH),
  component: lazyRouteComponent(() => import("@/components/departments/departments-view"), "DepartmentsView"),
});

export const departmentRoute = createRoute({
  getParentRoute: () => appRoute,
  path: "departments/$id",
  head: () => title("Department"),
  beforeLoad: guard,
  loader: ({ context, params }) => prefetch(context.queryClient, `/departments/${params.id}`, `/departments/${params.id}/members`),
  component: lazyRouteComponent(() => import("@/pages/department"), "DepartmentPage"),
});
