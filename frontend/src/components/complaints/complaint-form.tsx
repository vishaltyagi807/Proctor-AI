import { useState } from "react";
import { useNavigate } from "@tanstack/react-router";
import { Autocomplete } from "@base-ui/react";
import { motion } from "motion/react";
import { FilePlus2, ScanFace, Send } from "lucide-react";
import { api } from "@/lib/api";
import { useApi, useInvalidate } from "@/lib/query";
import { uploadComplaintFile } from "@/lib/uploads";
import { useSession } from "@/components/providers/session";
import { useToast } from "@/components/ui/toast";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Chips, Field, Input, Select, Textarea } from "@/components/ui/form";
import { ComboboxContent, ComboboxEmpty, ComboboxInput, ComboboxItem, ComboboxList } from "@/components/ui/combobox";
import { PeoplePicker } from "@/components/users/person-picker";
import { RadioGroup, RadioGroupItem } from "@/components/ui/radio-group";
import { PageHeader, Spinner } from "@/components/ui/misc";
import { CustomFieldsForm, type CustomValues } from "@/components/custom-fields/custom-fields-form";
import { Dropzone, UploadQueue, type QueuedFile } from "./dropzone";
import { FaceScanModal, type DetectedPerson } from "./face-subjects";
import { COMPLAINT_PRIORITIES } from "@/lib/complaints";
import type { Complaint, Department, PageResponse, UserInfo } from "@/lib/types";

const CATEGORIES = ["general", "infrastructure", "academic", "discipline", "hostel", "harassment", "facilities", "other"];

export function ComplaintForm() {
  const navigate = useNavigate();
  const toast = useToast();
  const invalidate = useInvalidate();
  const { session, can } = useSession();
  const onBehalf = can("complaints", "write", ["department", "all"]);
  const canScan = onBehalf && can("face_recognition", "read");

  const departments = useApi<PageResponse<Department>>("/departments?size=100&sortBy=name&direction=asc");
  const people = useApi<PageResponse<UserInfo>>(onBehalf ? "/users?size=100&sortBy=name&direction=asc" : null);

  const [title, setTitle] = useState("");
  const [description, setDescription] = useState("");
  const [category, setCategory] = useState("general");
  const [priority, setPriority] = useState("medium");
  const [departmentId, setDepartmentId] = useState("");
  const [subjectIds, setSubjectIds] = useState<string[]>([]);
  const [detected, setDetected] = useState<DetectedPerson[]>([]);
  const [separate, setSeparate] = useState(false);
  const [scanning, setScanning] = useState(false);
  const [custom, setCustom] = useState<CustomValues>({});
  const [files, setFiles] = useState<QueuedFile[]>([]);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const addFiles = (incoming: File[]) =>
    setFiles((current) => [...current, ...incoming.map((file) => ({ id: `${file.name}-${file.size}-${Math.random()}`, file, progress: 0, state: "queued" as const }))]);

  const patchFile = (id: string, patch: Partial<QueuedFile>) => setFiles((current) => current.map((item) => (item.id === id ? { ...item, ...patch } : item)));

  const candidates = [...(people.data?.content ?? []), ...detected.filter((person) => !people.data?.content.some((known) => known.id === person.id))].map((person) => ({
    id: person.id,
    name: person.id === session.user.id ? `${person.name} (you)` : person.name,
    email: person.email,
  }));

  const addDetected = (found: DetectedPerson[], photo: File) => {
    setDetected((current) => [...current, ...found.filter((person) => !current.some((known) => known.id === person.id))]);
    setSubjectIds((current) => [...current, ...found.map((person) => person.id).filter((id) => !current.includes(id))]);
    addFiles([photo]);
    toast.success(found.length === 1 ? "1 person added" : `${found.length} people added`, "The photo was attached as evidence.");
  };

  async function fileComplaint(subjects: string[]) {
    const complaint = await api.post<Complaint>("/complaints", {
      title,
      description,
      category,
      priority,
      departmentId: departmentId || undefined,
      studentId: subjects[0],
      subjectIds: subjects.length > 1 ? subjects.slice(1) : undefined,
      customFields: Object.keys(custom).length ? custom : undefined,
    });
    let failed = 0;
    for (const item of files) {
      patchFile(item.id, { state: "uploading", progress: 0 });
      try {
        await uploadComplaintFile(complaint.id, item.file, (progress) => patchFile(item.id, { progress }));
        patchFile(item.id, { state: "done", progress: 100 });
      } catch (uploadError) {
        failed += 1;
        patchFile(item.id, { state: "error", error: (uploadError as Error).message });
      }
    }
    return { complaint, failed };
  }

  async function submit(event: React.FormEvent) {
    event.preventDefault();
    setBusy(true);
    setError(null);
    const groups = subjectIds.length === 0 ? [[]] : separate ? subjectIds.map((id) => [id]) : [subjectIds];
    const filed: Complaint[] = [];
    let failed = 0;
    try {
      for (const group of groups) {
        const outcome = await fileComplaint(group);
        filed.push(outcome.complaint);
        failed += outcome.failed;
      }
    } catch (submitError) {
      const message = (submitError as Error).message;
      if (filed.length === 0) {
        setError(message);
        setBusy(false);
        return;
      }
      const done = groups.slice(0, filed.length).flat();
      setSubjectIds((current) => current.filter((id) => !done.includes(id)));
      setError(`Filed ${filed.length} of ${groups.length} complaints, then stopped: ${message} The people still selected have no complaint yet. Submit again to retry them.`);
      await invalidate("/complaints", "/notifications");
      setBusy(false);
      return;
    }
    await invalidate("/complaints", "/notifications");
    if (failed > 0) toast.error(filed.length === 1 ? "Complaint filed, but some files failed" : `${filed.length} complaints filed, but some files failed`, "You can retry the uploads from the complaint page.");
    else if (filed.length === 1) toast.success("Complaint filed", "You will be notified about every update.");
    else toast.success(`${filed.length} complaints filed`, "One for each selected person. You will be notified about every update.");
    if (filed.length === 1) navigate({ to: "/complaints/$id", params: { id: filed[0].id } });
    else navigate({ to: "/complaints" });
  }

  return (
    <div className="mx-auto max-w-3xl">
      <PageHeader title="New complaint" description="Describe the issue clearly and attach any evidence." icon={<FilePlus2 className="size-5" />} />
      <form onSubmit={submit} className="space-y-6">
        <Card>
          <CardHeader>
            <div>
              <CardTitle>What happened?</CardTitle>
              <CardDescription>A precise title helps the right person pick this up quickly.</CardDescription>
            </div>
          </CardHeader>
          <CardContent className="space-y-5">
            {onBehalf && (
              <Field
                label="Complaint about"
                hint={canScan ? "Search for people or identify them from a photo. Leave empty to file it about yourself." : "Pick the people this complaint concerns. Leave empty to file it about yourself."}
              >
                <div className="flex flex-col gap-2 sm:flex-row sm:items-start">
                  <PeoplePicker
                    className="flex-1"
                    people={candidates}
                    value={subjectIds}
                    onChange={setSubjectIds}
                    placeholder={`Myself (${session.user.name}) · search to add people`}
                    aria-label="Complaint about"
                  />
                  {canScan && (
                    <Button type="button" variant="outline" className="h-10 shrink-0 rounded-xl" onClick={() => setScanning(true)} disabled={busy}>
                      <ScanFace /> From photo
                    </Button>
                  )}
                </div>
              </Field>
            )}
            {subjectIds.length > 1 && (
              <Field label="How should this be filed?">
                <RadioGroup value={separate ? "separate" : "shared"} onValueChange={(value) => setSeparate(value === "separate")} className="grid gap-3 sm:grid-cols-2">
                  {[
                    { value: "shared", title: "One shared complaint", text: `A single case naming all ${subjectIds.length} people.` },
                    { value: "separate", title: "One complaint each", text: `${subjectIds.length} separate cases, one per person, each with the same details and evidence.` },
                  ].map((option) => (
                    <label
                      key={option.value}
                      className="flex cursor-pointer items-start gap-3 rounded-xl border bg-background/60 p-3.5 transition hover:border-ring/60 has-data-checked:border-primary/60 has-data-checked:bg-primary/5"
                    >
                      <RadioGroupItem value={option.value} className="mt-0.5" disabled={busy} />
                      <span className="min-w-0">
                        <span className="block text-sm font-medium">{option.title}</span>
                        <span className="block text-xs text-muted-foreground">{option.text}</span>
                      </span>
                    </label>
                  ))}
                </RadioGroup>
              </Field>
            )}
            <Field label="Title">
              <Input required maxLength={200} value={title} onChange={(event) => setTitle(event.target.value)} placeholder="e.g. Hostel wifi is down" />
            </Field>
            <Field label="Description">
              <Textarea required maxLength={5000} rows={6} value={description} onChange={(event) => setDescription(event.target.value)} placeholder="What happened, when, and who is affected?" />
            </Field>
            <div className="grid gap-5 sm:grid-cols-2">
              <Field label="Category">
                <Autocomplete.Root
                  items={CATEGORIES}
                  value={category}
                  onValueChange={setCategory}
                  openOnInputClick
                  filter={(item: string, query: string) => CATEGORIES.includes(query) || item.includes(query.trim().toLowerCase())}
                >
                  <ComboboxInput className="h-10 w-full rounded-xl" aria-label="Category" />
                  <ComboboxContent>
                    <ComboboxEmpty>No match. Your text will be used as the category.</ComboboxEmpty>
                    <ComboboxList>
                      {(item: string) => (
                        <ComboboxItem key={item} value={item} className="capitalize">
                          {item}
                        </ComboboxItem>
                      )}
                    </ComboboxList>
                  </ComboboxContent>
                </Autocomplete.Root>
              </Field>
              <Field label="Department">
                <Select
                  value={departmentId}
                  onValueChange={setDepartmentId}
                  options={[{ value: "", label: "Automatic" }, ...(departments.data?.content ?? []).map((department) => ({ value: department.id, label: department.name }))]}
                />
              </Field>
            </div>
            <Field label="Priority">
              <Chips multiple={false} options={COMPLAINT_PRIORITIES.map((item) => ({ value: item, label: item }))} value={[priority]} onChange={(value) => setPriority(value[0] ?? "medium")} />
            </Field>
            <CustomFieldsForm entity="complaints" roleIds={session.roles.map((role) => role.id)} departmentIds={departmentId ? [departmentId] : []} value={custom} onChange={setCustom} />
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <div>
              <CardTitle>Evidence</CardTitle>
              <CardDescription>Photos, video, audio or documents. Files upload securely straight to storage.</CardDescription>
            </div>
          </CardHeader>
          <CardContent className="space-y-4">
            <Dropzone onFiles={addFiles} disabled={busy || !can("complaint_files", "write")} />
            {files.length > 0 && <UploadQueue items={files} onRemove={(id) => setFiles((current) => current.filter((item) => item.id !== id))} />}
          </CardContent>
        </Card>

        {error && (
          <motion.p initial={{ opacity: 0 }} animate={{ opacity: 1 }} className="rounded-xl border border-destructive/30 bg-destructive/10 px-4 py-3 text-sm text-destructive" role="alert">
            {error}
          </motion.p>
        )}
        <div className="flex justify-end gap-3">
          <Button type="button" variant="outline" className="h-11 px-5" onClick={() => window.history.back()} disabled={busy}>
            Cancel
          </Button>
          <Button type="submit" disabled={busy} className="h-11 gradient-brand px-6 text-white shadow-lg shadow-primary/25">
            {busy ? <Spinner /> : <Send />} {separate && subjectIds.length > 1 ? `Submit ${subjectIds.length} complaints` : "Submit complaint"}
          </Button>
        </div>
      </form>
      {canScan && <FaceScanModal open={scanning} onClose={() => setScanning(false)} onAdd={addDetected} />}
    </div>
  );
}
