# Assessly Frontend

Assessly is a React + TypeScript + Vite frontend for the online assessment platform described in `../docs/implementation_contract.md`.

## Run locally

```bash
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

The current implementation uses deterministic, session-local mock adapters. It does not claim persistence, live telemetry, or backend security. The service boundary in `src/services/mock.ts` is the replacement point for the future Spring Boot HTTP/STOMP adapter.

Demo roles are selected on the login screen:

- Student / Candidate: `student@assessly.demo` / `password`
- Teacher / Admin: any email / `password` after selecting the teacher role

Coding questions and Google OAuth are intentionally unavailable in this first release.
