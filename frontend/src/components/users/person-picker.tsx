import { cn } from "@/lib/utils";
import { Avatar } from "@/components/ui/misc";
import {
  Combobox,
  ComboboxChip,
  ComboboxChips,
  ComboboxChipsInput,
  ComboboxContent,
  ComboboxEmpty,
  ComboboxItem,
  ComboboxList,
  ComboboxValue,
  useComboboxAnchor,
} from "@/components/ui/combobox";

type Person = { id: string; name: string; email: string };

function lookup(people: Person[]) {
  const byId = new Map(people.map((person) => [person.id, person]));
  return {
    ids: people.map((person) => person.id),
    name: (id: string) => byId.get(id)?.name ?? id,
    matches: (id: string, query: string) => {
      const person = byId.get(id);
      return `${person?.name ?? ""} ${person?.email ?? ""}`.toLowerCase().includes(query.trim().toLowerCase());
    },
    option: (id: string) => {
      const person = byId.get(id);
      return (
        <ComboboxItem key={id} value={id}>
          <Avatar name={person?.name} size={26} />
          <span className="flex min-w-0 flex-col">
            <span className="truncate">{person?.name ?? id}</span>
            <span className="truncate text-xs text-muted-foreground">{person?.email}</span>
          </span>
        </ComboboxItem>
      );
    },
  };
}

export function PeoplePicker({
  people,
  value,
  onChange,
  placeholder = "Search by name or email…",
  disabled,
  className,
  "aria-label": ariaLabel,
}: {
  people: Person[];
  value: string[];
  onChange: (ids: string[]) => void;
  placeholder?: string;
  disabled?: boolean;
  className?: string;
  "aria-label"?: string;
}) {
  const list = lookup(people);
  const anchor = useComboboxAnchor();
  return (
    <Combobox multiple items={list.ids} value={value} onValueChange={(ids: string[]) => onChange(ids)} itemToStringLabel={list.name} filter={list.matches} disabled={disabled}>
      <ComboboxChips ref={anchor} className={cn("min-h-10 w-full rounded-xl bg-background/60", className)}>
        <ComboboxValue>
          {(ids: string[]) => (
            <>
              {ids.map((id) => (
                <ComboboxChip key={id}>{list.name(id)}</ComboboxChip>
              ))}
              <ComboboxChipsInput placeholder={ids.length === 0 ? placeholder : undefined} aria-label={ariaLabel} />
            </>
          )}
        </ComboboxValue>
      </ComboboxChips>
      <ComboboxContent anchor={anchor}>
        <ComboboxEmpty>No one matches that search.</ComboboxEmpty>
        <ComboboxList>{list.option}</ComboboxList>
      </ComboboxContent>
    </Combobox>
  );
}
