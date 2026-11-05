# ProctorAI Documentation

The complete documentation set for ProctorAI 1.1.0, written in Markdown and published as PDFs.

**Creator & Developer:** Varshit Tyagi & Mohini Teotia

## Documents

| # | Document | ID | Audience | PDF |
|---|---|---|---|---|
| — | Documentation Handbook (all documents in one) | PAI-HB-000 | Everyone | [pdf](pdf/ProctorAI-Documentation-Handbook.pdf) |
| 01 | Software Requirements Specification | PAI-SRS-001 | Product, engineering, QA | [pdf](pdf/01-ProctorAI-Software-Requirements-Specification.pdf) |
| 02 | Architecture Document | PAI-ARC-002 | Engineering | [pdf](pdf/02-ProctorAI-Architecture-Document.pdf) |
| 03 | API Handbook | PAI-API-003 | Frontend, mobile, integrators | [pdf](pdf/03-ProctorAI-API-Handbook.pdf) |
| 04 | Database & Migrations | PAI-DB-004 | Backend, DBA | [pdf](pdf/04-ProctorAI-Database-and-Migrations.pdf) |
| 05 | Security & Hardening Guide | PAI-SEC-005 | Security, operations | [pdf](pdf/05-ProctorAI-Security-and-Hardening.pdf) |
| 06 | Production Deployment Runbook | PAI-DEP-006 | Operations | [pdf](pdf/06-ProctorAI-Production-Deployment-Runbook.pdf) |
| 07 | Configuration Reference | PAI-CFG-007 | Operations, engineering | [pdf](pdf/07-ProctorAI-Configuration-Reference.pdf) |
| 08 | Backup, Restore & Disaster Recovery Plan | PAI-BDR-008 | Operations | [pdf](pdf/08-ProctorAI-Backup-Restore-and-Disaster-Recovery.pdf) |
| 09 | Monitoring & Incident Runbook | PAI-OPS-009 | Operations, on-call | [pdf](pdf/09-ProctorAI-Monitoring-and-Incident-Runbook.pdf) |
| 10 | Release, Versioning & Rollback Guide | PAI-REL-010 | Maintainers | [pdf](pdf/10-ProctorAI-Release-Versioning-and-Rollback.pdf) |
| 11 | CI/CD Pipeline Design | PAI-CICD-011 | Maintainers, DevOps | [pdf](pdf/11-ProctorAI-CICD-Pipeline-Design.pdf) |
| 12 | Contributor & Testing Guide | PAI-DEV-012 | Developers | [pdf](pdf/12-ProctorAI-Contributor-and-Testing-Guide.pdf) |
| 13 | User & Administrator Manual | PAI-USR-013 | Students, staff, administrators | [pdf](pdf/13-ProctorAI-User-and-Administrator-Manual.pdf) |

## Suggested reading order

| Goal | Read |
|---|---|
| Deploy to production | 06 → 07 → 05 → 08 → 09 |
| Understand the system | 01 → 02 → 04 → 03 |
| Contribute code | 12 → 02 → 04 → 03 → 11 |
| Release a version | 10 → 11 |
| Use the application | 13 |

## Layout

```
docs/
├── src/          Markdown sources, one file per document
├── theme/        Stylesheet and logo used for the PDFs
├── pdf/          Generated PDFs
├── build.mjs     Renders src/*.md to HTML and prints PDFs with headless Chrome
└── package.json
```

## Building the PDFs

Requires Node.js 20+ and Google Chrome or Chromium (set `CHROME_PATH` if it is not in a standard location). Internet access is used for the Inter and JetBrains Mono web fonts and the Mermaid diagram renderer.

```bash
cd docs
npm install
npm run build                    # all documents and the combined handbook
node build.mjs --only=03-api     # a single document
npm run build:html               # HTML only, written to docs/html/
```

Each Markdown file starts with front matter (`title`, `subtitle`, `id`, `version`, `category`, `audience`, `classification`, `pdf`, `related`). Callouts use `> [!NOTE]`, `> [!TIP]`, `> [!IMPORTANT]`, `> [!WARNING]` and `> [!CAUTION]`. Diagrams use fenced `mermaid` blocks.

© 2026 Varshit Tyagi & Mohini Teotia. Released under the MIT License.
