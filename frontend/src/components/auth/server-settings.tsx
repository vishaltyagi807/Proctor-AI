import { useState } from "react";
import { Globe, Pencil } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/form";
import { Spinner } from "@/components/ui/misc";
import { isNative } from "@/platform/env";
import { saveServerUrl, serverUrl } from "@/platform/server";

function hostOf(url: string): string {
  try {
    return new URL(url).host;
  } catch {
    return url;
  }
}

export function ServerSettings({ onConnected }: { onConnected: (url: string) => void }) {
  const [current, setCurrent] = useState(serverUrl);
  const [editing, setEditing] = useState(() => !serverUrl());
  const [value, setValue] = useState(serverUrl);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  if (!isNative()) return null;

  async function connect(event: React.FormEvent) {
    event.preventDefault();
    setBusy(true);
    setError(null);
    try {
      const url = await saveServerUrl(value);
      setCurrent(url);
      setValue(url);
      setEditing(false);
      onConnected(url);
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "Could not connect to that server.");
    } finally {
      setBusy(false);
    }
  }

  if (!editing) {
    return (
      <div className="mt-6 flex items-center justify-between gap-3 rounded-xl border bg-card/60 px-3.5 py-2.5 text-sm">
        <span className="flex min-w-0 items-center gap-2 text-muted-foreground">
          <Globe className="size-4 shrink-0" />
          <span className="truncate">{hostOf(current)}</span>
        </span>
        <button type="button" onClick={() => setEditing(true)} className="flex shrink-0 items-center gap-1 text-xs font-medium text-primary hover:underline">
          <Pencil className="size-3" /> Change
        </button>
      </div>
    );
  }

  return (
    <form onSubmit={connect} className="mt-6 space-y-3 rounded-xl border bg-card/60 p-4">
      <label className="block space-y-1.5">
        <span className="text-[13px] font-medium">Server address</span>
        <Input
          type="url"
          inputMode="url"
          required
          autoCapitalize="none"
          autoCorrect="off"
          spellCheck={false}
          placeholder="app.example.com"
          className="h-11"
          value={value}
          onChange={(event) => setValue(event.target.value)}
        />
      </label>
      {error && (
        <p className="text-xs text-destructive" role="alert">
          {error}
        </p>
      )}
      <div className="flex gap-2">
        <Button type="submit" disabled={busy || !value.trim()} className="h-10 flex-1">
          {busy ? <Spinner /> : "Connect"}
        </Button>
        {current && (
          <Button
            type="button"
            variant="outline"
            className="h-10"
            onClick={() => {
              setEditing(false);
              setValue(current);
              setError(null);
            }}
          >
            Cancel
          </Button>
        )}
      </div>
    </form>
  );
}
