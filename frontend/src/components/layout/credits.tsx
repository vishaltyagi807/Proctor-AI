import { Fragment } from "react";
import { Hint } from "@/components/ui/hint";
import { CREATORS } from "@/lib/brand";
import { cn } from "@/lib/utils";

export function Credits({ className }: { className?: string }) {
  return (
    <p className={cn("text-[11px] leading-relaxed tracking-wide", className)}>
      Built by{" "}
      {CREATORS.map((creator, index) => (
        <Fragment key={creator.name}>
          {index > 0 && " & "}
          {creator.alias ? (
            <Hint label={`Also known as ${creator.alias}`}>
              <span className="cursor-default font-medium">{creator.name}</span>
            </Hint>
          ) : (
            <span className="font-medium">{creator.name}</span>
          )}
        </Fragment>
      ))}
    </p>
  );
}
