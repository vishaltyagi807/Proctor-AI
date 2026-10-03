import { useCallback, useState } from "react";
import { Link, useNavigate, useRouterState } from "@tanstack/react-router";
import { motion } from "motion/react";
import { ArrowDown, ArrowUp, ArrowUpDown, BadgeCheck, CircleDashed, Lock, SearchX, Upload, UserPlus, Users } from "lucide-react";
import { api } from "@/lib/api";
import { useAction, useApi } from "@/lib/query";
import { formatDate, timeAgo } from "@/lib/format";
import { useSession } from "@/components/providers/session";
import { RELATION_LABEL } from "@/lib/permissions";
import { useToast } from "@/components/ui/toast";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { Avatar, EmptyState, PageHeader, Skeleton, Spinner, Table, Td, Th } from "@/components/ui/misc";
import { PageNav } from "@/components/ui/page-nav";
import { Select, SelectContent, SelectGroup, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { UsersToolbar } from "./users-toolbar";
import { ColumnsMenu } from "./user-columns";
import { formatCustomValue, useUserColumns, type UserColumn } from "@/hooks/use-user-columns";
import { Modal } from "@/components/ui/modal";
import { ImportModal } from "./import-modal";
import { UserForm, emptyUser, type UserFormValues } from "./user-form";
import { DEFAULT_USER_PAGE_SIZE, USER_PAGE_SIZES, USER_SORTS, hasUserFilters, nextUserSort, usersHref, usersPath, type UserFilters, type UserSortField } from "@/lib/users";
import type { PageResponse, UserInfo } from "@/lib/types";
import { Hint } from "@/components/ui/hint";

export function UsersView({ filters }: { filters: UserFilters }) {
  const navigate = useNavigate();
  const toast = useToast();
  const { can, manage, user: me } = useSession();
  const pending = useRouterState({ select: (state) => state.status === "pending" });
  const [creating, setCreating] = useState(false);
  const [importing, setImporting] = useState(false);
  const [form, setForm] = useState<UserFormValues>(emptyUser);
  const { data, isLoading, isFetching } = useApi<PageResponse<UserInfo>>(usersPath(filters), { keepPrevious: true });

  const go = useCallback(
    (next: Partial<UserFilters>, options?: { replace?: boolean }) => void navigate({ href: usersHref({ ...filters, page: 0, ...next }), replace: options?.replace }),
    [filters, navigate],
  );
  const goToPage = (page: number) => void navigate({ href: usersHref({ ...filters, page }) });
  const sort = filters.sort ?? "newest";
  const size = filters.size ?? DEFAULT_USER_PAGE_SIZE;
  const columns = useUserColumns();
  const showEmailColumn = columns.visible.some((column) => column.id === "email");
  const sortIcon = (field: UserSortField) => {
    const current = USER_SORTS[sort];
    if (current.field !== field) return <ArrowUpDown className="size-3.5 opacity-40 transition group-hover/sort:opacity-80" />;
    return current.direction === "asc" ? <ArrowUp className="size-3.5 text-primary" /> : <ArrowDown className="size-3.5 text-primary" />;
  };
  const renderCell = (column: UserColumn, user: UserInfo) => {
    if (column.kind === "custom") {
      const text = formatCustomValue(column.definition, user.customFields?.[column.definition.key]);
      return <span className={text === "—" ? "text-xs text-muted-foreground" : "block max-w-56 truncate"}>{text}</span>;
    }
    switch (column.id) {
      case "user":
        return (
          <Link to="/users/$id" params={{ id: user.id }} className="flex items-center gap-3">
            <Avatar name={user.name} size={38} />
            <span className="min-w-0">
              <span className="flex items-center gap-1.5 whitespace-nowrap font-medium group-hover:text-primary">
                {user.name}
                {user.id !== me.id && !manage.user(user, "update") && (
                  <Hint label={RELATION_LABEL[manage.relation(user)]}>
                    <Lock className="size-3 text-muted-foreground" aria-label={RELATION_LABEL[manage.relation(user)]} />
                  </Hint>
                )}
              </span>
              {!showEmailColumn && <span className="block text-xs text-muted-foreground">{user.email}</span>}
            </span>
          </Link>
        );
      case "email":
        return <span className="text-muted-foreground">{user.email}</span>;
      case "roles":
        return (
          <div className="flex flex-wrap gap-1.5">
            {user.roles.length ? (
              user.roles.map((role) => (
                <Badge key={role.id} tone={role.superuser ? "violet" : "brand"} className="capitalize">
                  {role.name.replaceAll("_", " ")}
                </Badge>
              ))
            ) : (
              <span className="text-xs text-muted-foreground">—</span>
            )}
          </div>
        );
      case "departments":
        return (
          <div className="flex flex-wrap gap-1.5">
            {user.departments.length ? (
              user.departments.map((department) => (
                <Hint key={department.id} label={department.name}>
                  <Badge>{department.code}</Badge>
                </Hint>
              ))
            ) : (
              <span className="text-xs text-muted-foreground">—</span>
            )}
          </div>
        );
      case "status":
        return (
          <Badge tone={user.enabled ? "success" : "danger"} dot>
            {user.enabled ? "Active" : "Disabled"}
          </Badge>
        );
      case "verified":
        return (
          <Badge tone={user.verified ? "info" : "warning"}>
            {user.verified ? <BadgeCheck className="size-3" /> : <CircleDashed className="size-3" />}
            {user.verified ? "Verified" : "Unverified"}
          </Badge>
        );
      case "joined":
        return user.createdAt ? (
          <Hint label={formatDate(user.createdAt, true)}>
            <span className="text-muted-foreground">{timeAgo(user.createdAt)}</span>
          </Hint>
        ) : (
          <span className="text-xs text-muted-foreground">—</span>
        );
      case "updated":
        return user.updatedAt ? (
          <Hint label={formatDate(user.updatedAt, true)}>
            <span className="text-muted-foreground">{timeAgo(user.updatedAt)}</span>
          </Hint>
        ) : (
          <span className="text-xs text-muted-foreground">Never</span>
        );
      case "id":
        return (
          <Hint label={user.id}>
            <code className="rounded-md bg-muted px-1.5 py-0.5 font-mono text-xs text-muted-foreground">{user.id.slice(0, 8)}</code>
          </Hint>
        );
    }
  };
  const from = data && data.totalElements > 0 ? data.number * data.size + 1 : 0;
  const to = data ? Math.min((data.number + 1) * data.size, data.totalElements) : 0;

  const create = useAction(
    () =>
      api.post<UserInfo>("/users", {
        name: form.name,
        email: form.email,
        password: form.password,
        enabled: form.enabled,
        verified: form.verified,
        roles: form.roles,
        departments: form.departments,
        customFields: form.customFields,
      }),
    {
      invalidate: ["/users"],
      onSuccess: (user) => {
        toast.success("User created", `${user.name} can now sign in.`);
        setCreating(false);
        setForm(emptyUser);
        navigate({ to: "/users/$id", params: { id: user.id } });
      },
      onError: (error) => toast.error("Could not create user", error.message),
    },
  );

  return (
    <div>
      <PageHeader
        title="Users"
        description="Accounts you can see, with their roles and departments."
        icon={<Users className="size-5" />}
        actions={
          <>
            {can("users", "write") && (
              <Button variant="outline" className="h-10" onClick={() => setImporting(true)}>
                <Upload /> Import
              </Button>
            )}
            {can("users", "write") && (
              <Button className="h-10 gradient-brand text-white shadow-md shadow-primary/25" onClick={() => setCreating(true)}>
                <UserPlus /> Add user
              </Button>
            )}
          </>
        }
      />

      <UsersToolbar filters={filters} go={go} actions={<ColumnsMenu state={columns} />} />

      <Card className={pending || (isFetching && !isLoading) ? "opacity-70 transition-opacity" : "transition-opacity"}>
        {isLoading ? (
          <div className="space-y-2 p-4">
            {[0, 1, 2, 3, 4].map((index) => (
              <Skeleton key={index} className="h-14" />
            ))}
          </div>
        ) : data && data.content.length > 0 ? (
          <>
            <Table>
              <thead className="border-b">
                <tr>
                  {columns.visible.map((column) => {
                    const sortField = column.kind === "builtin" ? column.sortField : undefined;
                    return (
                      <Th key={column.id} className={column.id === "user" ? "sticky left-0 z-10 bg-card" : undefined}>
                        {sortField ? (
                          <button type="button" onClick={() => go({ sort: nextUserSort(sort, sortField) })} className="group/sort inline-flex items-center gap-1.5 uppercase tracking-wide transition hover:text-foreground">
                            {column.label} {sortIcon(sortField)}
                          </button>
                        ) : (
                          column.label
                        )}
                      </Th>
                    );
                  })}
                </tr>
              </thead>
              <tbody>
                {data.content.map((user, index) => (
                  <motion.tr key={user.id} initial={{ opacity: 0, y: 8 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: index * 0.03 }} className="group border-b last:border-0 transition hover:bg-muted/50">
                    {columns.visible.map((column) => (
                      <Td key={column.id} className={column.id === "user" ? "sticky left-0 z-10 bg-card transition group-hover:bg-muted" : "whitespace-nowrap"}>
                        {renderCell(column, user)}
                      </Td>
                    ))}
                  </motion.tr>
                ))}
              </tbody>
            </Table>
            <div className="flex flex-col gap-3 border-t px-4 py-3 sm:flex-row sm:items-center sm:justify-between">
              <div className="flex flex-wrap items-center gap-x-4 gap-y-2 text-sm text-muted-foreground">
                <span>
                  Showing <span className="font-medium text-foreground tabular-nums">{from}–{to}</span> of{" "}
                  <span className="font-medium text-foreground tabular-nums">{data.totalElements}</span> user{data.totalElements === 1 ? "" : "s"}
                </span>
                <span className="flex items-center gap-2">
                  Rows
                  <Select
                    items={Object.fromEntries(USER_PAGE_SIZES.map((value) => [String(value), String(value)]))}
                    value={String(size)}
                    onValueChange={(value) => value && go({ size: Number(value) })}
                  >
                    <SelectTrigger size="sm" className="h-8 w-[4.5rem] rounded-lg bg-background/60" aria-label="Rows per page">
                      <SelectValue />
                    </SelectTrigger>
                    <SelectContent alignItemWithTrigger={false} side="top">
                      <SelectGroup>
                        {USER_PAGE_SIZES.map((value) => (
                          <SelectItem key={value} value={String(value)}>
                            {value}
                          </SelectItem>
                        ))}
                      </SelectGroup>
                    </SelectContent>
                  </Select>
                </span>
              </div>
              <PageNav page={data.number} totalPages={data.totalPages} onChange={goToPage} />
            </div>
          </>
        ) : (
          <EmptyState
            icon={hasUserFilters(filters) ? <SearchX className="size-7" /> : <Users className="size-7" />}
            title={hasUserFilters(filters) ? "No users match these filters" : "No users yet"}
            description={hasUserFilters(filters) ? "Try a different search, or clear some filters." : "Add a user or import a file to get started."}
            action={
              hasUserFilters(filters) ? (
                <Button variant="outline" onClick={() => go({ q: undefined, enabled: undefined, verified: undefined, joined: undefined })}>
                  Clear filters
                </Button>
              ) : undefined
            }
          />
        )}
      </Card>

      <Modal
        open={creating}
        onClose={() => setCreating(false)}
        title="Add a user"
        description="Create an account and assign roles and departments."
        size="lg"
        footer={
          <>
            <Button variant="outline" onClick={() => setCreating(false)}>
              Cancel
            </Button>
            <Button className="gradient-brand text-white" disabled={create.isPending || !form.name || !form.email || form.password.length < 8} onClick={() => create.mutate(undefined)}>
              {create.isPending && <Spinner />} Create user
            </Button>
          </>
        }
      >
        <UserForm mode="create" value={form} onChange={setForm} />
      </Modal>
      <ImportModal open={importing} onClose={() => setImporting(false)} />
    </div>
  );
}
