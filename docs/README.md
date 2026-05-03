# Pseudo-PFLOW Code-Level Documentation

Human-friendly summaries of this codebase. The exhaustive code-level
architecture reference (auto-loaded by Claude Code) is `Pseudo-PFLOW/CLAUDE.md`.

## Index

| Document | Topic |
|---|---|
| [`architecture.md`](architecture.md) | Module map: Truck ABM, Taxi ABM, Trajectory generator, People-flow ABM |
| [`config-schema.md`](config-schema.md) | Layout of `config/` and the `${PFLOW_HOME}` token convention |
| [`pipeline.md`](pipeline.md) | Repo-root pipeline scripts and the `native/` build chain |
| [`branch-policy.md`](branch-policy.md) | Active branch + remote, branch-prefix conventions |
| [`team-dev-flow.md`](team-dev-flow.md) | Solo cross-platform Git workflow (Windows + macOS via Dropbox) |

## Scope

These docs describe files **only under `Pseudo-PFLOW/`**. Input data
(`${PFLOW_HOME}/data/...`), companion scripts (`${PFLOW_HOME}/scripts/...`),
and the journal/internal LaTeX papers (`${PFLOW_HOME}/docs/...`) live outside
this Git repository and are referenced by env-var path only.
