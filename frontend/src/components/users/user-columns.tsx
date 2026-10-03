import { Columns3, Eye, Lock, RotateCcw } from "lucide-react";
import { Button } from "@/components/ui/button";
import {
  DropdownMenu,
  DropdownMenuCheckboxItem,
  DropdownMenuContent,
  DropdownMenuGroup,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import type { useUserColumns } from "@/hooks/use-user-columns";

export function ColumnsMenu({ state }: { state: ReturnType<typeof useUserColumns> }) {
  const builtIn = state.columns.filter((column) => column.kind === "builtin");
  const custom = state.columns.filter((column) => column.kind === "custom");
  return (
    <DropdownMenu>
      <DropdownMenuTrigger render={<Button variant="outline" className="h-9 w-full gap-2 rounded-xl bg-background/60 px-3 sm:w-auto" aria-label="Choose columns" />}>
        <Columns3 className="size-4 text-muted-foreground" />
        Columns
        <span className="rounded-full bg-primary/12 px-1.5 text-[11px] font-medium text-primary tabular-nums">
          {state.visible.length}/{state.columns.length}
        </span>
      </DropdownMenuTrigger>
      <DropdownMenuContent align="end" className="w-60">
        <DropdownMenuGroup>
          <DropdownMenuLabel>Visible columns</DropdownMenuLabel>
          {builtIn.map((column) => {
            const locked = column.kind === "builtin" && column.locked;
            return (
              <DropdownMenuCheckboxItem key={column.id} checked={state.isVisible(column)} disabled={locked} onCheckedChange={(checked) => state.toggle(column, checked)}>
                {column.label}
                {locked && <Lock className="ml-auto size-3 text-muted-foreground" />}
              </DropdownMenuCheckboxItem>
            );
          })}
        </DropdownMenuGroup>
        {custom.length > 0 && (
          <>
            <DropdownMenuSeparator />
            <DropdownMenuGroup>
              <DropdownMenuLabel>Custom fields</DropdownMenuLabel>
              {custom.map((column) => (
                <DropdownMenuCheckboxItem key={column.id} checked={state.isVisible(column)} onCheckedChange={(checked) => state.toggle(column, checked)}>
                  {column.label}
                </DropdownMenuCheckboxItem>
              ))}
            </DropdownMenuGroup>
          </>
        )}
        <DropdownMenuSeparator />
        <DropdownMenuGroup>
          <DropdownMenuItem disabled={state.hiddenCount === 0} onClick={state.showAll}>
            <Eye /> Show all columns
          </DropdownMenuItem>
          <DropdownMenuItem onClick={state.reset}>
            <RotateCcw /> Reset to default
          </DropdownMenuItem>
        </DropdownMenuGroup>
      </DropdownMenuContent>
    </DropdownMenu>
  );
}
