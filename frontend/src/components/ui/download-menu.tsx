import { ChevronDown, Download, FileJson, FileSpreadsheet, FileText } from "lucide-react";
import { Button } from "@/components/ui/button";
import { DropdownMenu, DropdownMenuContent, DropdownMenuGroup, DropdownMenuItem, DropdownMenuLabel, DropdownMenuTrigger } from "@/components/ui/dropdown-menu";
import { useToast } from "@/components/ui/toast";
import { apiResponse, apiUrl } from "@/lib/api";
import { isNative } from "@/platform/env";
import { revealFile, saveResponse } from "@/platform/files";

const FORMATS = [
  { value: "csv", label: "CSV", icon: FileText },
  { value: "xlsx", label: "Excel", icon: FileSpreadsheet },
  { value: "json", label: "JSON", icon: FileJson },
] as const;

async function downloadFile(path: string, format: string): Promise<string | null> {
  if (!isNative()) {
    const link = document.createElement("a");
    link.href = apiUrl(`${path}?format=${format}`);
    link.rel = "noopener";
    document.body.appendChild(link);
    link.click();
    link.remove();
    return null;
  }
  const response = await apiResponse(`${path}?format=${format}`);
  return saveResponse(response, `template.${format}`);
}

export function TemplateDownloadMenu({ path, label = "Download template", className }: { path: string; label?: string; className?: string }) {
  const toast = useToast();

  const download = async (format: string) => {
    try {
      const saved = await downloadFile(path, format);
      if (saved) {
        toast.success("Template saved", saved);
        void revealFile(saved).catch(() => undefined);
      }
    } catch (cause) {
      toast.error("Download failed", cause instanceof Error ? cause.message : undefined);
    }
  };

  return (
    <DropdownMenu>
      <DropdownMenuTrigger render={<Button type="button" variant="outline" size="sm" className={className} />}>
        <Download /> {label} <ChevronDown className="opacity-60" />
      </DropdownMenuTrigger>
      <DropdownMenuContent align="end" className="w-44">
        <DropdownMenuGroup>
          <DropdownMenuLabel>Template format</DropdownMenuLabel>
          {FORMATS.map((format) => (
            <DropdownMenuItem key={format.value} onClick={() => void download(format.value)}>
              <format.icon /> {format.label}
            </DropdownMenuItem>
          ))}
        </DropdownMenuGroup>
      </DropdownMenuContent>
    </DropdownMenu>
  );
}
