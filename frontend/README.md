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

## 🐳 Docker

The `Dockerfile` has three targets:

| Target | Use |
|---|---|
| `dev` | Vite dev server on port 5173 |
| `build` | Production bundle |
| `prod` | Nginx serving the bundle on port 80, with a `/healthz` health check |

For the production stack, run `./setup.sh prod` from the repository root.
