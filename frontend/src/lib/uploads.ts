import { api } from "./api";
import type { ComplaintFile } from "./types";

type UploadInit = { fileId: string; uploadUrl: string; method: string; headers: Record<string, string> };

const FALLBACK_TYPES: Record<string, string> = {
  png: "image/png", jpg: "image/jpeg", jpeg: "image/jpeg", gif: "image/gif", webp: "image/webp", heic: "image/heic",
  mp4: "video/mp4", webm: "video/webm", mov: "video/quicktime", mp3: "audio/mpeg", wav: "audio/wav", m4a: "audio/mp4",
  pdf: "application/pdf", txt: "text/plain", csv: "text/csv",
  doc: "application/msword", docx: "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
  xls: "application/vnd.ms-excel", xlsx: "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
};

export function contentTypeOf(file: File): string {
  if (file.type) return file.type;
  const extension = file.name.split(".").pop()?.toLowerCase() ?? "";
  return FALLBACK_TYPES[extension] ?? "application/octet-stream";
}

function put(url: string, file: File, headers: Record<string, string>, onProgress: (percent: number) => void): Promise<void> {
  return new Promise((resolve, reject) => {
    const request = new XMLHttpRequest();
    request.open("PUT", url);
    Object.entries(headers).forEach(([name, value]) => {
      if (name.toLowerCase() !== "content-length") request.setRequestHeader(name, value);
    });
    request.upload.onprogress = (event) => event.lengthComputable && onProgress(Math.round((event.loaded / event.total) * 100));
    request.onload = () => (request.status >= 200 && request.status < 300 ? resolve() : reject(new Error(`Upload failed (${request.status})`)));
    request.onerror = () => reject(new Error("Upload failed. Check your connection."));
    request.send(file);
  });
}

export async function uploadComplaintFile(complaintId: string, file: File, onProgress: (percent: number) => void): Promise<ComplaintFile> {
  const init = await api.post<UploadInit>(`/complaints/${complaintId}/files/upload-url`, {
    fileName: file.name,
    contentType: contentTypeOf(file),
    sizeBytes: file.size,
  });
  await put(init.uploadUrl, file, init.headers, onProgress);
  return api.post<ComplaintFile>(`/complaints/${complaintId}/files/${init.fileId}/confirm`);
}
