# DevPlane

A small internal developer platform: service catalog, golden-path templates,
deployment, observability, and AI-assisted operations. It's a personal portfolio
project; README.md has the vision and the phased roadmap.

## Commands

Docker must be running for tests and `bootRun`.

- `./gradlew build`: compile, run all tests, and package. Must pass before a task
  is checked off.
- `./gradlew test --tests '*ClassNameTests'`: run one test class.
- `./gradlew bootRun`: start the app on http://localhost:8080. Spring Boot starts
  PostgreSQL from `compose.yaml`.

## Spec-driven development with OpenSpec

Behavior is agreed in specs before code is written.

- `openspec/specs/` is the source of truth for current behavior. Its requirements
  change only when a change is archived, never by direct edits.
- `openspec/changes/<name>/` holds work in progress: proposal, delta specs, design,
  and tasks. `openspec/config.yaml` holds the project context and artifact rules.
- Workflow: `/opsx:explore` (optional) → `/opsx:propose` → user review →
  `/opsx:apply` → `/opsx:verify` → `/opsx:archive`.

Rules:

- Any change to observable behavior starts with a change proposal. Fixes with no
  behavior change (typos, dependency bumps, pure refactors) can skip it.
- After proposing, stop. Implementation starts only when the user asks for it.
- Implement only what the change's specs and tasks describe. If they turn out to be
  wrong, update the artifacts (`/opsx:update`) before writing more code.
- After editing anything under `openspec/`, run `openspec validate --all --strict`.
  CI runs the same check.
