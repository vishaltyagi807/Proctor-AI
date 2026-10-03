import { useState } from "react";
import { RefreshCcw, ScanFace, UserPlus } from "lucide-react";
import { api } from "@/lib/api";
import { useAction } from "@/lib/query";
import { cn } from "@/lib/utils";
import { useToast } from "@/components/ui/toast";
import { Modal } from "@/components/ui/modal";
import { Button } from "@/components/ui/button";
import { Checkbox } from "@/components/ui/checkbox";
import { Skeleton, Spinner } from "@/components/ui/misc";
import { ImagePicker } from "@/components/faces/image-picker";
import { FaceResult } from "@/components/faces/face-result";
import type { FaceMatch, FaceRecognizeResult } from "@/lib/types";

export type DetectedPerson = { id: string; name: string; email: string };

function identified(faces: FaceMatch[]): DetectedPerson[] {
  const seen = new Map<string, DetectedPerson>();
  for (const face of faces) {
    if (face.matched && face.user && !seen.has(face.user.id)) {
      seen.set(face.user.id, { id: face.user.id, name: face.user.name, email: face.user.email ?? "" });
    }
  }
  return [...seen.values()];
}

async function toFile(dataUrl: string): Promise<File> {
  const blob = await (await fetch(dataUrl)).blob();
  const extension = blob.type.split("/")[1]?.replace("jpeg", "jpg") || "jpg";
  return new File([blob], `face-scan-${Date.now()}.${extension}`, { type: blob.type || "image/jpeg" });
}

export function FaceScanModal({ open, onClose, onAdd }: { open: boolean; onClose: () => void; onAdd: (people: DetectedPerson[], photo: File) => void }) {
  const toast = useToast();
  const [image, setImage] = useState<string | null>(null);
  const [result, setResult] = useState<FaceRecognizeResult | null>(null);
  const [picked, setPicked] = useState<string[]>([]);
  const [adding, setAdding] = useState(false);

  const recognize = useAction(() => api.post<FaceRecognizeResult>("/faces/recognize", { image, source: "upload" }), {
    onSuccess: (data) => {
      setResult(data);
      setPicked(identified(data.faces).map((person) => person.id));
    },
    onError: (error) => toast.error("Could not recognize", error.message),
  });

  const people = result ? identified(result.faces) : [];
  const unknown = result ? result.faces.filter((face) => !face.matched || !face.user).length : 0;
  const allPicked = people.length > 0 && picked.length === people.length;

  const reset = () => {
    setImage(null);
    setResult(null);
    setPicked([]);
  };

  const close = () => {
    reset();
    onClose();
  };

  const add = async () => {
    if (!image) return;
    setAdding(true);
    try {
      onAdd(
        people.filter((person) => picked.includes(person.id)),
        await toFile(image),
      );
      close();
    } finally {
      setAdding(false);
    }
  };

  const toggle = (id: string, checked: boolean) => setPicked((current) => (checked ? [...current, id] : current.filter((value) => value !== id)));

  return (
    <Modal
      open={open}
      onClose={close}
      size="lg"
      title="Identify people from a photo"
      description="Upload a photo, then choose who this complaint is about. The photo is attached as evidence."
      footer={
        result ? (
          <>
            <Button variant="outline" className="h-10" onClick={reset} disabled={adding}>
              <RefreshCcw /> Another photo
            </Button>
            <Button className="h-10 gradient-brand text-white" disabled={picked.length === 0 || adding} onClick={add}>
              {adding ? <Spinner /> : <UserPlus />} Add {picked.length} {picked.length === 1 ? "person" : "people"}
            </Button>
          </>
        ) : (
          <>
            <Button variant="outline" className="h-10" onClick={close}>
              Cancel
            </Button>
            <Button className="h-10 gradient-brand text-white" disabled={!image || recognize.isPending} onClick={() => recognize.mutate(undefined)}>
              {recognize.isPending ? <Spinner /> : <ScanFace />} Detect people
            </Button>
          </>
        )
      }
    >
      {recognize.isPending ? (
        <Skeleton className="h-72" />
      ) : result ? (
        <FaceResult
          result={result}
          emptyText="No faces were detected in this photo. Try a clearer, forward-facing shot."
          selected={picked}
          onToggle={(id) => toggle(id, !picked.includes(id))}
          className="md:grid-cols-[minmax(0,3fr)_minmax(0,2fr)]"
          listHeader={
            <label className={cn("flex items-center justify-between gap-3 rounded-xl border bg-muted/30 px-3 py-2.5", people.length > 0 && "cursor-pointer")}>
              <span className="flex items-center gap-2.5 text-sm font-medium">
                <Checkbox
                  checked={allPicked}
                  indeterminate={picked.length > 0 && !allPicked}
                  disabled={people.length === 0}
                  onCheckedChange={(checked) => setPicked(checked ? people.map((person) => person.id) : [])}
                />
                Select all
              </span>
              <span className="text-xs text-muted-foreground">
                {people.length} identified{unknown > 0 && ` · ${unknown} unknown`}
              </span>
            </label>
          }
        />
      ) : (
        <ImagePicker value={image} onChange={setImage} />
      )}
    </Modal>
  );
}
