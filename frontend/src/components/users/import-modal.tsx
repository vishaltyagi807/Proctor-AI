import { useState } from "react";
import { AlertCircle, CheckCircle2, FileSpreadsheet } from "lucide-react";
import { api, apiUrl, ApiError } from "@/lib/api";
import { useProcesses } from "@/components/providers/processes";
import { Button } from "@/components/ui/button";
import { Modal } from "@/components/ui/modal";
import { Progress, Spinner } from "@/components/ui/misc";
import { TemplateDownloadMenu } from "@/components/ui/download-menu";
import { Dropzone } from "@/components/complaints/dropzone";

export function ImportModal({ open, onClose }: { open: boolean; onClose: () => void }) {
  const { processes } = useProcesses();
  const [file, setFile] = useState<File | null>(null);
  const [processId, setProcessId] = useState<string | null>(null);
  const [starting, setStarting] = useState(false);
  const [failure, setFailure] = useState<string | null>(null);

  const process = processId ? processes.find((p) => p.processId === processId) : undefined;
  const percent = process && process.total > 0 ? Math.round((process.processed / process.total) * 100) : 0;

  function reset() {
    setFile(null);
    setProcessId(null);
    setFailure(null);
  }

  async function start() {
    if (!file) return;
    setStarting(true);
    setFailure(null);
    try {
      const form = new FormData();
      form.append("file", file);
      const result = await api.post<{ processId: string; total: number }>("/users/import", form);
      setProcessId(result.processId);
    } catch (error) {
      setFailure(error instanceof ApiError ? error.message : "Import failed");
    } finally {
      setStarting(false);
    }
  }

  function close() {
    reset();
    onClose();
  }

  return (
    <Modal
      open={open}
      onClose={close}
      title="Import users"
      description="Upload a CSV, JSON or XLSX file. Progress streams in realtime and also shows in the background processes panel."
      footer={
        process?.status === "done" ? (
          <Button onClick={close} className="gradient-brand text-white">
            Done
          </Button>
        ) : (
          <>
            <Button variant="outline" onClick={close}>
              Cancel
            </Button>
            <Button className="gradient-brand text-white" disabled={!file || starting || !!processId} onClick={start}>
              {starting && <Spinner />} Start import
            </Button>
          </>
        )
      }
    >
      <div className="space-y-4">
        <div className="flex justify-end">
          <TemplateDownloadMenu href={apiUrl("/users/import/template")} />
        </div>
        {!file ? (
          <Dropzone
            onFiles={(files) => setFile(files[0] ?? null)}
            hint="Columns: name, email, password, roles (names, separated by |), departments (codes, separated by |), enabled, verified, plus one column per custom field. Download a template below for the exact columns."
          />
        ) : (
          <div className="flex items-center gap-3 rounded-2xl border p-4">
            <span className="grid size-11 place-items-center rounded-xl bg-primary/10 text-primary">
              <FileSpreadsheet className="size-5" />
            </span>
            <div className="min-w-0 flex-1">
              <p className="truncate font-medium">{file.name}</p>
              <p className="text-xs text-muted-foreground">{(file.size / 1024).toFixed(1)} KB</p>
            </div>
            {!processId && (
              <Button variant="ghost" size="sm" onClick={() => setFile(null)}>
                Change
              </Button>
            )}
          </div>
        )}
        {process && (
          <div className="space-y-2">
            <div className="flex items-center justify-between text-sm">
              <span className="flex items-center gap-2 font-medium">
                {process.status === "done" ? (
                  <>
                    <CheckCircle2 className="size-4 text-success" /> Import finished
                  </>
                ) : (
                  <>
                    <Spinner className="text-primary" /> Importing…
                  </>
                )}
              </span>
              <span className="text-muted-foreground">
                {percent}% {process.total > 0 && `of ${process.total}`}
              </span>
            </div>
            <Progress value={percent} />
          </div>
        )}
        {failure && <p className="rounded-xl bg-destructive/10 px-3.5 py-2.5 text-sm text-destructive">{failure}</p>}
        {process && process.messages.length > 0 && (
          <div className="max-h-40 space-y-1.5 overflow-y-auto rounded-xl border border-destructive/30 bg-destructive/5 p-3 scrollbar-thin">
            {process.messages.map((message, index) => (
              <p key={index} className="flex gap-2 text-xs text-destructive">
                <AlertCircle className="mt-0.5 size-3.5 shrink-0" /> {message}
              </p>
            ))}
          </div>
        )}
      </div>
    </Modal>
  );
}
