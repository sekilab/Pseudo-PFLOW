---
name: daily-context
description: Loads today's context — recent changes, current priorities, and relevant vault notes. Use when user starts a session, says good morning, or asks what to work on.
user-invocable: true
allowed-tools: "Read Bash Glob Grep"
---

# Daily Context — PFLOW

Load session context for today.

## Steps

1. **Recent git activity** — Show last 10 commits:
   ```bash
   cd H:/Dropbox/PFLOW && git log --oneline -10 --format="%h %s (%cr)"
   ```

2. **Uncommitted changes** — Check working tree:
   ```bash
   cd H:/Dropbox/PFLOW && git status --short
   ```

3. **Current priorities** — Read the project CLAUDE.md:
   ```
   Read: H:\Dropbox\PFLOW\CLAUDE.md (first 30 lines for priorities)
   ```

4. **Recent vault notes** — Find notes modified in the last 7 days:
   ```bash
   find "H:/Dropbox/obsidian-vault/wiki/" -name "*.md" -mtime -7 -type f 2>/dev/null
   find "H:/Dropbox/obsidian-vault/raw/" -name "*.md" -mtime -7 -type f 2>/dev/null
   find "H:/Dropbox/obsidian-vault/reports/" -name "*.md" -mtime -7 -type f 2>/dev/null
   ```

5. **Weekly report** — Check current week's report:
   ```
   Glob: H:\Dropbox\PFLOW\report\2026-W*.md (most recent)
   ```
   Read the latest one for context on recent work.

6. **Refactoring plan** — Check top pending items:
   ```
   Read: H:\Dropbox\obsidian-vault\wiki\pflow-refactoring-plan.md
   ```
   Look at the Priority Matrix section for next actions.

## Output Format

Summarize in this structure:
```
## Session Context — YYYY-MM-DD

### Recent Work
- [bullet list of recent commits/changes]

### In Progress
- [uncommitted changes, if any]

### Priorities
- [from CLAUDE.md and refactoring plan]

### Suggested Next Actions
1. [most impactful pending item]
2. [second priority]
3. [third priority]
```

Keep it concise — this is a session opener, not a full report.
