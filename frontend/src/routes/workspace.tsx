import { createRoute, lazyRouteComponent, redirect } from "@tanstack/react-router";
import { appRoute } from "./app";
import { ADMIN_PATHS, FACES_PATH, NOTIFICATION_PATHS, customFieldsPath, inboxPath } from "@/lib/paths";
import { DEPARTMENTS_PATH, ROLES_PATH } from "@/lib/users";
import { makeCan } from "@/lib/permissions";
import { parseFaceTab, type FaceTab } from "@/lib/faces";
import { prefetch } from "@/lib/prefetch";
import { title } from "@/lib/title";

export const profileRoute = createRoute({
  getParentRoute: () => appRoute,
  path: "profile",
  head: () => title("My profile"),
  loader: ({ context }) => prefetch(context.queryClient, "/users/me", "/users/me/departments"),
  component: lazyRouteComponent(() => import("@/components/profile/profile-view"), "ProfileView"),
});

export const notificationsRoute = createRoute({
  getParentRoute: () => appRoute,
  path: "notifications",
  head: () => title("Notifications"),
  loader: ({ context }) => prefetch(context.queryClient, inboxPath(0, false), NOTIFICATION_PATHS.preferences, NOTIFICATION_PATHS.devices),
  component: lazyRouteComponent(() => import("@/components/notifications/notifications-view"), "NotificationsView"),
});

export const facesRoute = createRoute({
  getParentRoute: () => appRoute,
  path: "faces",
  head: () => title("Face recognition"),
  validateSearch: (search: Record<string, unknown>): { tab?: FaceTab } => ({ tab: parseFaceTab(search.tab) }),
  beforeLoad: ({ context }) => {
    const can = makeCan(context.session);
    if (!can("face_recognition", "read") && !can("face_recognition", "write", ["own", "department", "all"])) throw redirect({ to: "/" });
  },
  loader: ({ context }) => {
    const can = makeCan(context.session);
    const paths: string[] = [];
    if (can("face_recognition", "read")) paths.push(FACES_PATH);
    if (can("face_recognition", "write", ["own", "department", "all"]) && can("users", "read", ["department", "all"])) paths.push(ADMIN_PATHS.people);
    return prefetch(context.queryClient, ...paths);
  },
  component: lazyRouteComponent(() => import("@/components/faces/faces-view"), "FacesView"),
});

export const customFieldsRoute = createRoute({
  getParentRoute: () => appRoute,
  path: "custom-fields",
  head: () => title("Custom fields"),
  beforeLoad: ({ context }) => {
    const can = makeCan(context.session);
    if (!can("custom_fields", "write") && !can("custom_fields", "update") && !can("custom_fields", "delete")) throw redirect({ to: "/" });
  },
  loader: ({ context }) => prefetch(context.queryClient, customFieldsPath("users"), ROLES_PATH, DEPARTMENTS_PATH),
  component: lazyRouteComponent(() => import("@/components/custom-fields/custom-fields-manager"), "CustomFieldsManager"),
});

export const adminNotificationsRoute = createRoute({
  getParentRoute: () => appRoute,
  path: "admin/notifications",
  head: () => title("Broadcast & push"),
  beforeLoad: ({ context }) => {
    const can = makeCan(context.session);
    if (!can("integrations", "read") && !can("notifications", "write", ["all", "department"])) throw redirect({ to: "/" });
  },
  loader: ({ context }) => {
    const paths = [DEPARTMENTS_PATH, ADMIN_PATHS.people];
    if (makeCan(context.session)("integrations", "read")) paths.push(ADMIN_PATHS.integrations);
    return prefetch(context.queryClient, ...paths);
  },
  component: lazyRouteComponent(() => import("@/components/admin/notifications-admin"), "NotificationsAdmin"),
});

export const systemMonitorRoute = createRoute({
  getParentRoute: () => appRoute,
  path: "admin/system",
  head: () => title("System monitor"),
  beforeLoad: ({ context }) => {
    if (!makeCan(context.session)("system_monitor", "read")) throw redirect({ to: "/" });
  },
  component: lazyRouteComponent(() => import("@/components/system/system-monitor"), "SystemMonitor"),
});
