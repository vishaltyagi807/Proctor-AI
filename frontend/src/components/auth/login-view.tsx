import { useState } from "react";
import { useNavigate } from "@tanstack/react-router";
import { useQueryClient } from "@tanstack/react-query";
import { motion } from "motion/react";
import { ArrowRight, BellRing, Eye, EyeOff, Lock, Mail, ShieldCheck, Sparkles, Workflow } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/form";
import { Spinner } from "@/components/ui/misc";
import { EASE } from "@/components/ui/motion";
import { ApiError } from "@/lib/api";
import { signIn } from "@/lib/session";
import { Hint } from "@/components/ui/hint";
import { Credits } from "@/components/layout/credits";
import { LogoMark } from "@/components/brand/logo-mark";
import { ServerSettings } from "@/components/auth/server-settings";
import { needsServer } from "@/platform/server";

const features = [
  { icon: Workflow, title: "Guided workflows", text: "Every complaint moves through clear, auditable stages." },
  { icon: BellRing, title: "Realtime updates", text: "Instant in-app alerts and mobile or web push." },
  { icon: ShieldCheck, title: "Row-level security", text: "You only ever see what your role allows." },
];

export function LoginView({ next, expired }: { next: string; expired: boolean }) {
  const navigate = useNavigate();
  const client = useQueryClient();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [show, setShow] = useState(false);
  const [error, setError] = useState<string | null>(expired ? "Your session expired. Please sign in again." : null);
  const [busy, setBusy] = useState(false);
  const [connected, setConnected] = useState(() => !needsServer());

  async function submit(event: React.FormEvent) {
    event.preventDefault();
    setBusy(true);
    setError(null);
    try {
      await signIn(email, password);
    } catch (caught) {
      setError(caught instanceof ApiError ? caught.message || "Unable to sign in" : "The server is unreachable. Please try again.");
      setBusy(false);
      return;
    }
    client.clear();
    await navigate({ href: next, replace: true });
  }

  return (
    <div className="grid min-h-screen px-safe lg:grid-cols-[1.1fr_minmax(0,1fr)] lg:px-0">
      <div className="relative hidden overflow-hidden bg-sidebar p-12 text-white lg:flex lg:flex-col lg:justify-between">
        <div className="bg-grid absolute inset-0 opacity-60" />
        <motion.div className="absolute -left-24 -top-24 size-[420px] rounded-full bg-brand/50 blur-3xl animate-float" />
        <motion.div className="absolute -bottom-32 right-0 size-[480px] rounded-full bg-brand-2/40 blur-3xl animate-float [animation-delay:-4s]" />
        <div className="absolute right-24 top-1/3 size-56 rounded-full bg-info/30 blur-3xl animate-float [animation-delay:-2s]" />

        <div className="relative flex items-center gap-3">
          <LogoMark className="size-11 rounded-[22%] shadow-lg shadow-primary/40" />
          <span className="text-xl font-semibold tracking-tight">ProctorAI</span>
        </div>

        <div className="relative max-w-lg">
          <motion.h1 initial={{ opacity: 0, y: 24 }} animate={{ opacity: 1, y: 0 }} transition={{ duration: 0.7, ease: EASE }} className="text-5xl font-semibold leading-[1.08] tracking-tight">
            Every complaint,
            <br />
            <span className="text-gradient">resolved with clarity.</span>
          </motion.h1>
          <motion.p initial={{ opacity: 0 }} animate={{ opacity: 1 }} transition={{ delay: 0.3, duration: 0.7 }} className="mt-5 text-lg text-white/65">
            One workspace for students, staff and administrators, with access that adapts to each role.
          </motion.p>
          <ul className="mt-10 space-y-4">
            {features.map((feature, index) => (
              <motion.li key={feature.title} initial={{ opacity: 0, x: -20 }} animate={{ opacity: 1, x: 0 }} transition={{ delay: 0.5 + index * 0.12, duration: 0.55, ease: EASE }} className="flex items-start gap-4 rounded-2xl border border-white/10 bg-white/[0.05] p-4 backdrop-blur">
                <span className="grid size-10 shrink-0 place-items-center rounded-xl bg-white/10">
                  <feature.icon className="size-5" />
                </span>
                <span>
                  <span className="block font-medium">{feature.title}</span>
                  <span className="block text-sm text-white/60">{feature.text}</span>
                </span>
              </motion.li>
            ))}
          </ul>
        </div>

        <p className="relative flex items-center gap-2 text-sm text-white/45">
          <Sparkles className="size-4" /> Secure sessions with rotating refresh tokens
        </p>
      </div>

      <div className="relative flex flex-col items-center justify-center p-6 pb-[calc(4rem+var(--safe-bottom))] pt-[calc(1.5rem+var(--safe-top))] sm:p-12 sm:pb-[calc(4rem+var(--safe-bottom))] sm:pt-[calc(3rem+var(--safe-top))]">
        <div className="absolute inset-0 -z-10 bg-[radial-gradient(60%_50%_at_50%_0%,color-mix(in_oklch,var(--brand)_14%,transparent),transparent)]" />
        <motion.div initial={{ opacity: 0, y: 28, scale: 0.98 }} animate={{ opacity: 1, y: 0, scale: 1 }} transition={{ duration: 0.6, ease: EASE }} className="w-full max-w-md">
          <div className="mb-8 flex items-center gap-3 lg:hidden">
            <LogoMark className="size-10 rounded-[22%] shadow-md shadow-primary/30" />
            <span className="text-lg font-semibold">ProctorAI</span>
          </div>
          <h2 className="text-3xl font-semibold tracking-tight">Welcome back</h2>
          <p className="mt-2 text-muted-foreground">{connected ? "Sign in to continue to your workspace." : "Connect to your ProctorAI server to get started."}</p>

          <ServerSettings
            onConnected={() => {
              setConnected(true);
              setError(null);
              client.clear();
            }}
          />

          <form onSubmit={submit} className="mt-8 space-y-5">
            <fieldset disabled={!connected} className="space-y-5 disabled:opacity-50">
            <label className="block space-y-1.5">
              <span className="text-[13px] font-medium">Email</span>
              <span className="relative block">
                <Mail className="pointer-events-none absolute left-3.5 top-1/2 size-4 -translate-y-1/2 text-muted-foreground" />
                <Input type="email" required autoComplete="username" placeholder="you@college.edu" className="h-12 pl-10" value={email} onChange={(event) => setEmail(event.target.value)} />
              </span>
            </label>
            <label className="block space-y-1.5">
              <span className="text-[13px] font-medium">Password</span>
              <span className="relative block">
                <Lock className="pointer-events-none absolute left-3.5 top-1/2 size-4 -translate-y-1/2 text-muted-foreground" />
                <Input type={show ? "text" : "password"} required autoComplete="current-password" placeholder="••••••••" className="h-12 pl-10 pr-11" value={password} onChange={(event) => setPassword(event.target.value)} />
                <Hint label={show ? "Hide password" : "Show password"}>
                  <button type="button" onClick={() => setShow((value) => !value)} className="absolute right-3 top-1/2 -translate-y-1/2 rounded-md p-1 text-muted-foreground hover:text-foreground" aria-label={show ? "Hide password" : "Show password"}>
                    {show ? <EyeOff className="size-4" /> : <Eye className="size-4" />}
                  </button>
                </Hint>
              </span>
            </label>

            {error && (
              <motion.p initial={{ opacity: 0, height: 0 }} animate={{ opacity: 1, height: "auto" }} className="rounded-xl border border-destructive/30 bg-destructive/10 px-3.5 py-2.5 text-sm text-destructive" role="alert">
                {error}
              </motion.p>
            )}

            <motion.div whileTap={{ scale: 0.985 }}>
              <Button type="submit" disabled={busy} className="h-12 w-full gradient-brand-animated text-[15px] text-white shadow-lg shadow-primary/30 hover:opacity-95">
                {busy ? <Spinner /> : (
                  <>
                    Sign in <ArrowRight className="size-4" />
                  </>
                )}
              </Button>
            </motion.div>
            </fieldset>
          </form>
          <p className="mt-8 text-center text-xs text-muted-foreground">Trouble signing in? Contact your administrator.</p>
        </motion.div>
        <Credits className="absolute inset-x-0 bottom-[calc(1.5rem+var(--safe-bottom))] px-6 text-center text-muted-foreground/70 lg:bottom-12" />
      </div>
    </div>
  );
}
