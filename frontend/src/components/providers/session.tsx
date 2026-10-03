import { createContext, useContext, useMemo } from "react";
import type { Session } from "@/lib/types";
import { isStaffOf, makeCan, makeManage, roleLabel, type Can, type Manage } from "@/lib/permissions";

type SessionContextValue = {
  session: Session;
  user: Session["user"];
  can: Can;
  manage: Manage;
  staff: boolean;
  superuser: boolean;
  role: string;
};

const SessionContext = createContext<SessionContextValue | null>(null);

export function SessionProvider({ session, children }: { session: Session; children: React.ReactNode }) {
  const value = useMemo<SessionContextValue>(() => {
    const can = makeCan(session);
    return {
      session,
      user: session.user,
      can,
      manage: makeManage(session),
      staff: isStaffOf(can),
      superuser: session.roles.some((role) => role.superuser),
      role: roleLabel(session),
    };
  }, [session]);
  return <SessionContext.Provider value={value}>{children}</SessionContext.Provider>;
}

export function useSession() {
  const context = useContext(SessionContext);
  if (!context) throw new Error("useSession must be used inside SessionProvider");
  return context;
}
