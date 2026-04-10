---
paths:
  - "**/*.md"
  - "docs/**"
  - "report/**"
---

# Markdown & Documentation Rules — PFLOW Project

## Obsidian Conventions (for vault notes at H:\Dropbox\obsidian-vault\)
- Use Obsidian-flavored markdown with `[[wikilinks]]` for internal note connections
- Every note MUST have YAML frontmatter: `type`, `tags`, `date`, `status`
- Valid types: project, reference, note, decision, log
- Valid statuses: active, draft, archived, completed
- Filenames: kebab-case (e.g., `pflow-refactoring-plan.md`)
- One concept per note (atomic notes principle)
- Add `related:` field in frontmatter for explicit connections beyond wikilinks
- PFLOW notes live in `wiki/` (migrated from Projects/pflow/)
- Raw captures go in `raw/{papers,dev,data}/` with `compiled: false` frontmatter
- Reports go in `reports/{summaries,weekly}/`

## Weekly Reports
- Format: `report/YYYY-WNN.md` (ISO week)
- Each working day gets a `## Day` section header
- Sunday includes a `## Weekly Summary`
- Append the day's work at end of each session

## LaTeX Documentation
- Use `xelatex` for `docs/truck_abm.tex` and `docs/taxi_abm.tex` (Japanese characters)
- Both papers compile with 0 errors, 0 overfull warnings — maintain this standard

## CLAUDE.md Files
- `PFLOW/CLAUDE.md` — top-level project context (loaded automatically by Claude Code)
- `Pseudo-PFLOW/CLAUDE.md` — code-level architecture reference
- Do not duplicate information between them — top-level is for overview, code-level is for implementation details
