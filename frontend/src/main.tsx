import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import { RouterProvider } from "@tanstack/react-router";
import "@fontsource-variable/inter";
import "@fontsource-variable/geist-mono";
import "./index.css";
import { Providers } from "@/components/providers/providers";
import { safeNext } from "@/lib/session";
import { initPlatform } from "@/platform";
import { startDeepLinks } from "@/platform/deep-links";
import { queryClient, router } from "./router";

await initPlatform().catch(() => undefined);

const expireSession = () => void router.navigate({ href: "/login?expired=1", replace: true });

createRoot(document.getElementById("root")!).render(
  <StrictMode>
    <Providers client={queryClient} onSessionExpired={expireSession}>
      <RouterProvider router={router} />
    </Providers>
  </StrictMode>,
);

void startDeepLinks((href) => void router.navigate({ href: safeNext(href) })).catch(() => undefined);
