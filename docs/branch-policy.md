# Branch Policy

## Active

| Branch | Role |
|---|---|
| `pseudo-pflow-v3-dev-test` | Active development — feature work, debugging, batch refactors |
| `main` / `master` | Reserved for stable release snapshots; not yet cut |

## Remote

```
origin → https://github.com/wattwong103/Pseudo-PFLOW-North
```

## Rules

1. **Never push directly to `main` or `master`** — reserved for release snapshots.
2. **Rebase, don't merge**, to keep linear history:
   ```bash
   git pull --rebase origin <branch>
   ```
3. **Avoid force pushes.** They overwrite remote history; only acceptable on
   transient feature branches you own exclusively, never on shared branches.
4. **All branches push to `origin`** for backup and cross-machine sync (see
   [`team-dev-flow.md`](team-dev-flow.md)).

## Naming convention for new branches

| Prefix | Use |
|---|---|
| `feature/` | New experimental features |
| `bugfix/` | Fixes to existing functionality |
| `refactor/` | Structural / design refactoring |
| `hotfix/` | Urgent fixes |

## Tagging

Per-batch checkpoints use `b<n>-YYYY-MM-DD` (e.g., `b7-2026-04-27`). Tags push
to `origin` alongside their branch.
