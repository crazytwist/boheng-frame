# boheng-frame Constitution

## Core Principles

### I. Module boundaries (NON-NEGOTIABLE)

- Cross-module calls use `*-api` contracts only (`wms-api`, `device-api`, future `flow-api`).
- `boheng-module-flow` must not depend on `boheng-module-wms` implementation; WMS exposes `SampleApi`, `MaterialInstanceExternalApi`, etc.
- Keep changes scoped to the modules a feature touches.

### II. Orchestration truth source

- Run lifecycle: `flow_execution` / `flow_step_execution` (RUNNING, SUCCESS, FAILED, CANCELLED).
- Device queue: `flow_device_task` (PENDING → CLAIMED → DONE/FAILED); completion advances via `FlowSignalDispatcher`.
- External protocols (e.g. Tsinghua taskId/Status) live in app controllers + assemblers, not in core enums unless shared.

### III. Full-stack contract sync

- HTTP changes require the same PR/task to update: Java VO/Controller, `web/src/api/**`, and affected Vue views.
- App machine APIs: document `tenant-id`; `@PermitAll` only where intended.
- Prefer dict + `labels.ts` over hardcoded business enums in UI.

### IV. Small diffs & existing style

- Match surrounding naming, layering, and error codes (`ErrorCodeConstants`).
- No drive-by refactors; no committing secrets (`.env*`, credentials).
- `boheng-module-device` Java: Chinese comments on classes, fields, and methods (`device-java-comments` skill).

### V. Spec-driven features

- Non-trivial features: `/speckit-specify` → `/speckit-plan` → `/speckit-tasks` → `/speckit-implement` → `/speckit-converge` (or project skill `boheng-fullstack-feature`).
- Pre-merge: `boheng-pre-commit-review`; app-api changes get security-minded review.

## Technology

- Backend: Java, Spring, MyBatis-Plus, multi-tenant.
- Frontend: Vue3, Element Plus under `web/`.
- Optional local API port via `web/.env.local` (not committed with secrets).

## Governance

- This constitution guides Spec Kit plans and `.cursor/rules`.
- Amendments: update this file + note in PR description.

**Version**: 1.0.0 | **Ratified**: 2026-10-21 | **Last Amended**: 2026-10-21
