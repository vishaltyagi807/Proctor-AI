import { useEffect } from "react";
import { QueryClientProvider, type QueryClient } from "@tanstack/react-query";
import { onUnauthorized } from "@/lib/api";
import { ThemeProvider } from "./theme";
import { ToastProvider } from "@/components/ui/toast";
import { TooltipProvider } from "@/components/ui/tooltip";

function UnauthorizedRedirect({ client }: { client: QueryClient }) {
  useEffect(() => {
    onUnauthorized(() => {
      if (window.location.pathname === "/login") return;
      client.clear();
      window.location.assign("/login?expired=1");
    });
    return () => onUnauthorized(null);
  }, [client]);
  return null;
}

export function Providers({ client, children }: { client: QueryClient; children: React.ReactNode }) {
  return (
    <QueryClientProvider client={client}>
      <ThemeProvider>
        <UnauthorizedRedirect client={client} />
        <TooltipProvider delay={300}>
          <ToastProvider>{children}</ToastProvider>
        </TooltipProvider>
      </ThemeProvider>
    </QueryClientProvider>
  );
}
