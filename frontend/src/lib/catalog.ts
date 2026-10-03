import { queryOptions } from "@tanstack/react-query";
import { api } from "./api";
import type { PageResponse, Permission } from "./types";

const CATALOG_PAGE_SIZE = 100;

async function fetchCatalog(): Promise<Permission[]> {
  const first = await api.get<PageResponse<Permission>>(catalogPage(0));
  const rest = await Promise.all(
    Array.from({ length: Math.max(first.totalPages - 1, 0) }, (_, index) => api.get<PageResponse<Permission>>(catalogPage(index + 1))),
  );
  return [first, ...rest].flatMap((page) => page.content);
}

function catalogPage(page: number) {
  return `/roles/permissions?page=${page}&size=${CATALOG_PAGE_SIZE}&sortBy=entity&direction=asc`;
}

export const catalogQuery = queryOptions({
  queryKey: ["api", "/roles/permissions", "all"],
  queryFn: fetchCatalog,
});
