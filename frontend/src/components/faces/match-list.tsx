import { Avatar } from "@/components/ui/misc";
import { Badge } from "@/components/ui/badge";
import type { FaceMatch } from "@/lib/types";

export function MatchList({ faces, emptyText }: { faces: FaceMatch[]; emptyText: string }) {
  if (faces.length === 0) return <p className="text-sm text-muted-foreground">{emptyText}</p>;
  return (
    <ul className="space-y-2">
      {faces.map((face, index) => (
        <li key={`${face.box.x}-${face.box.y}-${index}`} className="flex items-center justify-between gap-3 rounded-xl border bg-muted/30 p-3">
          <div className="flex min-w-0 items-center gap-3">
            <Avatar name={face.matched && face.user ? face.user.name : "?"} size={32} />
            <div className="min-w-0">
              <p className="truncate text-sm font-medium">{face.matched && face.user ? face.user.name : "Unknown"}</p>
              {face.matched && face.user?.email && <p className="truncate text-xs text-muted-foreground">{face.user.email}</p>}
            </div>
          </div>
          <Badge tone={face.matched ? "success" : "neutral"} dot>
            {face.matched ? (face.confidence !== null ? `${Math.round(face.confidence * 100)}% match` : "Identified") : "Not identified"}
          </Badge>
        </li>
      ))}
    </ul>
  );
}
