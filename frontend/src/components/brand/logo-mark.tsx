import { useId } from "react";
import { cn } from "@/lib/utils";

export function LogoMark({ className, title = "ProctorAI" }: { className?: string; title?: string }) {
  const id = useId().replace(/[^a-zA-Z0-9_-]/g, "");
  const tile = `${id}-tile`;
  const glow = `${id}-glow`;
  const ember = `${id}-ember`;
  const shield = `${id}-shield`;
  const eye = `${id}-eye`;
  const clip = `${id}-clip`;
  return (
    <svg viewBox="0 0 1024 1024" role="img" aria-label={title} className={cn("shrink-0", className)}>
      <defs>
        <linearGradient id={tile} x1="96" y1="64" x2="928" y2="960" gradientUnits="userSpaceOnUse">
          <stop offset="0" stopColor="#6a5cff" />
          <stop offset="0.52" stopColor="#5851f8" />
          <stop offset="1" stopColor="#c35dd9" />
        </linearGradient>
        <radialGradient id={glow} cx="230" cy="170" r="560" gradientUnits="userSpaceOnUse">
          <stop offset="0" stopColor="#3cbbf9" stopOpacity="0.55" />
          <stop offset="1" stopColor="#3cbbf9" stopOpacity="0" />
        </radialGradient>
        <radialGradient id={ember} cx="860" cy="900" r="420" gradientUnits="userSpaceOnUse">
          <stop offset="0" stopColor="#ff8be0" stopOpacity="0.45" />
          <stop offset="1" stopColor="#ff8be0" stopOpacity="0" />
        </radialGradient>
        <linearGradient id={shield} x1="512" y1="150" x2="512" y2="914" gradientUnits="userSpaceOnUse">
          <stop offset="0" stopColor="#ffffff" />
          <stop offset="1" stopColor="#e9e6ff" />
        </linearGradient>
        <linearGradient id={eye} x1="292" y1="430" x2="732" y2="610" gradientUnits="userSpaceOnUse">
          <stop offset="0" stopColor="#3cbbf9" />
          <stop offset="0.45" stopColor="#5851f8" />
          <stop offset="1" stopColor="#c35dd9" />
        </linearGradient>
        <clipPath id={clip}>
          <rect width="1024" height="1024" rx="232" />
        </clipPath>
      </defs>
      <g clipPath={`url(#${clip})`}>
        <rect width="1024" height="1024" fill={`url(#${tile})`} />
        <rect width="1024" height="1024" fill={`url(#${glow})`} />
        <rect width="1024" height="1024" fill={`url(#${ember})`} />
      </g>
      <path
        fill={`url(#${shield})`}
        d="M512 150C606 214 716 248 816 256Q856 260 856 300V508C856 704 722 846 512 914C302 846 168 704 168 508V300Q168 260 208 256C308 248 418 214 512 150Z"
      />
      <path fill={`url(#${eye})`} d="M292 520C380 386 644 386 732 520C644 654 380 654 292 520Z" />
      <circle cx="512" cy="520" r="92" fill="#ffffff" />
      <circle cx="512" cy="520" r="54" fill="#3321a8" />
    </svg>
  );
}
