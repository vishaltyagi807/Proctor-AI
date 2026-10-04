import { useEffect } from "react";
import { QueryClientProvider, type QueryClient } from "@tanstack/react-query";
import { onUnauthorized } from "@/lib/api";
import { ThemeProvider } from "./theme";
import { ToastProvider } from "@/components/ui/toast";
import { TooltipProvider } from "@/components/ui/tooltip";

function UnauthorizedRedirect({ client, onSessionExpired }: { client: QueryClient; onSessionExpired: () => void }) {
  useEffect(() => {
    onUnauthorized(() => {
      if (window.location.pathname === "/login") return;
      client.clear();
      onSessionExpired();
    });
    return () => onUnauthorized(null);
  }, [client, onSessionExpired]);
  return null;
}

export function Providers({ client, onSessionExpired, children }: { client: QueryClient; onSessionExpired: () => void; children: React.ReactNode }) {
  return (
    <QueryClientProvider client={client}>
      <ThemeProvider>
        <UnauthorizedRedirect client={client} onSessionExpired={onSessionExpired} />
        <TooltipProvider delay={300}>
          <ToastProvider>{children}</ToastProvider>
        </TooltipProvider>
      </ThemeProvider>
    </QueryClientProvider>
  );
}
