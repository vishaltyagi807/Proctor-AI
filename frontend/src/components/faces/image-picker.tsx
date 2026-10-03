import { useRef, useState } from "react";
import { motion } from "motion/react";
import { Camera, RefreshCcw, ScanFace } from "lucide-react";
import { cn } from "@/lib/utils";

export function ImagePicker({ value, onChange, disabled }: { value: string | null; onChange: (dataUrl: string | null) => void; disabled?: boolean }) {
  const input = useRef<HTMLInputElement>(null);
  const [over, setOver] = useState(false);

  const readFile = (file: File | undefined) => {
    if (!file) return;
    const reader = new FileReader();
    reader.onload = () => onChange(reader.result as string);
    reader.readAsDataURL(file);
  };

  if (value) {
    return (
      <div className="relative overflow-hidden rounded-2xl border bg-muted/30">
        <img src={value} alt="Selected" className="max-h-96 w-full object-contain" />
        {!disabled && (
          <button
            type="button"
            onClick={() => onChange(null)}
            className="absolute right-3 top-3 inline-flex items-center gap-1.5 rounded-full bg-black/60 px-3 py-1.5 text-xs font-medium text-white backdrop-blur transition hover:bg-black/75"
          >
            <RefreshCcw className="size-3.5" /> Change photo
          </button>
        )}
      </div>
    );
  }

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
        if (!disabled) readFile(event.dataTransfer.files[0]);
      }}
      animate={{ scale: over ? 1.015 : 1 }}
      className={cn(
        "relative flex flex-col items-center justify-center gap-2 rounded-2xl border-2 border-dashed px-6 py-10 text-center transition-colors",
        over ? "border-primary bg-primary/8" : "border-border hover:border-primary/50",
        disabled && "opacity-50",
      )}
    >
      <span className="grid size-12 place-items-center rounded-2xl bg-primary/10 text-primary">
        <ScanFace className="size-6" />
      </span>
      <p className="text-sm font-medium">
        Drag a photo here or{" "}
        <button type="button" disabled={disabled} onClick={() => input.current?.click()} className="text-primary underline-offset-4 hover:underline">
          browse
        </button>
      </p>
      <p className="flex items-center gap-1 text-xs text-muted-foreground">
        <Camera className="size-3.5" /> A clear, forward-facing photo works best
      </p>
      <input
        ref={input}
        type="file"
        accept="image/*"
        capture="environment"
        hidden
        onChange={(event) => {
          readFile(event.target.files?.[0]);
          event.target.value = "";
        }}
      />
    </motion.div>
  );
}
