import type { FaceMatch } from "./types";

export const FACE_TABS = ["recognize", "live", "enroll", "enrolled", "activity"] as const;

export type FaceTab = (typeof FACE_TABS)[number];

export function parseFaceTab(value: unknown): FaceTab | undefined {
  return FACE_TABS.find((tab) => tab === value);
}

export type NumberedFace = FaceMatch & { number: number; key: string };

export function identifiedFace(face: FaceMatch): face is FaceMatch & { user: NonNullable<FaceMatch["user"]> } {
  return face.matched && face.user !== null;
}

export function numberFaces(faces: FaceMatch[]): NumberedFace[] {
  const seen = new Map<string, number>();
  return [...faces]
    .sort((a, b) => a.box.x - b.box.x || a.box.y - b.box.y)
    .map((face, index) => {
      const identity = identifiedFace(face) ? `user:${face.user.id}` : "unknown";
      const count = seen.get(identity) ?? 0;
      seen.set(identity, count + 1);
      return { ...face, number: index + 1, key: `${identity}:${count}` };
    });
}
