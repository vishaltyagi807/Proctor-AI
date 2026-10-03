import { createRootRouteWithContext } from "@tanstack/react-router";
import type { QueryClient } from "@tanstack/react-query";
import { NotFound, RootLayout } from "@/pages/root";
import { title } from "@/lib/title";

export type RouterContext = { queryClient: QueryClient };

export const rootRoute = createRootRouteWithContext<RouterContext>()({
  head: () => title(),
  component: RootLayout,
  notFoundComponent: NotFound,
});
