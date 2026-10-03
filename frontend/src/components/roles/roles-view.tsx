import { useState } from "react";
import { Link, useNavigate } from "@tanstack/react-router";
import { EyeOff, Lock, Plus, ShieldCheck, Sparkles } from "lucide-react";
import { api } from "@/lib/api";
import { useAction, useApi } from "@/lib/query";
import { ROLES_PATH } from "@/lib/users";
import { useSession } from "@/components/providers/session";
import { useToast } from "@/components/ui/toast";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Field, Input, Switch, Textarea } from "@/components/ui/form";
import { EmptyState, PageHeader, Skeleton, Spinner } from "@/components/ui/misc";
import { Modal } from "@/components/ui/modal";
import { Hover, Stagger, StaggerItem } from "@/components/ui/motion";
import type { PageResponse, Role } from "@/lib/types";
import { RelationBadge } from "@/components/access/relation";

export function RolesView() {
  const navigate = useNavigate();
  const toast = useToast();
  const { can, manage } = useSession();
  const { data, isLoading } = useApi<PageResponse<Role>>(ROLES_PATH);
  const minLevel = Math.max(1, manage.minRoleLevel);
  const [creating, setCreating] = useState(false);
  const [name, setName] = useState("");
  const [description, setDescription] = useState("");
  const [level, setLevel] = useState(Math.max(50, minLevel));
  const [reveal, setReveal] = useState(false);

  const create = useAction(() => api.post<{ id: string }>("/roles", { name, description, level, revealIdentity: reveal }), {
    invalidate: ["/roles"],
    onSuccess: (role) => {
      toast.success("Role created", "Now choose what it can do.");
      setCreating(false);
      navigate({ to: "/roles/$id", params: { id: role.id } });
    },
    onError: (error) => toast.error("Could not create role", error.message),
  });

  return (
    <div>
      <PageHeader
        title="Roles & access"
        description="Design who can do what. Changes apply to everyone holding the role immediately."
        icon={<ShieldCheck className="size-5" />}
        actions={
          can("roles", "write") && (
            <Button className="h-10 gradient-brand text-white shadow-md shadow-primary/25" onClick={() => setCreating(true)}>
              <Plus /> New role
            </Button>
          )
        }
      />
      {isLoading ? (
        <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-3">
          {[0, 1, 2].map((index) => (
            <Skeleton key={index} className="h-40" />
          ))}
        </div>
      ) : data && data.content.length > 0 ? (
        <Stagger className="grid gap-4 sm:grid-cols-2 xl:grid-cols-3">
          {data.content.map((role) => (
            <StaggerItem key={role.id}>
              <Hover>
                <Link to="/roles/$id" params={{ id: role.id }} className="group relative block overflow-hidden rounded-2xl border bg-card p-5 transition hover:border-primary/40 hover:shadow-lg hover:shadow-primary/10">
                  <div className="absolute -right-8 -top-8 size-28 rounded-full gradient-brand opacity-[0.12] blur-2xl transition group-hover:opacity-25" />
                  <div className="flex items-start justify-between">
                    <span className="grid size-11 place-items-center rounded-2xl gradient-brand text-white shadow-lg shadow-primary/25">{role.superuser ? <Sparkles className="size-5" /> : <ShieldCheck className="size-5" />}</span>
                    <div className="flex gap-1.5">
                      {role.system && (
                        <Badge>
                          <Lock className="size-3" /> System
                        </Badge>
                      )}
                      {role.superuser && <Badge tone="violet">Full access</Badge>}
                    </div>
                  </div>
                  <h3 className="mt-4 text-lg font-semibold capitalize">{role.name.replaceAll("_", " ")}</h3>
                  <p className="mt-1 line-clamp-2 min-h-10 text-sm text-muted-foreground">{role.description || "No description"}</p>
                  <div className="mt-4 flex items-center gap-2 text-xs text-muted-foreground">
                    <Badge tone="brand">Level {role.level}</Badge>
                    <RelationBadge relation={manage.roleRelation(role)} />
                    {role.revealIdentity && (
                      <Badge tone="info">
                        <EyeOff className="size-3" /> Identity shown
                      </Badge>
                    )}
                  </div>
                </Link>
              </Hover>
            </StaggerItem>
          ))}
        </Stagger>
      ) : (
        <EmptyState icon={<ShieldCheck className="size-7" />} title="No roles to show" />
      )}

      <Modal
        open={creating}
        onClose={() => setCreating(false)}
        title="Create a role"
        description={
          minLevel > manage.level
            ? `Lower level means more privilege. Your level is ${manage.level}, so new roles must be level ${minLevel} or lower in rank.`
            : "Lower level means more privilege. You can create roles at your own level or below."
        }
        footer={
          <>
            <Button variant="outline" onClick={() => setCreating(false)}>
              Cancel
            </Button>
            <Button className="gradient-brand text-white" disabled={!name.trim() || create.isPending} onClick={() => create.mutate(undefined)}>
              {create.isPending && <Spinner />} Create role
            </Button>
          </>
        }
      >
        <div className="space-y-4">
          <Field label="Name">
            <Input value={name} onChange={(event) => setName(event.target.value)} placeholder="e.g. counselor" />
          </Field>
          <Field label="Description">
            <Textarea rows={2} value={description} onChange={(event) => setDescription(event.target.value)} />
          </Field>
          <Field label={`Level: ${level}`} hint={`Allowed range starts at ${minLevel}. Students are typically 50.`}>
            <input type="range" min={minLevel} max={Math.max(200, minLevel)} value={level} onChange={(event) => setLevel(Number(event.target.value))} className="w-full accent-[var(--brand)]" />
          </Field>
          <label className="flex items-center gap-3 text-sm">
            <Switch checked={reveal} onChange={setReveal} label="Reveal identity" />
            <span>
              Show members&apos; identity as reporters
              <span className="block text-xs text-muted-foreground">Students can see who reported a complaint when this role raised it.</span>
            </span>
          </label>
        </div>
      </Modal>
    </div>
  );
}
