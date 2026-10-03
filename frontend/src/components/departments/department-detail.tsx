import { useState } from "react";
import { Link, useNavigate } from "@tanstack/react-router";
import { AnimatePresence, motion } from "motion/react";
import { ArrowLeft, Building2, Save, Trash2, UserMinus, UserPlus } from "lucide-react";
import { api } from "@/lib/api";
import { useAction, useApi } from "@/lib/query";
import { useSession } from "@/components/providers/session";
import { useToast } from "@/components/ui/toast";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Field, Input, Select, Switch, Textarea } from "@/components/ui/form";
import { Avatar, EmptyState, Skeleton, Spinner } from "@/components/ui/misc";
import { Modal } from "@/components/ui/modal";
import { FadeIn } from "@/components/ui/motion";
import { CustomFieldsForm, type CustomValues } from "@/components/custom-fields/custom-fields-form";
import type { Department, PageResponse, UserInfo } from "@/lib/types";
import { Hint } from "@/components/ui/hint";

type Member = { id: string; email: string; name: string };

export function DepartmentDetail({ id }: { id: string }) {
  const { data, isLoading } = useApi<Department>(`/departments/${id}`);
  if (isLoading) return <Skeleton className="h-96" />;
  if (!data) {
    return (
      <EmptyState
        icon={<Building2 className="size-7" />}
        title="Department not found"
        action={
          <Link to="/departments">
            <Button variant="outline">Back to departments</Button>
          </Link>
        }
      />
    );
  }
  return <Editor key={data.updatedAt ?? data.id} department={data} />;
}

function Editor({ department }: { department: Department }) {
  const navigate = useNavigate();
  const toast = useToast();
  const { can } = useSession();
  const membersPath = `/departments/${department.id}/members`;
  const members = useApi<Member[]>(membersPath);
  const people = useApi<PageResponse<UserInfo>>(can("department_users", "write") ? "/users?size=100&sortBy=name&direction=asc" : null);
  const [name, setName] = useState(department.name);
  const [code, setCode] = useState(department.code);
  const [description, setDescription] = useState(department.description ?? "");
  const [active, setActive] = useState(department.active);
  const [custom, setCustom] = useState<CustomValues>(department.customFields ?? {});
  const [toAdd, setToAdd] = useState("");
  const [confirmDelete, setConfirmDelete] = useState(false);
  const editable = can("departments", "update");

  const save = useAction(() => api.put(`/departments/${department.id}`, { name, code, description, active, customFields: custom }), {
    invalidate: ["/departments"],
    onSuccess: () => toast.success("Department saved"),
    onError: (error) => toast.error("Could not save", error.message),
  });
  const add = useAction((userId: string) => api.put(membersPath, { userIds: [userId] }), {
    invalidate: [membersPath, "/users"],
    onSuccess: () => {
      setToAdd("");
      toast.success("Member added");
    },
    onError: (error) => toast.error("Could not add member", error.message),
  });
  const removeMember = useAction((userId: string) => api.delete(membersPath, { userIds: [userId] }), {
    invalidate: [membersPath, "/users"],
    onError: (error) => toast.error("Could not remove member", error.message),
  });
  const remove = useAction(() => api.delete(`/departments/${department.id}`), {
    invalidate: ["/departments"],
    onSuccess: () => {
      toast.success("Department deleted");
      navigate({ to: "/departments" });
    },
    onError: (error) => toast.error("Could not delete", error.message),
  });

  const memberIds = new Set(members.data?.map((member) => member.id));
  const candidates = people.data?.content.filter((person) => !memberIds.has(person.id)) ?? [];

  return (
    <div className="space-y-6">
      <FadeIn>
        <Link to="/departments" className="inline-flex items-center gap-1.5 text-sm text-muted-foreground transition hover:text-foreground">
          <ArrowLeft className="size-4" /> All departments
        </Link>
        <div className="mt-4 flex flex-wrap items-center gap-4">
          <span className="grid size-14 place-items-center rounded-2xl gradient-brand text-white shadow-lg shadow-primary/25">
            <Building2 className="size-6" />
          </span>
          <div className="flex-1">
            <h1 className="text-2xl font-semibold tracking-tight">{department.name}</h1>
            <div className="mt-1.5 flex gap-1.5">
              <Badge tone="brand">{department.code}</Badge>
              <Badge tone={department.active ? "success" : "neutral"} dot>
                {department.active ? "Active" : "Inactive"}
              </Badge>
            </div>
          </div>
          {can("departments", "delete") && (
            <Button variant="destructive" className="h-10" onClick={() => setConfirmDelete(true)}>
              <Trash2 /> Delete
            </Button>
          )}
        </div>
      </FadeIn>

      <div className="grid gap-6 xl:grid-cols-[minmax(0,1fr)_420px]">
        <Card className="self-start">
          <CardHeader>
            <CardTitle>Details</CardTitle>
          </CardHeader>
          <CardContent className="space-y-4">
            <div className="grid gap-4 sm:grid-cols-[minmax(0,1fr)_140px]">
              <Field label="Name">
                <Input value={name} onChange={(event) => setName(event.target.value)} disabled={!editable} />
              </Field>
              <Field label="Code">
                <Input value={code} onChange={(event) => setCode(event.target.value.toUpperCase())} disabled={!editable} />
              </Field>
            </div>
            <Field label="Description">
              <Textarea rows={3} value={description} onChange={(event) => setDescription(event.target.value)} disabled={!editable} />
            </Field>
            <label className="flex items-center gap-3 text-sm">
              <Switch checked={active} onChange={setActive} disabled={!editable} label="Active" /> Active
            </label>
            <CustomFieldsForm entity="departments" departmentIds={[department.id]} value={custom} onChange={setCustom} />
            {editable && (
              <div className="flex justify-end border-t pt-4">
                <Button className="h-10 gradient-brand text-white" disabled={save.isPending} onClick={() => save.mutate(undefined)}>
                  {save.isPending ? <Spinner /> : <Save />} Save changes
                </Button>
              </div>
            )}
          </CardContent>
        </Card>

        <Card className="self-start">
          <CardHeader>
            <div>
              <CardTitle>Members</CardTitle>
              <CardDescription>{members.data ? `${members.data.length} people` : "Loading"}</CardDescription>
            </div>
          </CardHeader>
          <CardContent className="space-y-4">
            {can("department_users", "write") && (
              <div className="flex gap-2">
                <Select
                  value={toAdd}
                  onValueChange={setToAdd}
                  placeholder="Add a person…"
                  aria-label="Add a person"
                  options={candidates.map((person) => ({ value: person.id, label: `${person.name} · ${person.email}` }))}
                />
                <Button className="h-10 gradient-brand text-white" disabled={!toAdd || add.isPending} onClick={() => add.mutate(toAdd)}>
                  {add.isPending ? <Spinner /> : <UserPlus />}
                </Button>
              </div>
            )}
            {members.isLoading ? (
              <Skeleton className="h-32" />
            ) : members.data && members.data.length > 0 ? (
              <ul className="space-y-1.5">
                <AnimatePresence initial={false}>
                  {members.data.map((member) => (
                    <motion.li key={member.id} layout initial={{ opacity: 0, x: -12 }} animate={{ opacity: 1, x: 0 }} exit={{ opacity: 0, x: 24, height: 0 }} className="group flex items-center gap-3 rounded-xl p-2.5 transition hover:bg-muted/60">
                      <Avatar name={member.name} size={36} />
                      <Link to="/users/$id" params={{ id: member.id }} className="min-w-0 flex-1">
                        <span className="block truncate text-sm font-medium">{member.name}</span>
                        <span className="block truncate text-xs text-muted-foreground">{member.email}</span>
                      </Link>
                      {can("department_users", "delete") && (
                        <Hint label={`Remove ${member.name}`}>
                          <button onClick={() => removeMember.mutate(member.id)} className="rounded-lg p-1.5 text-muted-foreground opacity-0 transition hover:bg-destructive/10 hover:text-destructive group-hover:opacity-100" aria-label={`Remove ${member.name}`}>
                            <UserMinus className="size-4" />
                          </button>
                        </Hint>
                      )}
                    </motion.li>
                  ))}
                </AnimatePresence>
              </ul>
            ) : (
              <p className="py-4 text-center text-sm text-muted-foreground">No members yet.</p>
            )}
          </CardContent>
        </Card>
      </div>

      <Modal
        open={confirmDelete}
        onClose={() => setConfirmDelete(false)}
        title={`Delete ${department.name}?`}
        size="sm"
        footer={
          <>
            <Button variant="outline" onClick={() => setConfirmDelete(false)}>
              Cancel
            </Button>
            <Button variant="destructive" disabled={remove.isPending} onClick={() => remove.mutate(undefined)}>
              {remove.isPending && <Spinner />} Delete
            </Button>
          </>
        }
      >
        <p className="text-sm text-muted-foreground">Members stay in the system but lose this department.</p>
      </Modal>
    </div>
  );
}
