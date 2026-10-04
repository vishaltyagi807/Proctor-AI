import { useCallback, useEffect, useRef, useState } from "react";
import { Camera, CameraOff, Radar } from "lucide-react";
import { api, ApiError } from "@/lib/api";
import { useSession } from "@/components/providers/session";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Spinner } from "@/components/ui/misc";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { numberFaces } from "@/lib/faces";
import { FaceList, FaceOverlay } from "./face-result";
import type { FaceRecognizeResult } from "@/lib/types";
import { isNative } from "@/platform/env";

const SCAN_GAP_MS = 1000;
const RETRY_MS = 5000;
const MAX_FRAME_WIDTH = 960;

export function LivePanel() {
  const { can } = useSession();
  const videoRef = useRef<HTMLVideoElement>(null);
  const streamRef = useRef<MediaStream | null>(null);
  const [active, setActive] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [result, setResult] = useState<FaceRecognizeResult | null>(null);
  const [latency, setLatency] = useState<number | null>(null);
  const [aspect, setAspect] = useState(16 / 9);
  const [highlight, setHighlight] = useState<string | null>(null);

  const stop = useCallback(() => {
    streamRef.current?.getTracks().forEach((track) => track.stop());
    streamRef.current = null;
    if (videoRef.current) videoRef.current.srcObject = null;
    setActive(false);
    setResult(null);
    setLatency(null);
    setHighlight(null);
  }, []);

  const start = async () => {
    setError(null);
    try {
      const stream = await navigator.mediaDevices.getUserMedia({ video: { facingMode: "user", width: { ideal: 1280 }, height: { ideal: 720 } }, audio: false });
      streamRef.current = stream;
      const video = videoRef.current;
      if (video) {
        video.srcObject = stream;
        await video.play();
        if (video.videoWidth > 0 && video.videoHeight > 0) setAspect(video.videoWidth / video.videoHeight);
      }
      setActive(true);
    } catch (cause) {
      setError(
        cause instanceof DOMException && cause.name === "NotAllowedError"
          ? `Camera access was denied. Allow it in your ${isNative() ? "device" : "browser"} settings to continue.`
          : "No camera could be started on this device.",
      );
    }
  };

  useEffect(() => {
    if (!active) return;
    let cancelled = false;
    let timer: number | undefined;
    const canvas = document.createElement("canvas");

    const next = (delay: number) => {
      if (!cancelled) timer = window.setTimeout(() => void scan(), delay);
    };

    const scan = async () => {
      const video = videoRef.current;
      if (!video || video.videoWidth === 0 || document.visibilityState === "hidden") {
        next(SCAN_GAP_MS);
        return;
      }
      const started = performance.now();
      const scale = Math.min(1, MAX_FRAME_WIDTH / video.videoWidth);
      canvas.width = Math.round(video.videoWidth * scale);
      canvas.height = Math.round(video.videoHeight * scale);
      canvas.getContext("2d")?.drawImage(video, 0, 0, canvas.width, canvas.height);
      const image = canvas.toDataURL("image/jpeg", 0.8).split(",")[1];
      try {
        const data = await api.post<FaceRecognizeResult>("/faces/recognize", { image, source: "camera" });
        if (cancelled) return;
        const took = performance.now() - started;
        setResult(data);
        setLatency(Math.round(took));
        setError(null);
        next(Math.max(0, SCAN_GAP_MS - took));
      } catch (cause) {
        if (cancelled) return;
        if (cause instanceof ApiError && (cause.status === 401 || cause.status === 403)) {
          setError(cause.message);
          stop();
          return;
        }
        setError(`${cause instanceof Error ? cause.message : "Recognition failed"}. Retrying in ${RETRY_MS / 1000} seconds.`);
        next(RETRY_MS);
      }
    };

    void scan();
    return () => {
      cancelled = true;
      window.clearTimeout(timer);
    };
  }, [active, stop]);

  useEffect(() => stop, [stop]);

  const faces = result ? numberFaces(result.faces) : [];

  return (
    <div className="grid gap-6 xl:grid-cols-[minmax(0,1fr)_420px] [&>*]:min-w-0">
      <Card>
        <CardHeader>
          <div>
            <CardTitle>Live camera</CardTitle>
            <CardDescription>
              {can("face_live_recognition", "read", ["all"])
                ? "Everyone who is enrolled can be identified."
                : "Only people in your departments are identified. Anyone else is shown as unknown."}
            </CardDescription>
          </div>
          {active && (
            <span className="flex items-center gap-1.5 text-xs font-medium text-destructive">
              <span className="size-2 animate-pulse rounded-full bg-destructive" /> Live
            </span>
          )}
        </CardHeader>
        <CardContent className="space-y-4">
          <div className="relative overflow-hidden rounded-2xl border bg-muted" style={{ aspectRatio: aspect }}>
            <video
              ref={videoRef}
              muted
              playsInline
              onLoadedMetadata={(event) => {
                const video = event.currentTarget;
                if (video.videoWidth > 0 && video.videoHeight > 0) setAspect(video.videoWidth / video.videoHeight);
              }}
              className="absolute inset-0 size-full -scale-x-100 object-cover"
            />
            {active && result && (
              <FaceOverlay faces={faces} width={result.width} height={result.height} mirrored animate active={highlight} onActive={setHighlight} />
            )}
            {!active && (
              <div className="absolute inset-0 grid place-items-center text-center text-muted-foreground">
                <div className="space-y-2">
                  <Camera className="mx-auto size-8" />
                  <p className="text-sm">The camera is off</p>
                </div>
              </div>
            )}
            {active && !result && (
              <div className="absolute right-3 top-3 flex items-center gap-1.5 rounded-full bg-background/80 px-2.5 py-1 text-xs backdrop-blur">
                <Spinner /> Starting recognition
              </div>
            )}
          </div>
          {error && (
            <Alert variant="destructive">
              <CameraOff />
              <AlertTitle>Camera problem</AlertTitle>
              <AlertDescription>{error}</AlertDescription>
            </Alert>
          )}
          <div className="flex flex-wrap items-center gap-3">
            {active ? (
              <Button variant="outline" className="h-11" onClick={stop}>
                <CameraOff /> Stop camera
              </Button>
            ) : (
              <Button className="h-11 gradient-brand text-white shadow-lg shadow-primary/25" onClick={start}>
                <Camera /> Start camera
              </Button>
            )}
            <p className="ml-auto text-right text-xs text-muted-foreground">
              Faces are recognized automatically about once a second{latency !== null && ` · last frame ${latency} ms`}
              <span className="block">Each person is written to the activity log at most once a minute</span>
            </p>
          </div>
        </CardContent>
      </Card>

      <Card className="self-start">
        <CardHeader>
          <div>
            <CardTitle>Who&apos;s in view</CardTitle>
            <CardDescription>{active ? "Numbers match the boxes on the camera. Point at either to link them." : "Start the camera to identify people"}</CardDescription>
          </div>
          <Radar className="size-4 text-muted-foreground" />
        </CardHeader>
        <CardContent>
          {active && result ? (
            <FaceList faces={faces} emptyText="Nobody is in view right now." active={highlight} onActive={setHighlight} />
          ) : (
            <p className="text-sm text-muted-foreground">{active ? "Looking for faces…" : "Start the camera to see who is in view."}</p>
          )}
        </CardContent>
      </Card>
    </div>
  );
}
