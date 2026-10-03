import { Lock } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { RELATION_LABEL, type Relation } from "@/lib/permissions";

const TONE = {
  self: "neutral",
  senior: "warning",
  same_role: "info",
  parallel_role: "violet",
  junior: "neutral",
} as const;

export function RelationBadge({ relation, showJunior = false }: { relation: Relation; showJunior?: boolean }) {
  if (relation === "junior" && !showJunior) return null;
  return <Badge tone={TONE[relation]}>{RELATION_LABEL[relation]}</Badge>;
}

const PERSON: Record<Exclude<Relation, "self" | "junior">, { title: string; body: string }> = {
  senior: {
    title: "This person ranks above you",
    body: "You can view their account, but only someone ranked higher can change it.",
  },
  same_role: {
    title: "This person shares your role",
    body: "Changing people who share your role needs the matching “Same-role peers” permission, for their department or for everyone.",
  },
  parallel_role: {
    title: "This person holds a parallel role",
    body: "They are at your level in a different role. Changing them needs the matching “Parallel-role peers” permission.",
  },
};

const ROLE: Record<Exclude<Relation, "self" | "junior">, { title: string; body: string }> = {
  senior: {
    title: "This role ranks above you",
    body: "You can view it, but only someone ranked higher can change it.",
  },
  same_role: {
    title: "You hold this role",
    body: "Editing a role you hold yourself needs the organization-wide “Same-role peers” permission for that action.",
  },
  parallel_role: {
    title: "This is a parallel role at your level",
    body: "Editing parallel roles needs the organization-wide “Parallel-role peers” permission for that action.",
  },
};

export function RelationNotice({ relation, kind }: { relation: Relation; kind: "person" | "role" }) {
  if (relation === "self" || relation === "junior") return null;
  const copy = (kind === "person" ? PERSON : ROLE)[relation];
  return (
    <Alert>
      <Lock />
      <AlertTitle>{copy.title}</AlertTitle>
      <AlertDescription>{copy.body}</AlertDescription>
    </Alert>
  );
}
