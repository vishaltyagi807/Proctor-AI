export function title(value?: string) {
  return { meta: [{ title: value ? `${value} · ProctorAI` : "ProctorAI" }] };
}
