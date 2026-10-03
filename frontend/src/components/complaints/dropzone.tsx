import { useRef, useState } from "react";
import { AnimatePresence, motion } from "motion/react";
import { CheckCircle2, FileText, ImageIcon, UploadCloud, Video, X, AlertCircle } from "lucide-react";
import { Progress, Spinner } from "@/components/ui/misc";
import { formatBytes } from "@/lib/format";
import { cn } from "@/lib/utils";
import { Hint } from "@/components/ui/hint";

export type QueuedFile = { id: string; file: File; progress: number; state: "queued" | "uploading" | "done" | "error"; error?: string };

export function FileTypeIcon({ type, className }: { type: string; className?: string }) {
  if (type.startsWith("image/")) return <ImageIcon className={className} />;
  if (type.startsWith("video/")) return <Video className={className} />;
  return <FileText className={className} />;
}

export function Dropzone({ onFiles, disabled, hint }: { onFiles: (files: File[]) => void; disabled?: boolean; hint?: string }) {
  const input = useRef<HTMLInputElement>(null);
  const [over, setOver] = useState(false);
  return (
    <motion.div
      onDragOver={(event) => {
        event.preventDefault();
        if (!disabled) setOver(true);
      }}
      onDragLeave={() => setOver(false)}
      onDrop={(event) => {
        event.preventDefault();
        setOver(false);
        if (!disabled) onFiles(Array.from(event.dataTransfer.files));
      }}
      animate={{ scale: over ? 1.015 : 1 }}
      className={cn("relative flex flex-col items-center justify-center gap-2 rounded-2xl border-2 border-dashed px-6 py-8 text-center transition-colors", over ? "border-primary bg-primary/8" : "border-border hover:border-primary/50", disabled && "opacity-50")}
    >
      <motion.span animate={{ y: over ? -4 : 0 }} className="grid size-12 place-items-center rounded-2xl bg-primary/10 text-primary">
        <UploadCloud className="size-6" />
      </motion.span>
      <p className="text-sm font-medium">
        Drag files here or{" "}
        <button type="button" disabled={disabled} onClick={() => input.current?.click()} className="text-primary underline-offset-4 hover:underline">
          browse
        </button>
      </p>
      <p className="text-xs text-muted-foreground">{hint ?? "Images, video, audio, PDF and office documents up to 200 MB"}</p>
      <input
        ref={input}
        type="file"
        multiple
        hidden
        onChange={(event) => {
          onFiles(Array.from(event.target.files ?? []));
          event.target.value = "";
        }}
      />
    </motion.div>
  );
}

export function UploadQueue({ items, onRemove }: { items: QueuedFile[]; onRemove?: (id: string) => void }) {
  return (
    <ul className="space-y-2">
      <AnimatePresence initial={false}>
        {items.map((item) => {
          return (
            <motion.li key={item.id} layout initial={{ opacity: 0, y: 10 }} animate={{ opacity: 1, y: 0 }} exit={{ opacity: 0, scale: 0.95 }} className="flex items-center gap-3 rounded-xl border bg-card p-3">
              <span className="grid size-9 place-items-center rounded-lg bg-muted text-muted-foreground">
                <FileTypeIcon type={item.file.type} className="size-4" />
              </span>
              <div className="min-w-0 flex-1">
                <p className="truncate text-sm font-medium">{item.file.name}</p>
                <div className="mt-1 flex items-center gap-3">
                  {item.state === "error" ? <span className="text-xs text-destructive">{item.error}</span> : <Progress value={item.state === "done" ? 100 : item.progress} className="flex-1" />}
                  <span className="text-xs text-muted-foreground">{formatBytes(item.file.size)}</span>
                </div>
              </div>
              {item.state === "uploading" && <Spinner className="text-primary" />}
              {item.state === "done" && <CheckCircle2 className="size-5 text-success" />}
              {item.state === "error" && <AlertCircle className="size-5 text-destructive" />}
              {item.state === "queued" && onRemove && (
                <Hint label="Remove">
                  <button onClick={() => onRemove(item.id)} className="rounded-md p-1 text-muted-foreground hover:bg-muted" aria-label="Remove">
                    <X className="size-4" />
                  </button>
                </Hint>
              )}
            </motion.li>
          );
        })}
      </AnimatePresence>
    </ul>
  );
}
