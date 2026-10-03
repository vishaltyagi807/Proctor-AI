import { useState } from "react";
import { useNavigate, useSearch } from "@tanstack/react-router";
import { motion } from "motion/react";
import { FileArchive, KeyRound, Lock, ScanFace, Trash2, UserPlus, Users2 } from "lucide-react";
import { api, apiUrl, ApiError } from "@/lib/api";
import { useAction, useApi, useInvalidate } from "@/lib/query";
import { ADMIN_PATHS, FACES_PATH } from "@/lib/paths";
import { formatDate } from "@/lib/format";
import { useSession } from "@/components/providers/session";
import { useProcesses } from "@/components/providers/processes";
import { useToast } from "@/components/ui/toast";
import { Avatar, EmptyState, PageHeader, Progress, Skeleton, Spinner, Table, Tabs, Td, Th } from "@/components/ui/misc";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Field, Input } from "@/components/ui/form";
import {
  Combobox,
  ComboboxCollection,
  ComboboxContent,
  ComboboxEmpty,
  ComboboxGroup,
  ComboboxInput,
  ComboboxItem,
  ComboboxLabel,
  ComboboxList,
} from "@/components/ui/combobox";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { Dropzone } from "@/components/complaints/dropzone";
import { TemplateDownloadMenu } from "@/components/ui/download-menu";
import { ImagePicker } from "@/components/faces/image-picker";
import { FaceResult } from "./face-result";
import { LivePanel } from "./live-panel";
import { ActivityPanel } from "./activity-panel";
import type { FaceTab } from "@/lib/faces";
import type { FaceEnrollment, FaceRecognizeResult, PageResponse, UserInfo } from "@/lib/types";
import { Hint } from "@/components/ui/hint";


function groupByDepartment(people: UserInfo[]): { department: string; people: UserInfo[] }[] {
  const groups = new Map<string, UserInfo[]>();
  for (const person of people) {
    const names = person.departments?.length ? person.departments.map((department) => department.name) : ["No department"];
    for (const name of names) {
      if (!groups.has(name)) groups.set(name, []);
      groups.get(name)!.push(person);
    }
  }
  return [...groups.entries()].sort((a, b) => a[0].localeCompare(b[0])).map(([department, members]) => ({ department, people: members }));
}

export function FacesView() {
  const { can } = useSession();
  const tabs = [
    ...(can("face_recognition", "read") ? [{ value: "recognize" as const, label: "Photo" }] : []),
    ...(can("face_live_recognition", "read") ? [{ value: "live" as const, label: "Live camera" }] : []),
    ...(can("face_enrollments", "write") || can("face_enrollments", "update") || can("face_import", "write") ? [{ value: "enroll" as const, label: "Enroll" }] : []),
    ...(can("face_enrollments", "read") ? [{ value: "enrolled" as const, label: "Enrolled people" }] : []),
    ...(can("face_audit", "read") ? [{ value: "activity" as const, label: "Activity" }] : []),
  ];
  const search = useSearch({ from: "/app/faces" });
  const navigate = useNavigate({ from: "/faces" });
  const tab: FaceTab = tabs.find((item) => item.value === search.tab)?.value ?? tabs[0]?.value ?? "enrolled";
  const setTab = (next: FaceTab) => void navigate({ search: { tab: next }, replace: true });
  return (
    <div>
      <PageHeader title="Face recognition" description="Identify people from photos or the live camera, and manage who is enrolled." icon={<ScanFace className="size-5" />} />
      <div className="mb-6">
        <Tabs id="faces" tabs={tabs} value={tab} onChange={setTab} />
      </div>
      <motion.div key={tab} initial={{ opacity: 0, y: 10 }} animate={{ opacity: 1, y: 0 }} transition={{ duration: 0.3 }}>
        {tab === "recognize" && <RecognizePanel />}
        {tab === "live" && <LivePanel />}
        {tab === "enroll" && <EnrollPanel />}
        {tab === "enrolled" && <EnrolledPanel />}
        {tab === "activity" && <ActivityPanel />}
      </motion.div>
    </div>
  );
}

type PersonChoice = { id: string; name: string; email: string; label: string };
type PersonGroup = { value: string; items: PersonChoice[] };

function RecognizePanel() {
  const toast = useToast();
  const { can } = useSession();
  const [image, setImage] = useState<string | null>(null);
  const [result, setResult] = useState<FaceRecognizeResult | null>(null);

  const recognize = useAction(() => api.post<FaceRecognizeResult>("/faces/recognize", { image, source: "upload" }), {
    onSuccess: (data) => setResult(data),
    onError: (error) => toast.error("Could not recognize", error.message),
  });

  return (
    <div className="grid gap-6 xl:grid-cols-[400px_minmax(0,1fr)] [&>*]:min-w-0">
      <Card className="self-start">
        <CardHeader>
          <div>
            <CardTitle>Submit a photo</CardTitle>
            <CardDescription>
              {can("face_recognition", "read", ["all"])
                ? "The image is checked against everyone who is enrolled."
                : "Only people in your departments are identified. Anyone else is shown as unknown."}
            </CardDescription>
          </div>
        </CardHeader>
        <CardContent className="space-y-5">
          <ImagePicker value={image} onChange={setImage} disabled={recognize.isPending} />
          <Button
            className="h-11 w-full gradient-brand text-white shadow-lg shadow-primary/25"
            disabled={!image || recognize.isPending}
            onClick={() => recognize.mutate(undefined)}
          >
            {recognize.isPending ? <Spinner /> : <ScanFace />} Recognize faces
          </Button>
        </CardContent>
      </Card>

      <Card className="self-start">
        <CardHeader>
          <div>
            <CardTitle>Result</CardTitle>
            <CardDescription>Each box is numbered to match the list. Point at a face or a row to link them.</CardDescription>
          </div>
        </CardHeader>
        <CardContent>
          {recognize.isPending ? (
            <Skeleton className="h-64" />
          ) : result ? (
            <FaceResult result={result} emptyText="No faces were detected in this photo." className="2xl:grid-cols-[minmax(0,3fr)_minmax(0,2fr)]" />
          ) : (
            <p className="text-sm text-muted-foreground">Submit a photo to see who&apos;s in it.</p>
          )}
        </CardContent>
      </Card>
    </div>
  );
}

function EnrollPanel() {
  const toast = useToast();
  const { can, manage, user } = useSession();
  const scopeAll = can("face_enrollments", "write", ["all"]) || can("face_enrollments", "update", ["all"]);
  const scopeDept = can("face_enrollments", "write", ["department"]) || can("face_enrollments", "update", ["department"]);
  const canPickOthers = (scopeAll || scopeDept) && can("users", "read", ["department", "all"]);
  const canSeeEnrollments = can("face_enrollments", "read");
  const canBulk = can("face_import", "write");
  const canSelf = can("face_enrollments", "write") || can("face_enrollments", "update");

  const people = useApi<PageResponse<UserInfo>>(canPickOthers ? ADMIN_PATHS.people : null);
  const enrollments = useApi<FaceEnrollment[]>(canSeeEnrollments && !canPickOthers ? FACES_PATH : null);
  const manageable = (people.data?.content ?? []).filter((person) => person.id !== user.id && manage.user(person, "write"));
  const hiddenCount = (people.data?.content.length ?? 0) - manageable.length - (people.data?.content.some((person) => person.id === user.id) ? 1 : 0);
  const [targetId, setTargetId] = useState(canSelf ? user.id : "");
  const [image, setImage] = useState<string | null>(null);

  const enroll = useAction(() => api.post<FaceEnrollment>(`/faces/enroll/${targetId}`, { image }), {
    invalidate: [FACES_PATH],
    onSuccess: (enrollment) => {
      toast.success("Face enrolled", `${enrollment.name} can now be recognized.`);
      setImage(null);
    },
    onError: (error) => toast.error("Could not enroll", error.message),
  });

  const toChoice = (person: { id: string; name: string; email: string }, you = false): PersonChoice => ({
    id: person.id,
    name: person.name,
    email: person.email,
    label: you ? `${person.name} (you)` : person.name,
  });
  const personGroups: PersonGroup[] = [
    ...(canSelf ? [{ value: "You", items: [toChoice(user, true)] }] : []),
    ...(scopeAll
      ? groupByDepartment(manageable).map((group) => ({ value: group.department, items: group.people.map((person) => toChoice(person)) }))
      : manageable.length > 0
        ? [{ value: "People you can enroll", items: manageable.map((person) => toChoice(person)) }]
        : []),
  ];
  const selectedPerson = personGroups.flatMap((group) => group.items).find((person) => person.id === targetId) ?? null;
  const targetName = targetId === user.id ? `${user.name} (you)` : (people.data?.content.find((person) => person.id === targetId)?.name ?? "");
  const scopeHint = scopeAll
    ? "You can enroll anyone ranked below you, organized by department."
    : scopeDept
      ? "You can enroll yourself or anyone ranked below you in your own departments."
      : "You can only enroll yourself, and only a limited number of times before an administrator needs to refresh it for you.";
  const selfLock = !canPickOthers ? enrollments.data?.find((entry) => entry.userId === user.id) : undefined;
  const selfLocked = Boolean(selfLock?.selfEnrollLocked);

  return (
    <div className="grid gap-6 xl:grid-cols-[minmax(0,1fr)_360px] [&>*]:min-w-0">
      <Card>
        <CardHeader>
          <div>
            <CardTitle>Enroll a face</CardTitle>
            <CardDescription>Use a clear, well-lit, forward-facing photo for the best accuracy.</CardDescription>
          </div>
        </CardHeader>
        <CardContent className="space-y-5">
          {selfLocked && (
            <Alert variant="destructive">
              <Lock />
              <AlertTitle>Self-enrollment locked</AlertTitle>
              <AlertDescription>
                You&apos;ve used your self-enrollment attempt{selfLock && selfLock.selfEnrollCount > 1 ? "s" : ""}. Ask someone with department or
                organization-wide face-recognition access to update your face.
              </AlertDescription>
            </Alert>
          )}
          {canPickOthers ? (
            <Field label="Person">
              {people.isLoading ? (
                <Skeleton className="h-10" />
              ) : (
                <Combobox
                  items={personGroups}
                  value={selectedPerson}
                  onValueChange={(person: PersonChoice | null) => setTargetId(person?.id ?? "")}
                  itemToStringLabel={(person: PersonChoice) => person.label}
                  filter={(person: PersonChoice, query: string) => `${person.label} ${person.email}`.toLowerCase().includes(query.trim().toLowerCase())}
                  isItemEqualToValue={(item: PersonChoice, value: PersonChoice) => item.id === value.id}
                  disabled={enroll.isPending}
                >
                  <ComboboxInput placeholder="Search by name or email…" className="h-10 w-full rounded-xl" showClear={Boolean(selectedPerson)} aria-label="Person" />
                  <ComboboxContent>
                    <ComboboxEmpty>No one matches that search.</ComboboxEmpty>
                    <ComboboxList>
                      {(group: PersonGroup) => (
                        <ComboboxGroup key={group.value} items={group.items}>
                          <ComboboxLabel>{group.value}</ComboboxLabel>
                          <ComboboxCollection>
                            {(person: PersonChoice) => (
                              <ComboboxItem key={`${group.value}-${person.id}`} value={person}>
                                <Avatar name={person.name} size={26} />
                                <span className="flex min-w-0 flex-col">
                                  <span className="truncate">{person.label}</span>
                                  <span className="truncate text-xs text-muted-foreground">{person.email}</span>
                                </span>
                              </ComboboxItem>
                            )}
                          </ComboboxCollection>
                        </ComboboxGroup>
                      )}
                    </ComboboxList>
                  </ComboboxContent>
                </Combobox>
              )}
            </Field>
          ) : (
            <div className="flex items-center gap-3 rounded-xl border bg-muted/30 p-3">
              <Avatar name={user.name} size={36} />
              <div className="min-w-0">
                <p className="truncate text-sm font-medium">{user.name}</p>
                <p className="truncate text-xs text-muted-foreground">{user.email}</p>
              </div>
            </div>
          )}
          <ImagePicker value={image} onChange={setImage} disabled={enroll.isPending || selfLocked} />
          <Button
            className="h-11 w-full gradient-brand text-white shadow-lg shadow-primary/25"
            disabled={!image || !targetId || enroll.isPending || selfLocked}
            onClick={() => enroll.mutate(undefined)}
          >
            {enroll.isPending ? <Spinner /> : <UserPlus />} <span className="truncate">Enroll {targetName || "face"}</span>
          </Button>
        </CardContent>
      </Card>

      <Card className="self-start">
        <CardHeader>
          <CardTitle>Who can be enrolled</CardTitle>
        </CardHeader>
        <CardContent className="space-y-4">
          <p className="text-sm text-muted-foreground">{scopeHint}</p>
          {canPickOthers && hiddenCount > 0 && (
            <p className="flex items-start gap-2 rounded-xl bg-muted/60 p-3 text-xs text-muted-foreground">
              <Lock className="mt-0.5 size-3.5 shrink-0" />
              {hiddenCount} {hiddenCount === 1 ? "person is" : "people are"} ranked at or above your level and can&apos;t be enrolled by you.
            </p>
          )}
          {canBulk && <BulkZipImport />}
        </CardContent>
      </Card>
    </div>
  );
}

function BulkZipImport() {
  const toast = useToast();
  const { can } = useSession();
  const { processes } = useProcesses();
  const [file, setFile] = useState<File | null>(null);
  const [processId, setProcessId] = useState<string | null>(null);
  const [starting, setStarting] = useState(false);

  const process = processId ? processes.find((p) => p.processId === processId) : undefined;
  const percent = process && process.total > 0 ? Math.round((process.processed / process.total) * 100) : 0;

  const start = async () => {
    if (!file) return;
    setStarting(true);
    try {
      const form = new FormData();
      form.append("file", file);
      const result = await api.post<{ processId: string; total: number }>("/faces/enroll/bulk", form);
      setProcessId(result.processId);
      toast.success("Bulk import started", `Processing ${result.total} photos - watch the background processes panel.`);
      setFile(null);
    } catch (error) {
      toast.error("Could not start bulk import", error instanceof ApiError ? error.message : "Unknown error");
    } finally {
      setStarting(false);
    }
  };

  return (
    <div className="space-y-3 border-t pt-4">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div className="min-w-0">
          <p className="text-sm font-medium">Bulk import from ZIP</p>
          <p className="text-xs text-muted-foreground">
            One photo per person, named like <code className="break-all rounded bg-muted px-1">student0001@college.com.jpg</code>.
          </p>
        </div>
        {can("face_import", "read") && <TemplateDownloadMenu href={apiUrl("/faces/enroll/bulk/template")} label="Template" />}
      </div>
      {file ? (
        <div className="flex items-center gap-3 rounded-xl border p-3">
          <span className="grid size-9 place-items-center rounded-lg bg-primary/10 text-primary">
            <FileArchive className="size-4" />
          </span>
          <div className="min-w-0 flex-1">
            <p className="truncate text-sm font-medium">{file.name}</p>
            <p className="text-xs text-muted-foreground">{(file.size / 1024 / 1024).toFixed(1)} MB</p>
          </div>
          <Button variant="ghost" size="sm" onClick={() => setFile(null)}>
            Change
          </Button>
        </div>
      ) : (
        <Dropzone onFiles={(files) => setFile(files.find((f) => f.name.toLowerCase().endsWith(".zip")) ?? files[0] ?? null)} hint="A .zip of face photos" />
      )}
      <Button className="h-10 w-full" variant="outline" disabled={!file || starting} onClick={start}>
        {starting ? <Spinner /> : <FileArchive />} Start bulk import
      </Button>
      {process && (
        <div className="space-y-1.5">
          <div className="flex items-center justify-between text-xs text-muted-foreground">
            <span>{process.status === "done" ? "Finished" : "Importing…"}</span>
            <span>
              {process.processed}/{process.total}
            </span>
          </div>
          <Progress value={percent} />
        </div>
      )}
    </div>
  );
}

function EnrolledPanel() {
  const toast = useToast();
  const invalidate = useInvalidate();
  const { can } = useSession();
  const { data, isLoading } = useApi<FaceEnrollment[]>(FACES_PATH);
  const [deletingId, setDeletingId] = useState<string | null>(null);
  const [unlockingId, setUnlockingId] = useState<string | null>(null);
  const [query, setQuery] = useState("");

  const canDelete = (enrollment: FaceEnrollment) => enrollment.canRemove ?? can("face_enrollments", "delete");
  const rows = (data ?? []).filter((enrollment) => `${enrollment.name} ${enrollment.email}`.toLowerCase().includes(query.trim().toLowerCase()));

  const unlock = async (enrollment: FaceEnrollment) => {
    setUnlockingId(enrollment.userId);
    try {
      await api.post(`/faces/enrollments/${enrollment.userId}/unlock`, {});
      await invalidate(FACES_PATH);
      toast.success("Lock reset", `${enrollment.name} can update their own face again.`);
    } catch (error) {
      toast.error("Could not reset the lock", error instanceof Error ? error.message : "Unknown error");
    } finally {
      setUnlockingId(null);
    }
  };

  const remove = async (enrollment: FaceEnrollment) => {
    setDeletingId(enrollment.userId);
    try {
      await api.delete(`/faces/enrollments/${enrollment.userId}`);
      await invalidate(FACES_PATH);
      toast.success("Enrollment removed", `${enrollment.name} was removed.`);
    } catch (error) {
      toast.error("Could not remove enrollment", error instanceof Error ? error.message : "Unknown error");
    } finally {
      setDeletingId(null);
    }
  };

  if (isLoading) return <Skeleton className="h-64" />;

  return (
    <Card>
      <CardHeader className="flex-wrap">
        <div>
          <CardTitle>Enrolled people</CardTitle>
          <CardDescription>
            {can("face_enrollments", "read", ["all"]) ? "Everyone whose face can currently be recognized." : "Enrollments you are allowed to see."}
          </CardDescription>
        </div>
        {(data?.length ?? 0) > 5 && (
          <Input value={query} onChange={(event) => setQuery(event.target.value)} placeholder="Search name or email" className="w-full sm:w-56" aria-label="Search enrollments" />
        )}
      </CardHeader>
      <CardContent className="px-0">
        {rows.length > 0 ? (
          <Table>
            <thead className="border-b">
              <tr>
                <Th>Name</Th>
                <Th className="hidden md:table-cell">Email</Th>
                <Th>Enrolled</Th>
                <Th></Th>
              </tr>
            </thead>
            <tbody>
              {rows.map((enrollment) => (
                <tr key={enrollment.userId} className="border-b last:border-0">
                  <Td>
                    <div className="flex items-center gap-3">
                      <Avatar name={enrollment.name} size={32} />
                      <span className="min-w-0">
                        <span className="block font-medium">{enrollment.name}</span>
                        <span className="block text-xs text-muted-foreground md:hidden">{enrollment.email}</span>
                      </span>
                      {enrollment.selfEnrollLocked && (
                        <Hint label="Self-enrollment limit reached - only department/all-scope access can update this now">
                          <Badge tone="warning">
                            <Lock className="size-3" /> Locked
                          </Badge>
                        </Hint>
                      )}
                    </div>
                  </Td>
                  <Td className="hidden text-muted-foreground md:table-cell">{enrollment.email}</Td>
                  <Td className="whitespace-nowrap text-muted-foreground">{formatDate(enrollment.enrolledAt, true)}</Td>
                  <Td className="whitespace-nowrap text-right">
                    {enrollment.selfEnrollLocked && enrollment.canUnlock && (
                      <Button variant="outline" size="sm" className="mr-2" disabled={unlockingId === enrollment.userId} onClick={() => unlock(enrollment)}>
                        {unlockingId === enrollment.userId ? <Spinner /> : <KeyRound />} Reset lock
                      </Button>
                    )}
                    {canDelete(enrollment) && (
                      <Hint label="Remove enrollment">
                        <Button variant="destructive" size="icon" disabled={deletingId === enrollment.userId} onClick={() => remove(enrollment)} aria-label="Remove enrollment">
                          {deletingId === enrollment.userId ? <Spinner /> : <Trash2 />}
                        </Button>
                      </Hint>
                    )}
                  </Td>
                </tr>
              ))}
            </tbody>
          </Table>
        ) : (
          <EmptyState
            icon={<Users2 className="size-7" />}
            title={query ? "No matching enrollments" : "No one is enrolled yet"}
            description={query ? "Try a different name or email." : "Enroll a face from the Enroll tab to start recognizing people."}
          />
        )}
      </CardContent>
    </Card>
  );
}
