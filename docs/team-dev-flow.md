# Cross-Platform Development Workflow

This codebase is developed solo across two machines (Windows and macOS), with
Dropbox handling the file-level sync between them.

## Machines

| Platform | Working tree | Override |
|---|---|---|
| Windows | `H:/Dropbox/PFLOW/Pseudo-PFLOW` | `set PFLOW_HOME=<path>` |
| macOS / Linux | `~/Dropbox/PFLOW/Pseudo-PFLOW` | `export PFLOW_HOME=<path>` |

`PFLOW_HOME` is auto-detected when unset (see [`config-schema.md`](config-schema.md)).

## Daily Git workflow

```bash
# Start of session — sync remote first, in case the other machine pushed
git checkout pseudo-pflow-v3-dev-test
git pull --rebase origin pseudo-pflow-v3-dev-test

# Work...

# End of session
git add <files>
git commit -m "B<n>: <subject>"
git push origin pseudo-pflow-v3-dev-test
```

## Cross-machine handoff

Two viable patterns:

1. **Commit-and-push handoff** (preferred — safest).
   Push at end of session on machine A, `git pull --rebase` first thing on
   machine B. Dropbox sync of working-tree edits is *not* the source of truth;
   `origin` is.

2. **Stash for in-flight work.**
   ```bash
   git stash push -m "wip: <topic>"
   # ...later, on the other machine after pull...
   git stash pop
   ```

Avoid letting Dropbox sync uncommitted edits across machines — it can produce
"Selective Sync Conflict" copies that look like real files but bypass Git's
diff machinery.

## Conflict resolution

```bash
git status                # see conflicted files
# Manually resolve, then:
git add <resolved-file>
git rebase --continue
```

If a rebase gets confusing, abort and try again:

```bash
git rebase --abort
```

## Commit-message convention

Recent history uses a `B<n>: <subject>` prefix for batch work
(e.g. `B7: Scientific diagnostic + targeted fixes`) and `chore:` / `feat:` /
`fix:` prefixes for non-batch work. Match the existing style.

## LOCAL-ONLY directories

Several working-tree directories are gitignored and machine-local — do not
expect Dropbox to keep them coherent across machines:

- `bin/`, `bin_fresh/` — compiled `.class` output (regenerable; see [`pipeline.md`](pipeline.md)).
- `output/`, `logs/` — runtime artifacts.
- `target/` — Maven scratch.

If Dropbox produces "Selective Sync Conflict" copies of any of these, treat
them as disposable and delete after a quick check that no unique data was
trapped.
