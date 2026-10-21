# Assessly

Assessly is an online assessment platform planned around a Spring Boot API, Kafka event workflows, Redis-backed exam state, and a React candidate/teacher interface.

## Repository layout

- `frontend/` — React, TypeScript, and Vite application. The current milestone uses deterministic session-local adapters.
- `docs/` — backend architecture, API contracts, data, messaging, reliability, and delivery documentation.

## Frontend development

```bash
cd frontend
npm install
npm run dev
```

Validation commands:

```bash
npm run typecheck
npm run lint
npm run test
npm run build
```

Copy `frontend/.env.example` to `frontend/.env` only when local configuration is required. Never put secrets in Vite environment variables; values prefixed with `VITE_` are bundled for the browser.

## Delivery workflow

Changes should be split into focused commits such as repository hygiene, security/configuration, frontend behavior, and tests. Keep generated directories and local configuration out of commits.
