import { useState } from "react";
import { Link, useNavigate } from "@tanstack/react-router";
import { ArrowUpRight, Building2, Plus } from "lucide-react";
import { api } from "@/lib/api";
import { useAction, useApi } from "@/lib/query";
import { DEPARTMENTS_PATH } from "@/lib/users";
import { useSession } from "@/components/providers/session";
import { useToast } from "@/components/ui/toast";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Field, Input, Switch, Textarea } from "@/components/ui/form";
import { EmptyState, PageHeader, Skeleton, Spinner } from "@/components/ui/misc";
import { Modal } from "@/components/ui/modal";
import { Hover, Stagger, StaggerItem } from "@/components/ui/motion";
import { CustomFieldsForm, type CustomValues } from "@/components/custom-fields/custom-fields-form";
import type { Department, PageResponse } from "@/lib/types";

export function DepartmentsView() {
  const navigate = useNavigate();
  const toast = useToast();
  const { can } = useSession();
  const { data, isLoading } = useApi<PageResponse<Department>>(DEPARTMENTS_PATH);
  const [creating, setCreating] = useState(false);
  const [name, setName] = useState("");
  const [code, setCode] = useState("");
  const [description, setDescription] = useState("");
  const [active, setActive] = useState(true);
  const [custom, setCustom] = useState<CustomValues>({});

  const create = useAction(() => api.post<Department>("/departments", { name, code, description, active, customFields: custom }), {
    invalidate: ["/departments"],
    onSuccess: (department) => {
      toast.success("Department created");
      setCreating(false);
      navigate({ to: "/departments/$id", params: { id: department.id } });
    },
    onError: (error) => toast.error("Could not create department", error.message),
  });

  return (
    <div>
      <PageHeader
        title="Departments"
        description="Faculties and units. Department scoped roles act within their own department."
        icon={<Building2 className="size-5" />}
        actions={
          can("departments", "write") && (
            <Button className="h-10 gradient-brand text-white shadow-md shadow-primary/25" onClick={() => setCreating(true)}>
              <Plus /> New department
            </Button>
          )
        }
      />
      {isLoading ? (
        <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-3">
          {[0, 1, 2].map((index) => (
            <Skeleton key={index} className="h-36" />
          ))}
        </div>
      ) : data && data.content.length > 0 ? (
        <Stagger className="grid gap-4 sm:grid-cols-2 xl:grid-cols-3">
          {data.content.map((department) => (
            <StaggerItem key={department.id}>
              <Hover>
                <Link to="/departments/$id" params={{ id: department.id }} className="group relative block overflow-hidden rounded-2xl border bg-card p-5 transition hover:border-primary/40 hover:shadow-lg hover:shadow-primary/10">
                  <div className="flex items-start justify-between">
                    <span className="grid size-11 place-items-center rounded-2xl bg-primary/10 text-primary transition group-hover:gradient-brand group-hover:text-white">
                      <Building2 className="size-5" />
                    </span>
                    <ArrowUpRight className="size-4 text-muted-foreground transition group-hover:text-primary" />
                  </div>
                  <h3 className="mt-4 text-lg font-semibold">{department.name}</h3>
                  <p className="mt-1 line-clamp-2 min-h-10 text-sm text-muted-foreground">{department.description || "No description"}</p>
                  <div className="mt-4 flex gap-2">
                    <Badge tone="brand">{department.code}</Badge>
                    <Badge tone={department.active ? "success" : "neutral"} dot>
                      {department.active ? "Active" : "Inactive"}
                    </Badge>
                  </div>
                </Link>
              </Hover>
            </StaggerItem>
          ))}
        </Stagger>
      ) : (
        <EmptyState icon={<Building2 className="size-7" />} title="No departments" description="Create the first department to organise people." />
      )}

      <Modal
        open={creating}
        onClose={() => setCreating(false)}
        title="New department"
        footer={
          <>
            <Button variant="outline" onClick={() => setCreating(false)}>
              Cancel
            </Button>
            <Button className="gradient-brand text-white" disabled={!name.trim() || !code.trim() || create.isPending} onClick={() => create.mutate(undefined)}>
              {create.isPending && <Spinner />} Create
            </Button>
          </>
        }
      >
        <div className="space-y-4">
          <div className="grid gap-4 sm:grid-cols-[minmax(0,1fr)_140px]">
            <Field label="Name">
              <Input value={name} onChange={(event) => setName(event.target.value)} />
            </Field>
            <Field label="Code">
              <Input value={code} onChange={(event) => setCode(event.target.value.toUpperCase())} placeholder="CS" />
            </Field>
          </div>
          <Field label="Description">
            <Textarea rows={2} value={description} onChange={(event) => setDescription(event.target.value)} />
          </Field>
          <label className="flex items-center gap-3 text-sm">
            <Switch checked={active} onChange={setActive} label="Active" /> Active
          </label>
          <CustomFieldsForm entity="departments" value={custom} onChange={setCustom} />
        </div>
      </Modal>
    </div>
  );
}
