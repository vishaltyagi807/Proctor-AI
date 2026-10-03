import { useMemo, useState } from "react";
import { motion } from "motion/react";
import { Lock, Search } from "lucide-react";
import { Input } from "@/components/ui/form";
import { cn } from "@/lib/utils";
import type { Action, Permission, Scope } from "@/lib/types";

const ACTIONS: Action[] = ["read", "write", "update", "delete"];
const SCOPES: Scope[] = ["own", "department", "all"];
const SCOPE_LABEL: Record<Scope, string> = { own: "Own", department: "Dept", all: "All" };

const ENTITIES: { key: string; label: string; hint: string; group: string }[] = [
  { key: "dashboard", label: "Dashboard", hint: "Statistics and overview", group: "Complaints" },
  { key: "complaints", label: "Complaints", hint: "Create, review and resolve", group: "Complaints" },
  { key: "complaint_comments", label: "Comments", hint: "Discussion on complaints", group: "Complaints" },
  { key: "complaint_files", label: "Files & evidence", hint: "Attachments and evidence", group: "Complaints" },
  { key: "complaint_history", label: "History", hint: "Status timeline", group: "Complaints" },
  { key: "complaint_reporter", label: "Reporter identity", hint: "See or reveal who raised it", group: "Complaints" },
  { key: "users", label: "Users", hint: "Accounts", group: "People & access" },
  { key: "user_roles", label: "User roles", hint: "Role assignments", group: "People & access" },
  { key: "departments", label: "Departments", hint: "Faculties and units", group: "People & access" },
  { key: "department_users", label: "Department members", hint: "Membership", group: "People & access" },
  { key: "roles", label: "Roles", hint: "Create and edit roles", group: "People & access" },
  { key: "role_permission", label: "Role permissions", hint: "Grant permissions to roles", group: "People & access" },
  { key: "permissions", label: "Permission catalog", hint: "Read available permissions", group: "People & access" },
  { key: "same_role_peers", label: "Same-role peers", hint: "People at your level who share your role, and that role itself", group: "Hierarchy" },
  { key: "parallel_role_peers", label: "Parallel-role peers", hint: "People at your level in a different role, and those roles", group: "Hierarchy" },
  { key: "senior_users", label: "Senior people", hint: "See people ranked above you", group: "Hierarchy" },
  { key: "face_recognition", label: "Photo recognition", hint: "Identify people in uploaded photos", group: "Face recognition" },
  { key: "face_live_recognition", label: "Live camera", hint: "Identify people with the live camera", group: "Face recognition" },
  { key: "face_match_details", label: "Match details", hint: "See confidence and email of matches", group: "Face recognition" },
  { key: "face_enrollments", label: "Enrollments", hint: "View, enroll, replace and remove faces", group: "Face recognition" },
  { key: "face_enroll_lock", label: "Enrollment lock", hint: "Reset self-enrollment attempts", group: "Face recognition" },
  { key: "face_import", label: "Bulk import", hint: "ZIP import and its template", group: "Face recognition" },
  { key: "face_audit", label: "Activity log", hint: "Who scanned or enrolled whom, and when", group: "Face recognition" },
  { key: "custom_fields", label: "Custom fields", hint: "Define extra fields", group: "Platform" },
  { key: "notifications", label: "Notifications", hint: "Read and send messages", group: "Platform" },
  { key: "notification_devices", label: "Devices", hint: "Push devices", group: "Platform" },
  { key: "integrations", label: "Integrations", hint: "FCM push configuration", group: "Platform" },
  { key: "system_monitor", label: "System monitor", hint: "Live CPU, memory, services, database and cache", group: "Platform" },
];

export function PermissionMatrix({ catalog, selected, onChange, readOnly }: { catalog: Permission[]; selected: Set<string>; onChange: (next: Set<string>) => void; readOnly?: boolean }) {
  const [filter, setFilter] = useState("");

  const index = useMemo(() => {
    const map = new Map<string, Map<Action, Permission[]>>();
    catalog.forEach((permission) => {
      const actions = map.get(permission.entity) ?? new Map<Action, Permission[]>();
      actions.set(permission.action, [...(actions.get(permission.action) ?? []), permission]);
      map.set(permission.entity, actions);
    });
    return map;
  }, [catalog]);

  const rows = ENTITIES.filter((entity) => index.has(entity.key) && `${entity.label} ${entity.hint} ${entity.group}`.toLowerCase().includes(filter.toLowerCase()));
  const groups = Array.from(new Set(rows.map((row) => row.group)));

  function choose(entity: string, action: Action, scope: Scope | null) {
    const options = index.get(entity)?.get(action) ?? [];
    const next = new Set(selected);
    options.forEach((option) => next.delete(option.id));
    if (scope) {
      const match = options.find((option) => option.scope === scope);
      if (match) next.add(match.id);
    }
    onChange(next);
  }

  return (
    <div className="space-y-5">
      <div className="relative max-w-sm">
        <Search className="pointer-events-none absolute left-3.5 top-1/2 size-4 -translate-y-1/2 text-muted-foreground" />
        <Input placeholder="Filter permissions…" value={filter} onChange={(event) => setFilter(event.target.value)} className="pl-10" />
      </div>
      {readOnly && (
        <p className="flex items-center gap-2 rounded-xl bg-muted/60 px-3.5 py-2.5 text-sm text-muted-foreground">
          <Lock className="size-4" /> You cannot change the permissions of this role.
        </p>
      )}
      {groups.map((group) => (
        <div key={group} className="overflow-hidden rounded-2xl border">
          <div className="border-b bg-muted/40 px-4 py-2.5 text-xs font-medium uppercase tracking-[0.14em] text-muted-foreground">{group}</div>
          <div className="overflow-x-auto scrollbar-thin">
            <table className="w-full min-w-[720px] text-sm">
              <thead>
                <tr className="border-b text-left text-xs text-muted-foreground">
                  <th className="px-4 py-2.5 font-medium">Resource</th>
                  {ACTIONS.map((action) => (
                    <th key={action} className="px-2 py-2.5 font-medium capitalize">
                      {action === "write" ? "Create" : action}
                    </th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {rows
                  .filter((row) => row.group === group)
                  .map((row) => (
                    <tr key={row.key} className="border-b last:border-0">
                      <td className="px-4 py-3">
                        <p className="font-medium">{row.label}</p>
                        <p className="text-xs text-muted-foreground">{row.hint}</p>
                      </td>
                      {ACTIONS.map((action) => {
                        const options = index.get(row.key)?.get(action) ?? [];
                        if (options.length === 0) return <td key={action} className="px-2 py-3 text-muted-foreground/40">—</td>;
                        const active = options.find((option) => selected.has(option.id))?.scope ?? null;
                        return (
                          <td key={action} className="px-2 py-3">
                            <div className="inline-flex rounded-lg bg-muted p-0.5">
                              <ScopeButton label="None" active={active === null} disabled={readOnly} onClick={() => choose(row.key, action, null)} />
                              {SCOPES.filter((scope) => options.some((option) => option.scope === scope)).map((scope) => (
                                <ScopeButton key={scope} label={SCOPE_LABEL[scope]} active={active === scope} disabled={readOnly} onClick={() => choose(row.key, action, scope)} highlight />
                              ))}
                            </div>
                          </td>
                        );
                      })}
                    </tr>
                  ))}
              </tbody>
            </table>
          </div>
        </div>
      ))}
    </div>
  );
}

function ScopeButton({ label, active, disabled, onClick, highlight }: { label: string; active: boolean; disabled?: boolean; onClick: () => void; highlight?: boolean }) {
  return (
    <button type="button" disabled={disabled} onClick={onClick} className={cn("relative rounded-md px-2.5 py-1 text-xs font-medium transition disabled:cursor-not-allowed", active ? "text-white" : "text-muted-foreground hover:text-foreground")}>
      {active && <motion.span layoutId={undefined} initial={{ scale: 0.8, opacity: 0 }} animate={{ scale: 1, opacity: 1 }} className={cn("absolute inset-0 rounded-md", highlight ? "gradient-brand shadow" : "bg-muted-foreground/60")} />}
      <span className="relative">{label}</span>
    </button>
  );
}
