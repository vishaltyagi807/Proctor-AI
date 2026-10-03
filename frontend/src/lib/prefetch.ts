import type { QueryClient } from "@tanstack/react-query";
import { api } from "./api";

export async function prefetch(client: QueryClient, ...paths: string[]) {
  await Promise.all(
    paths.map((path) =>
      client.prefetchQuery({
        queryKey: ["api", path],
        queryFn: () => api.get(path),
      }),
    ),
  );
}
