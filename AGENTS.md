# Agent guide (boheng-frame)

Cursor Agent + Spec Kit are configured for **full-stack** work (Java modules + `web/`).

## Rules (auto-applied)

- `.cursor/rules/boheng-core.mdc` — always on
- `.cursor/rules/boheng-java-backend.mdc` — `boheng-module-*/**/*.java`
- `.cursor/rules/boheng-web-frontend.mdc` — `web/**/*.{vue,ts}`
- `.cursor/rules/boheng-api-sync.mdc` — API + frontend touch sets

## Project skills

| Skill | Use when |
|-------|----------|
| `boheng-fullstack-feature` | New feature spanning backend + web |
| `boheng-pre-commit-review` | Before commit / PR |
| `device-java-comments` | Editing `boheng-module-device` Java |
| `speckit-*` | Spec Kit SDD workflow (see below) |

## Spec Kit (SDD)

Constitution: `.specify/memory/constitution.md`

Typical sequence in chat:

1. `/speckit-constitution` (once, or when amending principles)
2. `/speckit-specify` — what to build
3. `/speckit-clarify` (optional)
4. `/speckit-plan` — technical plan
5. `/speckit-tasks` → `/speckit-analyze` (optional)
6. `/speckit-implement`
7. `/speckit-converge` until converged

Artifacts under `.specify/` (per feature). CLI: `specify` (via `uv tool install specify-cli`).

## Review

- Bug-focused: Bugbot subagent on uncommitted changes
- Security: Security Review when changing app-api / tenant / PermitAll
