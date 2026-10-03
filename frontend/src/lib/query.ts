import { useCallback } from "react";
import { useMutation, useQuery, useQueryClient, keepPreviousData, type QueryKey } from "@tanstack/react-query";
import { api } from "./api";

export function useApi<T>(path: string | null, options?: { enabled?: boolean; refetchInterval?: number; keepPrevious?: boolean }) {
  return useQuery<T>({
    queryKey: ["api", path],
    queryFn: () => api.get<T>(path as string),
    enabled: path !== null && (options?.enabled ?? true),
    refetchInterval: options?.refetchInterval,
    placeholderData: options?.keepPrevious ? keepPreviousData : undefined,
  });
}

export function useInvalidate() {
  const client = useQueryClient();
  return useCallback(
    (...prefixes: string[]) =>
      client.invalidateQueries({
        predicate: (query) => {
          const key = query.queryKey as QueryKey;
          const path = typeof key[1] === "string" ? key[1] : "";
          return prefixes.some((prefix) => path === prefix || path.startsWith(`${prefix}/`) || path.startsWith(`${prefix}?`));
        },
      }),
    [client],
  );
}

export function useAction<TVars, TResult = unknown>(
  fn: (vars: TVars) => Promise<TResult>,
  options?: { invalidate?: string[]; onSuccess?: (result: TResult, vars: TVars) => void; onError?: (error: Error) => void },
) {
  const invalidate = useInvalidate();
  return useMutation<TResult, Error, TVars>({
    mutationFn: fn,
    onSuccess: async (result, vars) => {
      if (options?.invalidate?.length) await invalidate(...options.invalidate);
      options?.onSuccess?.(result, vars);
    },
    onError: options?.onError,
  });
}
