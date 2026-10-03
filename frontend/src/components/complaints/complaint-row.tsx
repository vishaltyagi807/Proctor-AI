import { Link } from "@tanstack/react-router";
import { ChevronRight, Paperclip } from "lucide-react";
import { Avatar } from "@/components/ui/misc";
import { PriorityBadge, StatusBadge } from "@/components/ui/badge";
import { timeAgo } from "@/lib/format";
import { subjectSummary } from "@/lib/complaints";
import type { Complaint } from "@/lib/types";

export function ComplaintRow({ complaint, showStudent = true }: { complaint: Complaint; showStudent?: boolean }) {
  const about = subjectSummary(complaint);
  return (
    <Link to="/complaints/$id" params={{ id: complaint.id }} className="group flex items-center gap-4 rounded-2xl px-4 py-3.5 transition hover:bg-muted/60">
      <Avatar name={showStudent ? complaint.studentName : complaint.title} size={40} />
      <div className="min-w-0 flex-1">
        <div className="flex flex-wrap items-center gap-2">
          <p className="truncate font-medium">{complaint.title}</p>
          <PriorityBadge priority={complaint.priority} />
        </div>
        <p className="mt-0.5 flex flex-wrap items-center gap-x-2 text-xs text-muted-foreground">
          {showStudent && about && <span>{about}</span>}
          {complaint.departmentName && <span>· {complaint.departmentName}</span>}
          <span>· {complaint.category}</span>
          <span>· {timeAgo(complaint.createdAt)}</span>
        </p>
      </div>
      <StatusBadge status={complaint.status} />
      <ChevronRight className="size-4 text-muted-foreground transition group-hover:translate-x-0.5 group-hover:text-foreground" />
    </Link>
  );
}

export function AttachmentHint() {
  return <Paperclip className="size-3.5" />;
}
