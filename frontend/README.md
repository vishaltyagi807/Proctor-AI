<div align="center">

# 🎨 ProctorAI Frontend

The web client for **ProctorAI**, built with React 19, TypeScript, and Vite.

![React](https://img.shields.io/badge/React-19-61DAFB?style=flat-square&logo=react&logoColor=black)
![TypeScript](https://img.shields.io/badge/TypeScript-6-3178C6?style=flat-square&logo=typescript&logoColor=white)
![Vite](https://img.shields.io/badge/Vite-8-646CFF?style=flat-square&logo=vite&logoColor=white)
![Tailwind CSS](https://img.shields.io/badge/Tailwind_CSS-4-06B6D4?style=flat-square&logo=tailwindcss&logoColor=white)
![TanStack](https://img.shields.io/badge/TanStack-Router_%2B_Query-FF4154?style=flat-square&logo=reactquery&logoColor=white)

</div>

---

## 🚀 Running

The recommended way to run the frontend is with the full stack from the repository root:

```bash
./setup.sh
```

The app is then served at **http://localhost:5173** with hot reload. The `src/`, `public/`, and config files are mounted into the container, so your edits apply instantly.

### Standalone

To run the frontend outside Docker against a running backend:

```bash
npm ci
cp .env.example .env
npm run dev
```

| Script | Description |
|---|---|
| `npm run dev` | Start the Vite dev server |
| `npm run build` | Type-check and build for production |
| `npm run preview` | Preview the production build |
| `npm run lint` | Run ESLint |

---

## ⚙️ Environment

| Variable | Default | Purpose |
|---|---|---|
| `API_URL` | `http://localhost:7050` | API gateway that the dev and preview proxy forwards to |
| `VITE_API_URL` | `/api` | API base path used by the browser |

Vite proxies `/api/*` to the gateway, strips the `/api` prefix, and rewrites the auth cookie path so that refresh-token cookies keep working through the proxy.

---

## 📁 Structure

```
src/
├── main.tsx              # app entry point
├── router.tsx            # TanStack Router setup
├── routes/               # route definitions
├── pages/                # page-level layouts
├── components/
│   ├── ui/               # shadcn/ui primitives
│   ├── layout/           # app shell, sidebar, command palette, notifications
│   ├── users/            # user table, forms, CSV import
│   ├── roles/            # roles and the permission matrix
│   ├── departments/
│   ├── complaints/       # complaint list, detail, uploads
│   ├── faces/            # enrollment and live recognition
│   ├── custom-fields/
│   ├── notifications/
│   ├── dashboard/
│   └── system/           # system monitor
├── lib/                  # API client, session, permissions, queries, streams
└── hooks/                # reusable React hooks
```

---

## 🧰 Stack

- **Routing and data:** TanStack Router, TanStack Query
- **Styling:** Tailwind CSS 4, shadcn/ui, Base UI, tw-animate-css
- **UI:** Lucide icons, cmdk command palette, Recharts, Motion, Embla Carousel, react-day-picker
- **Fonts:** Inter, Geist Mono, JetBrains Mono

---

## 📱 Desktop and Mobile Apps

The same frontend ships as a web app and as native apps for **Windows, macOS, Linux, Android and iOS** through [Tauri 2](https://v2.tauri.app). Everything native lives in `src/platform/`, and the web build compiles it out entirely (`vite build` contains no Tauri code).

Check that your machine has everything these builds need with `./native-doctor.sh` from the repository root. Add `desktop`, `android` or `ios` to check one target. It changes nothing and prints the install command for anything missing.

| Command | Builds |
|---|---|
| `npm run tauri:dev` | Desktop app against the local dev stack |
| `npm run tauri:build` | Desktop installers for the current OS |
| `npm run tauri:android:init` then `npm run tauri:android:dev` | Android project and dev build (adds the camera permission automatically) |
| `npm run tauri:ios:init` then `npm run tauri:ios:dev` | iOS project and dev build (macOS with Xcode only) |

Set `VITE_SERVER_URL=https://app.example.com` when building to preconnect the app to your server. Without it, the sign-in screen asks for the server address and checks it before saving.

**How it works on each platform**

| Area | Web | Native apps |
|---|---|---|
| Sign-in | HTTP-only cookies | Bearer tokens. The refresh token is kept in the app store and rotated on every refresh |
| API calls | `fetch` to `/api` | Tauri HTTP client (no CORS preflights; works before DNS and HTTPS are set up) |
| Large uploads | `fetch` / XHR | Webview `fetch` / XHR with the bearer token, with upload progress |
| Live updates | Server-sent events over `fetch` | The same, streamed through the Tauri HTTP client |
| Downloads | Browser download | Native save dialog with a "saved" notice on desktop, the system opener on phones |
| Notifications | In-app toasts | Toasts, plus OS notifications while the app is in the background |
| Deep links | — | `proctorai://complaints/<id>` opens that page; a second launch focuses the running window |
| Camera | `getUserMedia` | `getUserMedia`, with camera permission declared for macOS, iOS and Android |
| Safe areas | `env(safe-area-inset-*)` | The same, with `viewport-fit=cover` for notches, rounded corners and system bars |

**Plugins:** http, notification, upload, fs, dialog, deep-link, opener, store, os and log, plus single-instance and window-state on desktop. Permissions are limited to what the app uses (`src-tauri/capabilities/default.json`), and a strict CSP allows scripts only from the app itself.

Setup adds `tauri://localhost`, `http://tauri.localhost` and `https://tauri.localhost` to the allowed origins automatically, so the apps work against any ProctorAI server set up with `./setup.sh`. File uploads from the apps need the HTTPS domain, because Android blocks plain-HTTP uploads in release builds.

---

## 🎨 Brand and Icons

The logo is a guardian shield with an AI eye inside face-scan brackets. The master artwork lives in `brand/` as SVG:

| File | Used for |
|---|---|
| `proctorai-icon.svg` | Master icon: Windows, Linux, Android legacy, Microsoft Store tiles, web 192/512 |
| `proctorai-icon-small.svg` | Simplified mark for 16–48 px: favicon, small `.ico` sizes, the in-app logo |
| `proctorai-icon-macos.svg` | macOS icon, inset to Apple's Dock grid with its shadow |
| `proctorai-icon-maskable.svg` | Full-bleed square for iOS, the Apple touch icon and maskable web icons |
| `proctorai-android-*.svg` | Android adaptive icon layers (background, foreground, monochrome) |

Run `npm run icons` after changing any of them. It regenerates `src-tauri/icons` (desktop, Android, iOS) and the web icons in `public/` (`favicon.svg`, `favicon.ico`, `apple-touch-icon.png`, PWA icons). Run it again after `tauri android init` or `tauri ios init`.

---

## 🐳 Docker

The `Dockerfile` has three targets:

| Target | Use |
|---|---|
| `dev` | Vite dev server on port 5173 |
| `build` | Production bundle |
| `prod` | Nginx serving the bundle on port 80, with a `/healthz` health check |

`src-tauri` and `scripts` are excluded from the Docker context, so the web image never contains native app code.

For the production stack, run `./setup.sh prod` from the repository root.
