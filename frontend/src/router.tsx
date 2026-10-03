import { QueryClient } from "@tanstack/react-query";
import { createRouter } from "@tanstack/react-router";
import { rootRoute } from "./routes/root";
import { loginRoute } from "./routes/login";
import { appRoute } from "./routes/app";
import { RouteError } from "./pages/app-layout";
import { dashboardRoute } from "./routes/dashboard";
import { complaintRoute, complaintsRoute, newComplaintRoute } from "./routes/complaints";
import { userRoute, usersRoute } from "./routes/users";
import { departmentRoute, departmentsRoute } from "./routes/departments";
import { roleRoute, rolesRoute } from "./routes/roles";
import { adminNotificationsRoute, customFieldsRoute, facesRoute, notificationsRoute, profileRoute, systemMonitorRoute } from "./routes/workspace";

export const queryClient = new QueryClient({
  defaultOptions: {
    queries: { staleTime: 30_000, refetchOnWindowFocus: false, retry: 1 },
  },
});

const routeTree = rootRoute.addChildren([
  loginRoute,
  appRoute.addChildren([
    dashboardRoute,
    complaintsRoute,
    newComplaintRoute,
    complaintRoute,
    usersRoute,
    userRoute,
    departmentsRoute,
    departmentRoute,
    rolesRoute,
    roleRoute,
    profileRoute,
    notificationsRoute,
    facesRoute,
    customFieldsRoute,
    adminNotificationsRoute,
    systemMonitorRoute,
  ]),
]);

export const router = createRouter({
  routeTree,
  context: { queryClient },
  defaultPreload: "intent",
  defaultPreloadStaleTime: 0,
  defaultErrorComponent: RouteError,
  notFoundMode: "root",
  scrollRestoration: true,
});

declare module "@tanstack/react-router" {
  interface Register {
    router: typeof router;
  }
}
