import { useState } from "react";
import { Check } from "lucide-react";
import { cn } from "@/lib/utils";
import { Avatar } from "@/components/ui/misc";
import { Badge } from "@/components/ui/badge";
import { Checkbox } from "@/components/ui/checkbox";
import { identifiedFace as identified, numberFaces, type NumberedFace } from "@/lib/faces";
import type { FaceRecognizeResult } from "@/lib/types";

function describe(face: NumberedFace): string {
  if (!identified(face)) return `Face ${face.number}: not identified`;
  return `Face ${face.number}: ${face.user.name}${face.confidence !== null ? `, ${Math.round(face.confidence * 100)}% match` : ""}`;
}

type Linking = {
  active: string | null;
  onActive: (key: string | null) => void;
  selected?: string[];
  onToggle?: (userId: string) => void;
};

export function FaceOverlay({
  faces,
  width,
  height,
  mirrored = false,
  animate = false,
  active,
  onActive,
  selected,
  onToggle,
}: Linking & { faces: NumberedFace[]; width: number; height: number; mirrored?: boolean; animate?: boolean }) {
  const selectable = Boolean(onToggle && selected);
  const w = width || 1;
  const h = height || 1;
  return (
    <>
      {faces.map((face) => {
        const known = identified(face);
        const highlighted = active === face.key;
        const dimmed = active !== null && !highlighted;
        const chosen = known && Boolean(selected?.includes(face.user.id));
        const near = face.box.y / h < 0.08;
        const left = mirrored ? w - face.box.x - face.box.width : face.box.x;
        const interactive = selectable && known;
        const Box = interactive ? "button" : "div";
        return (
          <Box
            key={face.key}
            {...(interactive ? { type: "button" as const, "aria-pressed": chosen } : {})}
            aria-label={describe(face)}
            tabIndex={0}
            onPointerEnter={() => onActive(face.key)}
            onPointerLeave={() => onActive(null)}
            onFocus={() => onActive(face.key)}
            onBlur={() => onActive(null)}
            onClick={() => interactive && onToggle?.(face.user.id)}
            className={cn(
              "absolute rounded-md border-2 outline-none",
              animate ? "transition-[left,top,width,height,opacity,box-shadow] duration-300 ease-out" : "transition-[opacity,box-shadow] duration-150",
              known ? "border-primary" : "border-dashed border-white",
              interactive && !chosen && "border-primary/60",
              highlighted ? "z-20 shadow-[0_0_0_3px_rgba(0,0,0,0.45),0_0_0_6px_var(--ring)]" : "z-10 shadow-[0_0_0_1px_rgba(0,0,0,0.55)]",
              dimmed && "opacity-35",
              interactive && "cursor-pointer",
            )}
            style={{
              left: `${(left / w) * 100}%`,
              top: `${(face.box.y / h) * 100}%`,
              width: `${(face.box.width / w) * 100}%`,
              height: `${(face.box.height / h) * 100}%`,
            }}
          >
            <span
              className={cn(
                "pointer-events-none absolute left-[-2px] flex max-w-[11rem] items-center gap-1 whitespace-nowrap rounded-md px-1.5 py-0.5 text-[11px] font-semibold leading-4 shadow-sm",
                near ? "top-full mt-1" : "bottom-full mb-1",
                known ? "bg-primary text-primary-foreground" : "bg-black/75 text-white",
              )}
            >
              {interactive && (chosen ? <Check className="size-3 shrink-0" /> : <span className="size-2.5 shrink-0 rounded-full border border-current" />)}
              <span className="tabular-nums">{face.number}</span>
              {known && <span className="truncate font-medium">{face.user.name}</span>}
            </span>
          </Box>
        );
      })}
    </>
  );
}

export function FaceList({
  faces,
  emptyText,
  header,
  active,
  onActive,
  selected,
  onToggle,
}: Linking & { faces: NumberedFace[]; emptyText: string; header?: React.ReactNode }) {
  const selectable = Boolean(onToggle && selected);
  if (faces.length === 0) return <p className="text-sm text-muted-foreground">{emptyText}</p>;
  return (
    <div className="min-w-0 space-y-3">
      {header}
      <ul className="space-y-2" aria-label="Detected faces">
        {faces.map((face) => {
          const known = identified(face);
          const highlighted = active === face.key;
          const chosen = known && Boolean(selected?.includes(face.user.id));
          const interactive = selectable && known;
          const Row = interactive ? "label" : "div";
          return (
            <li key={face.key}>
              <Row
                tabIndex={interactive ? undefined : 0}
                onPointerEnter={() => onActive(face.key)}
                onPointerLeave={() => onActive(null)}
                onFocus={() => onActive(face.key)}
                onBlur={() => onActive(null)}
                className={cn(
                  "flex items-center gap-3 rounded-xl border p-3 outline-none transition",
                  highlighted ? "border-ring bg-muted/50" : "bg-muted/20",
                  interactive && "cursor-pointer",
                  chosen && "border-primary/50 bg-primary/5",
                  !known && "border-dashed",
                )}
              >
                {interactive && <Checkbox checked={chosen} onCheckedChange={() => onToggle?.(face.user.id)} aria-label={`Select ${face.user.name}`} />}
                <span
                  className={cn(
                    "grid size-6 shrink-0 place-items-center rounded-md text-xs font-semibold tabular-nums",
                    known ? "bg-primary text-primary-foreground" : "bg-foreground/80 text-background",
                  )}
                  aria-hidden
                >
                  {face.number}
                </span>
                <Avatar name={known ? face.user.name : "?"} size={32} />
                <span className="min-w-0 flex-1">
                  <span className="block truncate text-sm font-medium">{known ? face.user.name : "Not identified"}</span>
                  {known && face.user.email && <span className="block truncate text-xs text-muted-foreground">{face.user.email}</span>}
                  {!known && <span className="block truncate text-xs text-muted-foreground">No enrolled match, or outside your access</span>}
                </span>
                {known && (
                  <Badge tone="success" dot>
                    {face.confidence !== null ? `${Math.round(face.confidence * 100)}%` : "Identified"}
                  </Badge>
                )}
              </Row>
            </li>
          );
        })}
      </ul>
    </div>
  );
}

export function FaceResult({
  result,
  emptyText,
  selected,
  onToggle,
  listHeader,
  className,
}: {
  result: FaceRecognizeResult;
  emptyText: string;
  selected?: string[];
  onToggle?: (userId: string) => void;
  listHeader?: React.ReactNode;
  className?: string;
}) {
  const [active, setActive] = useState<string | null>(null);
  const faces = numberFaces(result.faces);
  const linking = { active, onActive: setActive, selected, onToggle };
  return (
    <div className={cn("grid gap-5", className)}>
      <div className="relative self-start overflow-hidden rounded-xl border bg-black/5">
        <img src={`data:image/png;base64,${result.image}`} alt={`Photo with ${faces.length} detected ${faces.length === 1 ? "face" : "faces"}`} className="block h-auto w-full" />
        <FaceOverlay faces={faces} width={result.width} height={result.height} {...linking} />
      </div>
      <FaceList faces={faces} emptyText={emptyText} header={listHeader} {...linking} />
    </div>
  );
}
