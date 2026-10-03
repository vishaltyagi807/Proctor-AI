import { createRoute, lazyRouteComponent, redirect } from "@tanstack/react-router";
import { appRoute } from "./app";
import { complaintsPath, parseComplaintFilters, type ComplaintFilters } from "@/lib/complaints";
import { makeCan } from "@/lib/permissions";
import { prefetch } from "@/lib/prefetch";
import { detailPaths } from "@/lib/paths";
import { title } from "@/lib/title";

export const complaintsRoute = createRoute({
  getParentRoute: () => appRoute,
  path: "complaints",
  head: () => title("Complaints"),
  validateSearch: (search: Record<string, unknown>): Partial<ComplaintFilters> => {
    const { page, ...rest } = parseComplaintFilters(search);
    return page > 0 ? { page, ...rest } : rest;
  },
  loaderDeps: ({ search }) => ({ filters: { page: 0, ...search } }),
  loader: ({ context, deps }) => prefetch(context.queryClient, complaintsPath(deps.filters, context.session.user.id)),
  component: lazyRouteComponent(() => import("@/pages/complaints"), "ComplaintsPage"),
});

export const newComplaintRoute = createRoute({
  getParentRoute: () => appRoute,
  path: "complaints/new",
  head: () => title("New complaint"),
  beforeLoad: ({ context }) => {
    if (!makeCan(context.session)("complaints", "write")) throw redirect({ to: "/complaints" });
  },
  loader: ({ context }) => prefetch(context.queryClient, "/departments?size=100&sortBy=name&direction=asc"),
  component: lazyRouteComponent(() => import("@/components/complaints/complaint-form"), "ComplaintForm"),
});

export const complaintRoute = createRoute({
  getParentRoute: () => appRoute,
  path: "complaints/$id",
  head: () => title("Complaint"),
  loader: ({ context, params }) => {
    const paths = detailPaths(params.id);
    return prefetch(context.queryClient, paths.complaint, paths.history, paths.comments, paths.files);
  },
  component: lazyRouteComponent(() => import("@/pages/complaints"), "ComplaintPage"),
});
