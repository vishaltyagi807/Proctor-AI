import { AnimatePresence, motion } from "motion/react";
import { Monitor, Moon, Sun } from "lucide-react";
import { useTheme } from "@/components/providers/theme";
import { Hint } from "@/components/ui/hint";
import { DropdownMenu, DropdownMenuContent, DropdownMenuGroup, DropdownMenuLabel, DropdownMenuRadioGroup, DropdownMenuRadioItem, DropdownMenuTrigger } from "@/components/ui/dropdown-menu";

const MODES = [
  { value: "light", label: "Light", icon: Sun },
  { value: "dark", label: "Dark", icon: Moon },
  { value: "system", label: "System", icon: Monitor },
] as const;

export function ThemeToggle() {
  const { mode, resolved, setMode } = useTheme();
  return (
    <DropdownMenu>
      <Hint label="Theme" side="bottom">
        <DropdownMenuTrigger
          render={
            <button className="relative grid size-10 place-items-center rounded-xl border bg-card/60 text-muted-foreground transition hover:text-foreground data-popup-open:text-foreground" aria-label="Change theme">
              <AnimatePresence mode="wait" initial={false}>
                <motion.span
                  key={resolved}
                  initial={{ rotate: -90, opacity: 0, scale: 0.6 }}
                  animate={{ rotate: 0, opacity: 1, scale: 1 }}
                  exit={{ rotate: 90, opacity: 0, scale: 0.6 }}
                  transition={{ duration: 0.2 }}
                >
                  {resolved === "dark" ? <Sun className="size-[18px]" /> : <Moon className="size-[18px]" />}
                </motion.span>
              </AnimatePresence>
            </button>
          }
        />
      </Hint>
      <DropdownMenuContent align="end" className="w-40">
        <DropdownMenuGroup>
          <DropdownMenuLabel>Theme</DropdownMenuLabel>
          <DropdownMenuRadioGroup value={mode} onValueChange={(value) => setMode(value as (typeof MODES)[number]["value"])}>
            {MODES.map((item) => (
              <DropdownMenuRadioItem key={item.value} value={item.value} closeOnClick>
                <item.icon /> {item.label}
              </DropdownMenuRadioItem>
            ))}
          </DropdownMenuRadioGroup>
        </DropdownMenuGroup>
      </DropdownMenuContent>
    </DropdownMenu>
  );
}
