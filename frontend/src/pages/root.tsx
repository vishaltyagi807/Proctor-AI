import { HeadContent, Link, Outlet } from "@tanstack/react-router";
import { Compass } from "lucide-react";
import { Button } from "@/components/ui/button";

export function NotFound() {
  return (
    <div className="grid min-h-screen place-items-center p-6 text-center">
      <div>
        <div className="mx-auto mb-6 grid size-20 place-items-center rounded-3xl gradient-brand text-white shadow-xl shadow-primary/30">
          <Compass className="size-9" />
        </div>
        <h1 className="text-5xl font-semibold tracking-tight text-gradient">404</h1>
        <p className="mt-3 text-muted-foreground">This page does not exist or has moved.</p>
        <Link to="/" className="mt-6 inline-block">
          <Button className="h-11 gradient-brand px-6 text-white">Back to overview</Button>
        </Link>
      </div>
    </div>
  );
}

export function RootLayout() {
  return (
    <>
      <HeadContent />
      <Outlet />
    </>
  );
}
