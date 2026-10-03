import { useId } from "react";
import { motion } from "motion/react";
import { Check, Lock } from "lucide-react";
import { cn } from "@/lib/utils";
import { Hint } from "@/components/ui/hint";
import { Select as SelectRoot, SelectContent, SelectGroup, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";

const control =
  "w-full rounded-xl border bg-background/60 px-3.5 py-2 text-sm outline-none transition placeholder:text-muted-foreground/70 focus:border-ring focus:ring-4 focus:ring-ring/15 disabled:opacity-60";

export function Input({ className, ...props }: React.ComponentProps<"input">) {
  return <input className={cn(control, "h-10", className)} {...props} />;
}

export function Textarea({ className, ...props }: React.ComponentProps<"textarea">) {
  return <textarea className={cn(control, "min-h-24 resize-y py-2.5", className)} {...props} />;
}

export function Select({
  value,
  onValueChange,
  options,
  placeholder,
  disabled,
  className,
  "aria-label": ariaLabel,
}: {
  value: string;
  onValueChange: (value: string) => void;
  options: { value: string; label: string }[];
  placeholder?: string;
  disabled?: boolean;
  className?: string;
  "aria-label"?: string;
}) {
  return (
    <SelectRoot items={options} value={value} onValueChange={(next) => onValueChange(next ?? "")} disabled={disabled}>
      <SelectTrigger aria-label={ariaLabel} className={cn("h-10 w-full min-w-0 rounded-xl bg-background/60 px-3.5", className)}>
        <SelectValue placeholder={placeholder} className="truncate" />
      </SelectTrigger>
      <SelectContent alignItemWithTrigger={false} className="max-h-72">
        <SelectGroup>
          {options.map((option) => (
            <SelectItem key={option.value} value={option.value}>
              {option.label}
            </SelectItem>
          ))}
        </SelectGroup>
      </SelectContent>
    </SelectRoot>
  );
}

export function Field({ label, hint, error, children, className }: { label: string; hint?: string; error?: string | null; children: React.ReactNode; className?: string }) {
  const id = useId();
  return (
    <div className={cn("space-y-1.5", className)}>
      <label htmlFor={id} className="text-[13px] font-medium">
        {label}
      </label>
      <div id={id}>{children}</div>
      {hint && !error && <p className="text-xs text-muted-foreground">{hint}</p>}
      {error && <p className="text-xs text-destructive">{error}</p>}
    </div>
  );
}

export function Switch({ checked, onChange, disabled, label }: { checked: boolean; onChange: (value: boolean) => void; disabled?: boolean; label?: string }) {
  return (
    <button
      type="button"
      role="switch"
      aria-checked={checked}
      aria-label={label}
      disabled={disabled}
      onClick={() => onChange(!checked)}
      className={cn("relative h-6 w-11 shrink-0 rounded-full transition-colors disabled:opacity-50", checked ? "gradient-brand" : "bg-muted-foreground/25")}
    >
      <motion.span
        layout
        transition={{ type: "spring", stiffness: 500, damping: 32 }}
        className={cn("absolute top-0.5 size-5 rounded-full bg-white shadow", checked ? "left-[22px]" : "left-0.5")}
      />
    </button>
  );
}

export function Chips<T extends string>({
  options,
  value,
  onChange,
  multiple = true,
  empty = "Nothing to choose from",
}: {
  options: { value: T; label: string; disabled?: boolean; title?: string }[];
  value: T[];
  onChange: (value: T[]) => void;
  multiple?: boolean;
  empty?: string;
}) {
  if (options.length === 0) return <p className="text-xs text-muted-foreground">{empty}</p>;
  return (
    <div className="flex flex-wrap gap-2">
      {options.map((option) => {
        const active = value.includes(option.value);
        return (
          <Hint key={option.value} label={option.title}>
            <motion.button
              type="button"
              disabled={option.disabled}
              whileTap={option.disabled ? undefined : { scale: 0.94 }}
              onClick={() => onChange(multiple ? (active ? value.filter((v) => v !== option.value) : [...value, option.value]) : active ? [] : [option.value])}
              className={cn(
                "inline-flex items-center gap-1.5 rounded-full border px-3 py-1 text-xs font-medium transition disabled:cursor-not-allowed disabled:opacity-60",
                active ? "border-transparent gradient-brand text-white shadow-md shadow-primary/25" : "bg-background/60 text-muted-foreground enabled:hover:border-ring/60 enabled:hover:text-foreground",
              )}
            >
              {option.disabled ? <Lock className="size-3" /> : active && <Check className="size-3" />}
              {option.label}
            </motion.button>
          </Hint>
        );
      })}
    </div>
  );
}

export function Checkbox({ checked, onChange, label }: { checked: boolean; onChange: (value: boolean) => void; label: string }) {
  return (
    <label className="flex cursor-pointer items-center gap-2.5 text-sm">
      <button
        type="button"
        role="checkbox"
        aria-checked={checked}
        onClick={() => onChange(!checked)}
        className={cn("grid size-5 place-items-center rounded-md border transition", checked ? "border-transparent gradient-brand text-white" : "bg-background")}
      >
        <motion.span initial={false} animate={{ scale: checked ? 1 : 0, opacity: checked ? 1 : 0 }} transition={{ duration: 0.15 }}>
          <Check className="size-3.5" />
        </motion.span>
      </button>
      {label}
    </label>
  );
}
