import { useState } from "react";
import { Activity, FileArchive, KeyRound, Radar, RefreshCcw, ScanFace, Trash2, UserPlus } from "lucide-react";
import { useApi } from "@/lib/query";
import { formatDate, timeAgo } from "@/lib/format";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Select, SelectContent, SelectGroup, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { EmptyState, Pagination, Skeleton, Table, Td, Th } from "@/components/ui/misc";
import { Badge } from "@/components/ui/badge";
import type { FaceAuditEntry, FaceAuditEvent, PageResponse } from "@/lib/types";
import { Hint } from "@/components/ui/hint";

const EVENTS: Record<FaceAuditEvent, { label: string; icon: React.ComponentType<{ className?: string }> }> = {
  recognize: { label: "Photo recognition", icon: ScanFace },
  live_recognize: { label: "Live camera scan", icon: Radar },
  enroll: { label: "Enrolled", icon: UserPlus },
  replace: { label: "Enrollment replaced", icon: RefreshCcw },
  remove: { label: "Enrollment removed", icon: Trash2 },
  unlock: { label: "Lock reset", icon: KeyRound },
  bulk_import: { label: "Bulk import started", icon: FileArchive },
};

export function ActivityPanel() {
  const [page, setPage] = useState(0);
  const [event, setEvent] = useState<FaceAuditEvent | "">("");
  const { data, isLoading } = useApi<PageResponse<FaceAuditEntry>>(`/faces/audit?page=${page}&size=15${event ? `&event=${event}` : ""}`, { keepPrevious: true });

  return (
    <Card>
      <CardHeader className="flex-wrap">
        <div className="min-w-0 flex-1">
          <CardTitle>Activity log</CardTitle>
          <CardDescription>Every scan and enrollment change you are allowed to see. Entries cannot be edited or deleted.</CardDescription>
        </div>
        <Select
          items={{ all: "All activity", ...Object.fromEntries((Object.keys(EVENTS) as FaceAuditEvent[]).map((key) => [key, EVENTS[key].label])) }}
          value={event || "all"}
          onValueChange={(value) => {
            setEvent(!value || value === "all" ? "" : (value as FaceAuditEvent));
            setPage(0);
          }}
        >
          <SelectTrigger className="h-10 w-full rounded-xl bg-background/60 px-3.5 sm:w-48" aria-label="Filter by activity">
            <SelectValue />
          </SelectTrigger>
          <SelectContent alignItemWithTrigger={false}>
            <SelectGroup>
              <SelectItem value="all">All activity</SelectItem>
              {(Object.keys(EVENTS) as FaceAuditEvent[]).map((key) => (
                <SelectItem key={key} value={key}>
                  {EVENTS[key].label}
                </SelectItem>
              ))}
            </SelectGroup>
          </SelectContent>
        </Select>
      </CardHeader>
      <CardContent className="px-0">
        {isLoading ? (
          <div className="space-y-2 px-4">
            {[0, 1, 2, 3].map((index) => (
              <Skeleton key={index} className="h-12" />
            ))}
          </div>
        ) : data && data.content.length > 0 ? (
          <>
            <Table>
              <thead className="border-b">
                <tr>
                  <Th>When</Th>
                  <Th>Activity</Th>
                  <Th>By</Th>
                  <Th>Person</Th>
                  <Th>Result</Th>
                </tr>
              </thead>
              <tbody>
                {data.content.map((entry) => {
                  const meta = EVENTS[entry.event];
                  const scan = entry.event === "recognize" || entry.event === "live_recognize";
                  return (
                    <tr key={entry.id} className="border-b last:border-0">
                      <Td className="whitespace-nowrap text-muted-foreground">
                        <Hint label={formatDate(entry.createdAt, true)}>
                          <span>{timeAgo(entry.createdAt)}</span>
                        </Hint>
                      </Td>
                      <Td>
                        <span className="flex items-center gap-2">
                          <meta.icon className="size-4 text-muted-foreground" />
                          {meta.label}
                        </span>
                      </Td>
                      <Td>{entry.actorName ?? <span className="text-muted-foreground">Hidden</span>}</Td>
                      <Td>{entry.subjectName ?? <span className="text-muted-foreground">{scan ? (entry.facesDetected === 0 ? "No face" : "Unknown face") : "—"}</span>}</Td>
                      <Td>
                        {scan ? (
                          <Badge tone={entry.matched ? "success" : "neutral"} dot>
                            {entry.matched ? (entry.confidence !== null ? `${Math.round(entry.confidence * 100)}%` : "Matched") : "No match"}
                          </Badge>
                        ) : entry.event === "bulk_import" ? (
                          <span className="text-xs text-muted-foreground">{entry.facesDetected ?? 0} photos</span>
                        ) : entry.detail ? (
                          <span className="text-xs text-muted-foreground">{entry.detail}</span>
                        ) : null}
                      </Td>
                    </tr>
                  );
                })}
              </tbody>
            </Table>
            <div className="px-4 pb-3">
              <Pagination page={data.number} totalPages={data.totalPages} onChange={setPage} />
            </div>
          </>
        ) : (
          <EmptyState icon={<Activity className="size-7" />} title="No activity yet" description="Scans and enrollment changes will be listed here." />
        )}
      </CardContent>
    </Card>
  );
}
