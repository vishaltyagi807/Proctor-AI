import { useState } from "react";
import { AnimatePresence, motion } from "motion/react";
import { ArrowLeft, Building2, CalendarClock, Download, Eye, EyeOff, Lock, MessageSquare, Paperclip, Pencil, Send, Trash2, UserRound, UserCheck, Users } from "lucide-react";
import { Link, useNavigate } from "@tanstack/react-router";
import { api } from "@/lib/api";
import { useAction, useApi } from "@/lib/query";
import { detailPaths } from "@/lib/paths";
import { uploadComplaintFile } from "@/lib/uploads";
import { formatBytes, formatDate, timeAgo } from "@/lib/format";
import { useSession } from "@/components/providers/session";
import { useToast } from "@/components/ui/toast";
import { downloadFromUrl } from "@/platform/files";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Badge, PriorityBadge, StatusBadge } from "@/components/ui/badge";
import { Chips, Field, Input, Select, Switch, Textarea } from "@/components/ui/form";
import { Avatar, EmptyState, Skeleton, Spinner } from "@/components/ui/misc";
import { Modal } from "@/components/ui/modal";
import { FadeIn, Stagger, StaggerItem } from "@/components/ui/motion";
import { CustomFieldsDisplay, CustomFieldsForm, type CustomValues } from "@/components/custom-fields/custom-fields-form";
import { Dropzone, FileTypeIcon, UploadQueue, type QueuedFile } from "./dropzone";
import { COMPLAINT_PRIORITIES, subjectNames, subjectSummary } from "@/lib/complaints";
import type { Complaint, ComplaintComment, ComplaintFile, ComplaintHistory, ComplaintStatus, PageResponse, UserInfo } from "@/lib/types";
import { Hint } from "@/components/ui/hint";

const STATUS_STEPS: { value: ComplaintStatus; label: string; hint: string }[] = [
  { value: "pending", label: "Pending", hint: "Waiting for review" },
  { value: "reviewed", label: "In review", hint: "Someone is looking into it" },
  { value: "resolved", label: "Resolved", hint: "The issue was fixed" },
  { value: "rejected", label: "Rejected", hint: "Cannot be actioned" },
];

export function ComplaintDetail({ id }: { id: string }) {
  const navigate = useNavigate();
  const paths = detailPaths(id);
  const { user, can, staff } = useSession();
  const toast = useToast();
  const { data: complaint, isLoading, error } = useApi<Complaint>(paths.complaint);
  const history = useApi<ComplaintHistory[]>(paths.history);
  const [editing, setEditing] = useState(false);
  const [confirmDelete, setConfirmDelete] = useState(false);

  const remove = useAction(() => api.delete(paths.complaint), {
    invalidate: ["/complaints", "/notifications"],
    onSuccess: () => {
      toast.success("Complaint deleted");
      navigate({ to: "/complaints" });
    },
    onError: (failure) => toast.error("Could not delete", failure.message),
  });

  if (isLoading) return <DetailSkeleton />;
  if (error || !complaint) {
    return (
      <EmptyState
        icon={<MessageSquare className="size-7" />}
        title="Complaint not found"
        description="It may have been removed, or you may not have access to it."
        action={
          <Link to="/complaints">
            <Button variant="outline">Back to complaints</Button>
          </Link>
        }
      />
    );
  }

  const isReporter = complaint.raisedBy === user.id;
  const about = subjectNames(complaint);
  const canEdit = complaint.status === "pending" ? staff || isReporter : staff;
  const canDelete = can("complaints", "delete") && (staff || (isReporter && complaint.status === "pending"));

  return (
    <div className="space-y-6">
      <FadeIn>
        <Link to="/complaints" className="inline-flex items-center gap-1.5 text-sm text-muted-foreground transition hover:text-foreground">
          <ArrowLeft className="size-4" /> All complaints
        </Link>
        <div className="mt-3 flex flex-wrap items-start justify-between gap-4">
          <div className="min-w-0">
            <div className="flex flex-wrap items-center gap-2.5">
              <StatusBadge status={complaint.status} />
              <PriorityBadge priority={complaint.priority} />
              <Badge>{complaint.category}</Badge>
            </div>
            <h1 className="mt-3 text-2xl font-semibold tracking-tight sm:text-3xl">{complaint.title}</h1>
            <p className="mt-1.5 text-sm text-muted-foreground">
              Filed {timeAgo(complaint.createdAt)}
              {about.length > 0 && <> · about {subjectSummary(complaint, 3)}</>}
            </p>
          </div>
          <div className="flex gap-2">
            {canEdit && (
              <Button variant="outline" className="h-10" onClick={() => setEditing(true)}>
                <Pencil /> Edit
              </Button>
            )}
            {canDelete && (
              <Button variant="destructive" className="h-10" onClick={() => setConfirmDelete(true)}>
                <Trash2 /> Delete
              </Button>
            )}
          </div>
        </div>
      </FadeIn>

      <div className="grid gap-6 xl:grid-cols-[minmax(0,1fr)_380px]">
        <div className="space-y-6">
          <Card>
            <CardHeader>
              <CardTitle>Description</CardTitle>
            </CardHeader>
            <CardContent className="space-y-5">
              <p className="whitespace-pre-wrap text-[15px] leading-7">{complaint.description}</p>
              <CustomFieldsDisplay entity="complaints" values={complaint.customFields} departmentIds={complaint.departmentId ? [complaint.departmentId] : []} />
              {complaint.resolution && (
                <motion.div initial={{ opacity: 0, y: 8 }} animate={{ opacity: 1, y: 0 }} className="rounded-2xl border border-success/30 bg-success/10 p-4">
                  <p className="text-xs font-medium uppercase tracking-wide text-success">Resolution</p>
                  <p className="mt-1 text-sm">{complaint.resolution}</p>
                </motion.div>
              )}
            </CardContent>
          </Card>

          <FilesPanel complaint={complaint} />
          <Discussion complaint={complaint} />
        </div>

        <div className="space-y-6">
          {staff && <WorkflowPanel complaint={complaint} />}
          <ReporterCard complaint={complaint} />
          <Card>
            <CardHeader>
              <CardTitle>Details</CardTitle>
            </CardHeader>
            <CardContent>
              <dl className="space-y-3.5 text-sm">
                <Detail icon={about.length > 1 ? Users : UserRound} label={about.length > 1 ? `Concerns ${about.length} people` : "Concerns"} value={about.join(", ") || null} wrap />
                <Detail icon={UserCheck} label="Assigned to" value={complaint.assignedToName ?? "Unassigned"} />
                <Detail icon={Building2} label="Department" value={complaint.departmentName ?? "—"} />
                <Detail icon={CalendarClock} label="Created" value={formatDate(complaint.createdAt, true)} />
                {complaint.resolvedAt && <Detail icon={CalendarClock} label="Closed" value={formatDate(complaint.resolvedAt, true)} />}
              </dl>
            </CardContent>
          </Card>
          <Timeline items={history.data} loading={history.isLoading} />
        </div>
      </div>

      <EditModal complaint={complaint} open={editing} onClose={() => setEditing(false)} />
      <Modal
        open={confirmDelete}
        onClose={() => setConfirmDelete(false)}
        title="Delete this complaint?"
        description="Its comments, history and files are removed permanently."
        size="sm"
        footer={
          <>
            <Button variant="outline" onClick={() => setConfirmDelete(false)}>
              Keep it
            </Button>
            <Button variant="destructive" disabled={remove.isPending} onClick={() => remove.mutate(undefined)}>
              {remove.isPending && <Spinner />} Delete
            </Button>
          </>
        }
      >
        <p className="text-sm text-muted-foreground">This action cannot be undone.</p>
      </Modal>
    </div>
  );
}

function Detail({ icon: Icon, label, value, wrap }: { icon: React.ComponentType<{ className?: string }>; label: string; value: string | null; wrap?: boolean }) {
  return (
    <div className="flex items-center gap-3">
      <span className="grid size-8 place-items-center rounded-lg bg-muted text-muted-foreground">
        <Icon className="size-4" />
      </span>
      <div className="min-w-0">
        <dt className="text-xs text-muted-foreground">{label}</dt>
        <dd className={wrap ? "font-medium break-words" : "truncate font-medium"}>{value ?? "—"}</dd>
      </div>
    </div>
  );
}

function DetailSkeleton() {
  return (
    <div className="space-y-6">
      <Skeleton className="h-24" />
      <div className="grid gap-6 xl:grid-cols-[minmax(0,1fr)_380px]">
        <Skeleton className="h-96" />
        <Skeleton className="h-96" />
      </div>
    </div>
  );
}

function WorkflowPanel({ complaint }: { complaint: Complaint }) {
  const toast = useToast();
  const paths = detailPaths(complaint.id);
  const [status, setStatus] = useState<ComplaintStatus>(complaint.status);
  const [note, setNote] = useState("");
  const [resolution, setResolution] = useState(complaint.resolution ?? "");
  const [assignee, setAssignee] = useState(complaint.assignedTo ?? "");
  const people = useApi<PageResponse<UserInfo>>("/users?size=100&sortBy=name&direction=asc");

  const changeStatus = useAction(
    () => api.put(`${paths.complaint}/status`, { status, note: note || undefined, resolution: resolution || undefined }),
    { invalidate: [paths.complaint, "/complaints", "/notifications"], onSuccess: () => { setNote(""); toast.success("Status updated", `Marked as ${status}.`); }, onError: (e) => toast.error("Could not update status", e.message) },
  );
  const assign = useAction((to: string) => api.put(`${paths.complaint}/assignee`, { assignedTo: to }), {
    invalidate: [paths.complaint, "/complaints", "/notifications"],
    onSuccess: () => toast.success("Assignee updated"),
    onError: (e) => toast.error("Could not assign", e.message),
  });

  const closing = status === "resolved" || status === "rejected";
  return (
    <Card className="overflow-hidden border-primary/25">
      <div className="h-1 gradient-brand-animated" />
      <CardHeader>
        <div>
          <CardTitle>Workflow</CardTitle>
          <CardDescription>Move this complaint forward</CardDescription>
        </div>
      </CardHeader>
      <CardContent className="space-y-5">
        <div className="grid grid-cols-2 gap-2">
          {STATUS_STEPS.map((step) => (
            <motion.button
              key={step.value}
              type="button"
              whileTap={{ scale: 0.97 }}
              onClick={() => setStatus(step.value)}
              className={`rounded-xl border p-3 text-left transition ${status === step.value ? "border-primary bg-primary/10" : "hover:border-primary/40"}`}
            >
              <span className="block text-sm font-medium">{step.label}</span>
              <span className="block text-[11px] text-muted-foreground">{step.hint}</span>
            </motion.button>
          ))}
        </div>
        <AnimatePresence initial={false}>
          {closing && (
            <motion.div initial={{ opacity: 0, height: 0 }} animate={{ opacity: 1, height: "auto" }} exit={{ opacity: 0, height: 0 }} className="overflow-hidden">
              <Field label="Resolution message" hint="Shown to the student on the complaint.">
                <Textarea rows={3} value={resolution} onChange={(event) => setResolution(event.target.value)} />
              </Field>
            </motion.div>
          )}
        </AnimatePresence>
        <Field label="Note for the history">
          <Input value={note} onChange={(event) => setNote(event.target.value)} placeholder="Optional" />
        </Field>
        <Button className="h-10 w-full gradient-brand text-white" disabled={changeStatus.isPending || status === complaint.status} onClick={() => changeStatus.mutate(undefined)}>
          {changeStatus.isPending && <Spinner />} Update status
        </Button>

        <div className="border-t pt-5">
          <Field label="Assign to">
            <div className="flex gap-2">
              <Select
                value={assignee}
                onValueChange={setAssignee}
                placeholder="Choose a person…"
                aria-label="Assign to"
                options={(people.data?.content ?? []).map((person) => ({ value: person.id, label: `${person.name} (${person.roles.map((role) => role.name).join(", ") || "no role"})` }))}
              />
              <Button variant="outline" className="h-10" disabled={!assignee || assignee === complaint.assignedTo || assign.isPending} onClick={() => assign.mutate(assignee)}>
                {assign.isPending ? <Spinner /> : "Assign"}
              </Button>
            </div>
          </Field>
        </div>
      </CardContent>
    </Card>
  );
}

function ReporterCard({ complaint }: { complaint: Complaint }) {
  const { can } = useSession();
  const toast = useToast();
  const canToggle = can("complaint_reporter", "update", ["department", "all"]);
  const toggle = useAction((value: boolean) => api.patch(`/complaints/${complaint.id}`, { revealReporter: value }), {
    invalidate: [`/complaints/${complaint.id}`],
    onSuccess: () => toast.success("Reporter visibility updated"),
    onError: (e) => toast.error("Could not update", e.message),
  });
  return (
    <Card>
      <CardHeader>
        <CardTitle>Reported by</CardTitle>
        {complaint.reporterVisible ? <Eye className="size-4 text-muted-foreground" /> : <EyeOff className="size-4 text-muted-foreground" />}
      </CardHeader>
      <CardContent className="space-y-4">
        {complaint.reporterVisible && complaint.raisedByName ? (
          <div className="flex items-center gap-3">
            <Avatar name={complaint.raisedByName} size={42} />
            <div>
              <p className="font-medium">{complaint.raisedByName}</p>
              <p className="text-xs text-muted-foreground">{complaint.raisedByDepartments?.join(", ") || "No department"}</p>
            </div>
          </div>
        ) : (
          <div className="flex items-center gap-3 rounded-xl bg-muted/60 p-3.5">
            <span className="grid size-10 place-items-center rounded-full bg-muted text-muted-foreground">
              <Lock className="size-4" />
            </span>
            <p className="text-sm text-muted-foreground">The reporter&apos;s identity is hidden for your role.</p>
          </div>
        )}
        {canToggle && (
          <label className="flex items-center justify-between gap-3 rounded-xl border p-3.5 text-sm">
            <span>
              <span className="block font-medium">Reveal to the student</span>
              <span className="block text-xs text-muted-foreground">Let the person concerned see who reported this.</span>
            </span>
            <Switch checked={complaint.revealReporter} onChange={(value) => toggle.mutate(value)} disabled={toggle.isPending} label="Reveal reporter" />
          </label>
        )}
      </CardContent>
    </Card>
  );
}

function Timeline({ items, loading }: { items: ComplaintHistory[] | undefined; loading: boolean }) {
  return (
    <Card>
      <CardHeader>
        <CardTitle>Timeline</CardTitle>
      </CardHeader>
      <CardContent>
        {loading ? (
          <Skeleton className="h-32" />
        ) : (
          <Stagger className="relative space-y-5 pl-6 before:absolute before:bottom-1 before:left-[7px] before:top-1 before:w-px before:bg-border">
            {[...(items ?? [])].reverse().map((entry, index) => (
              <StaggerItem key={entry.id} className="relative">
                <span className={`absolute -left-6 top-1.5 size-3.5 rounded-full border-2 border-card ${index === 0 ? "gradient-brand ring-4 ring-primary/15" : "bg-muted-foreground/40"}`} />
                <div className="flex flex-wrap items-center gap-2">
                  {entry.fromStatus && (
                    <>
                      <StatusBadge status={entry.fromStatus} />
                      <span className="text-muted-foreground">→</span>
                    </>
                  )}
                  <StatusBadge status={entry.toStatus} />
                </div>
                {entry.note && <p className="mt-1.5 text-sm">{entry.note}</p>}
                <p className="mt-1 text-xs text-muted-foreground">
                  {entry.actorName ?? "Someone"} · {timeAgo(entry.createdAt)}
                </p>
              </StaggerItem>
            ))}
          </Stagger>
        )}
      </CardContent>
    </Card>
  );
}

function Discussion({ complaint }: { complaint: Complaint }) {
  const { staff, user, can } = useSession();
  const toast = useToast();
  const paths = detailPaths(complaint.id);
  const { data, isLoading } = useApi<ComplaintComment[]>(paths.comments);
  const [body, setBody] = useState("");
  const [internal, setInternal] = useState(false);

  const post = useAction(() => api.post(paths.comments, { body, internal }), {
    invalidate: [paths.comments, "/notifications"],
    onSuccess: () => { setBody(""); },
    onError: (e) => toast.error("Could not post comment", e.message),
  });
  const remove = useAction((commentId: string) => api.delete(`${paths.comments}/${commentId}`), {
    invalidate: [paths.comments],
    onError: (e) => toast.error("Could not delete comment", e.message),
  });

  return (
    <Card>
      <CardHeader>
        <div>
          <CardTitle>Discussion</CardTitle>
          <CardDescription>{data ? `${data.length} comment${data.length === 1 ? "" : "s"}` : "Loading"}</CardDescription>
        </div>
      </CardHeader>
      <CardContent className="space-y-5">
        {isLoading ? (
          <Skeleton className="h-24" />
        ) : data && data.length > 0 ? (
          <ul className="space-y-4">
            <AnimatePresence initial={false}>
              {data.map((comment) => (
                <motion.li key={comment.id} layout initial={{ opacity: 0, y: 12 }} animate={{ opacity: 1, y: 0 }} exit={{ opacity: 0, height: 0 }} className="flex gap-3">
                  <Avatar name={comment.authorName} size={36} />
                  <div className={`min-w-0 flex-1 rounded-2xl rounded-tl-md p-3.5 ${comment.internal ? "border border-warning/40 bg-warning/10" : "bg-muted/60"}`}>
                    <div className="flex flex-wrap items-center gap-2 text-xs text-muted-foreground">
                      <span className="text-sm font-medium text-foreground">{comment.authorName ?? "Someone"}</span>
                      {comment.internal && (
                        <Badge tone="warning">
                          <Lock className="size-3" /> Staff only
                        </Badge>
                      )}
                      <span>{timeAgo(comment.createdAt)}</span>
                      {(comment.authorId === user.id || can("complaint_comments", "delete", ["all"])) && (
                        <Hint label="Delete comment">
                          <button onClick={() => remove.mutate(comment.id)} className="ml-auto rounded p-1 hover:bg-background/60 hover:text-destructive" aria-label="Delete comment">
                            <Trash2 className="size-3.5" />
                          </button>
                        </Hint>
                      )}
                    </div>
                    <p className="mt-1.5 whitespace-pre-wrap text-sm leading-6">{comment.body}</p>
                  </div>
                </motion.li>
              ))}
            </AnimatePresence>
          </ul>
        ) : (
          <p className="py-4 text-center text-sm text-muted-foreground">No comments yet. Start the conversation.</p>
        )}

        {can("complaint_comments", "write") && (
          <form
            onSubmit={(event) => {
              event.preventDefault();
              if (body.trim()) post.mutate(undefined);
            }}
            className="space-y-3 border-t pt-5"
          >
            <Textarea rows={3} value={body} onChange={(event) => setBody(event.target.value)} placeholder="Write a comment…" maxLength={4000} />
            <div className="flex flex-wrap items-center justify-between gap-3">
              {staff ? (
                <label className="flex items-center gap-2.5 text-sm">
                  <Switch checked={internal} onChange={setInternal} label="Staff only" />
                  <span className="text-muted-foreground">Staff-only note</span>
                </label>
              ) : (
                <span />
              )}
              <Button type="submit" disabled={!body.trim() || post.isPending} className="h-10 gradient-brand text-white">
                {post.isPending ? <Spinner /> : <Send />} Post
              </Button>
            </div>
          </form>
        )}
      </CardContent>
    </Card>
  );
}

function FilesPanel({ complaint }: { complaint: Complaint }) {
  const { can } = useSession();
  const toast = useToast();
  const paths = detailPaths(complaint.id);
  const { data, isLoading } = useApi<ComplaintFile[]>(paths.files);
  const [queue, setQueue] = useState<QueuedFile[]>([]);
  const [preview, setPreview] = useState<ComplaintFile | null>(null);
  const invalidateFiles = useAction(async () => undefined, { invalidate: [paths.files] });

  const patch = (id: string, change: Partial<QueuedFile>) => setQueue((current) => current.map((item) => (item.id === id ? { ...item, ...change } : item)));

  async function upload(files: File[]) {
    const items = files.map((file) => ({ id: `${file.name}-${file.size}-${Math.random()}`, file, progress: 0, state: "uploading" as const }));
    setQueue((current) => [...current, ...items]);
    for (const item of items) {
      try {
        await uploadComplaintFile(complaint.id, item.file, (progress) => patch(item.id, { progress }));
        patch(item.id, { state: "done", progress: 100 });
        invalidateFiles.mutate(undefined);
      } catch (failure) {
        patch(item.id, { state: "error", error: (failure as Error).message });
        toast.error(`Could not upload ${item.file.name}`, (failure as Error).message);
      }
    }
    window.setTimeout(() => setQueue((current) => current.filter((item) => item.state === "error")), 2500);
  }

  const remove = useAction((fileId: string) => api.delete(`${paths.files}/${fileId}`), {
    invalidate: [paths.files],
    onError: (e) => toast.error("Could not delete file", e.message),
  });

  return (
    <Card>
      <CardHeader>
        <div>
          <CardTitle>Files &amp; evidence</CardTitle>
          <CardDescription>{data ? `${data.length} file${data.length === 1 ? "" : "s"}` : "Loading"}</CardDescription>
        </div>
        <Paperclip className="size-4 text-muted-foreground" />
      </CardHeader>
      <CardContent className="space-y-4">
        {isLoading ? (
          <Skeleton className="h-24" />
        ) : data && data.length > 0 ? (
          <Stagger className="grid gap-3 sm:grid-cols-2">
            {data.map((file) => (
              <StaggerItem key={file.id}>
                <FileCard complaintId={complaint.id} file={file} onPreview={() => setPreview(file)} onDelete={() => remove.mutate(file.id)} />
              </StaggerItem>
            ))}
          </Stagger>
        ) : (
          <p className="py-2 text-center text-sm text-muted-foreground">No files attached yet.</p>
        )}
        {queue.length > 0 && <UploadQueue items={queue} />}
        {can("complaint_files", "write") && <Dropzone onFiles={upload} />}
      </CardContent>
      <PreviewModal complaintId={complaint.id} file={preview} onClose={() => setPreview(null)} />
    </Card>
  );
}

function FileCard({ complaintId, file, onPreview, onDelete }: { complaintId: string; file: ComplaintFile; onPreview: () => void; onDelete: () => void }) {
  const isImage = file.contentType.startsWith("image/");
  const media = isImage || file.contentType.startsWith("video/") || file.contentType.startsWith("audio/");
  const thumbnail = useApi<{ url: string }>(isImage && file.status === "uploaded" ? `/complaints/${complaintId}/files/${file.id}/download-url?inline=true` : null);
  const toast = useToast();

  async function download() {
    try {
      const result = await api.get<{ url: string }>(`/complaints/${complaintId}/files/${file.id}/download-url`);
      const saved = await downloadFromUrl(result.url, file.fileName);
      if (saved) toast.success("Download complete", saved);
    } catch (cause) {
      toast.error("Download failed", cause instanceof Error ? cause.message : undefined);
    }
  }

  return (
    <motion.div whileHover={{ y: -3 }} className="group overflow-hidden rounded-2xl border bg-card">
      <button onClick={media ? onPreview : download} className="relative block h-32 w-full overflow-hidden bg-muted">
        {isImage && thumbnail.data ? (
          <div role="img" aria-label={file.fileName} style={{ backgroundImage: `url(${thumbnail.data.url})` }} className="size-full bg-cover bg-center transition duration-500 group-hover:scale-105" />
        ) : (
          <span className="grid size-full place-items-center text-muted-foreground">
            <FileTypeIcon type={file.contentType} className="size-9" />
          </span>
        )}
        {file.kind === "evidence" && <Badge tone="violet" className="absolute left-2 top-2">Evidence</Badge>}
      </button>
      <div className="flex items-center gap-2 p-3">
        <div className="min-w-0 flex-1">
          <p className="truncate text-sm font-medium">{file.fileName}</p>
          <p className="truncate text-xs text-muted-foreground">
            {formatBytes(file.sizeBytes)} · {file.uploadedByName ?? "Someone"}
          </p>
        </div>
        <Hint label="Download">
          <button onClick={download} className="rounded-lg p-1.5 text-muted-foreground hover:bg-muted hover:text-foreground" aria-label="Download">
            <Download className="size-4" />
          </button>
        </Hint>
        <Hint label="Delete file">
          <button onClick={onDelete} className="rounded-lg p-1.5 text-muted-foreground hover:bg-destructive/10 hover:text-destructive" aria-label="Delete file">
            <Trash2 className="size-4" />
          </button>
        </Hint>
      </div>
    </motion.div>
  );
}

function PreviewModal({ complaintId, file, onClose }: { complaintId: string; file: ComplaintFile | null; onClose: () => void }) {
  const link = useApi<{ url: string }>(file ? `/complaints/${complaintId}/files/${file.id}/download-url?inline=true` : null);
  return (
    <Modal open={file !== null} onClose={onClose} title={file?.fileName ?? "Preview"} size="lg">
      {!link.data ? (
        <Skeleton className="h-72" />
      ) : file?.contentType.startsWith("video/") ? (
        <video src={link.data.url} controls className="max-h-[60vh] w-full rounded-xl bg-black" />
      ) : file?.contentType.startsWith("audio/") ? (
        <audio src={link.data.url} controls className="w-full" />
      ) : (
        <div role="img" aria-label={file?.fileName} style={{ backgroundImage: `url(${link.data.url})` }} className="mx-auto h-[65vh] w-full rounded-xl bg-contain bg-center bg-no-repeat" />
      )}
    </Modal>
  );
}

function EditModal({ complaint, open, onClose }: { complaint: Complaint; open: boolean; onClose: () => void }) {
  const toast = useToast();
  const [title, setTitle] = useState(complaint.title);
  const [description, setDescription] = useState(complaint.description);
  const [category, setCategory] = useState(complaint.category);
  const [priority, setPriority] = useState<string>(complaint.priority);
  const [custom, setCustom] = useState<CustomValues>(complaint.customFields ?? {});
  const save = useAction(() => api.patch(`/complaints/${complaint.id}`, { title, description, category, priority, customFields: custom }), {
    invalidate: [`/complaints/${complaint.id}`, "/complaints"],
    onSuccess: () => {
      toast.success("Complaint updated");
      onClose();
    },
    onError: (e) => toast.error("Could not save", e.message),
  });
  return (
    <Modal
      open={open}
      onClose={onClose}
      title="Edit complaint"
      size="lg"
      footer={
        <>
          <Button variant="outline" onClick={onClose}>
            Cancel
          </Button>
          <Button className="gradient-brand text-white" disabled={save.isPending} onClick={() => save.mutate(undefined)}>
            {save.isPending && <Spinner />} Save changes
          </Button>
        </>
      }
    >
      <div className="space-y-4">
        <Field label="Title">
          <Input value={title} onChange={(event) => setTitle(event.target.value)} />
        </Field>
        <Field label="Description">
          <Textarea rows={5} value={description} onChange={(event) => setDescription(event.target.value)} />
        </Field>
        <Field label="Category">
          <Input value={category} onChange={(event) => setCategory(event.target.value)} />
        </Field>
        <Field label="Priority">
          <Chips multiple={false} options={COMPLAINT_PRIORITIES.map((item) => ({ value: item, label: item }))} value={[priority]} onChange={(value) => setPriority(value[0] ?? priority)} />
        </Field>
        <CustomFieldsForm entity="complaints" departmentIds={complaint.departmentId ? [complaint.departmentId] : []} value={custom} onChange={setCustom} />
      </div>
    </Modal>
  );
}
