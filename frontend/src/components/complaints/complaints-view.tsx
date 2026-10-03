import { useState } from "react";
import { Link, useNavigate, useRouterState } from "@tanstack/react-router";
import { motion } from "motion/react";
import { FilePlus2, Filter, Inbox, Search, UserCheck } from "lucide-react";
import { useApi } from "@/lib/query";
import { useSession } from "@/components/providers/session";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { Input } from "@/components/ui/form";
import { Select, SelectContent, SelectGroup, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { EmptyState, PageHeader, Pagination, Skeleton, Tabs } from "@/components/ui/misc";
import { Stagger, StaggerItem } from "@/components/ui/motion";
import { ComplaintRow } from "./complaint-row";
import { COMPLAINT_PRIORITIES, complaintsHref, complaintsPath, type ComplaintFilters } from "@/lib/complaints";
import type { Complaint, PageResponse } from "@/lib/types";

const PRIORITY_ITEMS: Record<string, string> = {
  any: "Any priority",
  ...Object.fromEntries(COMPLAINT_PRIORITIES.map((priority) => [priority, priority.charAt(0).toUpperCase() + priority.slice(1)])),
};

const TABS = [
  { value: "all", label: "All" },
  { value: "pending", label: "Pending" },
  { value: "reviewed", label: "In review" },
  { value: "resolved", label: "Resolved" },
  { value: "rejected", label: "Rejected" },
];

export function ComplaintsView({ filters }: { filters: ComplaintFilters }) {
  const navigate = useNavigate();
  const { can, user, staff } = useSession();
  const pending = useRouterState({ select: (state) => state.status === "pending" });
  const [search, setSearch] = useState(filters.q ?? "");
  const { data, isLoading, isFetching } = useApi<PageResponse<Complaint>>(complaintsPath(filters, user.id), { keepPrevious: true });

  const go = (next: Partial<ComplaintFilters>) => void navigate({ href: complaintsHref({ ...filters, page: 0, ...next }) });

  return (
    <div>
      <PageHeader
        title={staff ? "Complaints" : "My complaints"}
        description={staff ? "Review, assign and resolve complaints you have access to." : "Everything you have filed or that concerns you."}
        icon={<Inbox className="size-5" />}
        actions={
          can("complaints", "write") && (
            <Link to="/complaints/new">
              <Button className="h-10 gradient-brand px-4 text-white shadow-md shadow-primary/25">
                <FilePlus2 /> New complaint
              </Button>
            </Link>
          )
        }
      />

      <Card className="mb-5 p-4">
        <div className="flex flex-wrap items-center gap-3">
          <Tabs id="status" tabs={TABS} value={(filters.status ?? "all") as string} onChange={(value) => go({ status: value === "all" ? undefined : value })} />
          <form
            className="relative ml-auto w-full sm:w-64"
            onSubmit={(event) => {
              event.preventDefault();
              go({ q: search.trim() || undefined });
            }}
          >
            <Search className="pointer-events-none absolute left-3.5 top-1/2 size-4 -translate-y-1/2 text-muted-foreground" />
            <Input value={search} onChange={(event) => setSearch(event.target.value)} placeholder="Search titles…" className="pl-10" />
          </form>
          <div className="flex items-center gap-2">
            <Filter className="size-4 text-muted-foreground" />
            <Select
              items={PRIORITY_ITEMS}
              value={filters.priority ?? "any"}
              onValueChange={(value) => go({ priority: !value || value === "any" ? undefined : value })}
            >
              <SelectTrigger className="h-10 w-36 rounded-xl bg-background/60 px-3.5" aria-label="Filter by priority">
                <SelectValue />
              </SelectTrigger>
              <SelectContent alignItemWithTrigger={false}>
                <SelectGroup>
                  {Object.entries(PRIORITY_ITEMS).map(([value, label]) => (
                    <SelectItem key={value} value={value}>
                      {label}
                    </SelectItem>
                  ))}
                </SelectGroup>
              </SelectContent>
            </Select>
          </div>
          {staff && (
            <Button variant={filters.mine ? "default" : "outline"} className="h-10" onClick={() => go({ mine: filters.mine ? undefined : "assigned" })}>
              <UserCheck /> Assigned to me
            </Button>
          )}
        </div>
      </Card>

      <Card className={pending || (isFetching && !isLoading) ? "opacity-70 transition-opacity" : "transition-opacity"}>
        {isLoading ? (
          <div className="space-y-2 p-4">
            {[0, 1, 2, 3, 4].map((index) => (
              <Skeleton key={index} className="h-16" />
            ))}
          </div>
        ) : data && data.content.length > 0 ? (
          <div className="p-2">
            <Stagger key={`${filters.page}-${filters.status}-${filters.priority}-${filters.q}`}>
              {data.content.map((complaint) => (
                <StaggerItem key={complaint.id}>
                  <ComplaintRow complaint={complaint} showStudent={staff} />
                </StaggerItem>
              ))}
            </Stagger>
            <div className="px-3 pb-2">
              <Pagination page={data.number} totalPages={data.totalPages} onChange={(page) => void navigate({ href: complaintsHref({ ...filters, page }) })} />
            </div>
          </div>
        ) : (
          <EmptyState
            icon={<Inbox className="size-7" />}
            title="No complaints found"
            description={filters.status || filters.priority || filters.q ? "Try changing or clearing the filters." : "Nothing here yet."}
            action={
              (filters.status || filters.priority || filters.q || filters.mine) && (
                <motion.div whileTap={{ scale: 0.96 }}>
                  <Button variant="outline" onClick={() => navigate({ to: "/complaints" })}>
                    Clear filters
                  </Button>
                </motion.div>
              )
            }
          />
        )}
      </Card>
      {data && <p className="mt-3 text-center text-xs text-muted-foreground">{data.totalElements} result{data.totalElements === 1 ? "" : "s"}</p>}
    </div>
  );
}
