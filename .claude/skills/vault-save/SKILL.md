---
name: vault-save
description: Saves insights, decisions, or session learnings to the Obsidian vault. Use when user asks to save, note down, remember, log, or record something to the vault.
argument-hint: "[topic-or-content]"
user-invocable: true
allowed-tools: "Read Write Glob"
---

# Vault Save

Save `$ARGUMENTS` to the Obsidian vault at `H:\Dropbox\obsidian-vault\`.

## Determine Location

Based on the content type, choose the right folder:
- **Project-specific** (PFLOW code, simulation, data) → `raw/dev/` (as raw note with compiled: false)
- **Decision or ADR** → `wiki/pflow-decisions.md` (append)
- **Wiki concept** → `wiki/` (if it's a well-formed concept with definition and insights)
- **Ongoing area** (career, learning) → `Areas/`
- **Reference material** → `Resources/`
- **Session log** → `AI/logs/`

## Create or Append

### New note
If creating a new note:
1. Use kebab-case filename (e.g., `pflow-cargo-weight-analysis.md`)
2. Add proper frontmatter:
   ```yaml
   ---
   type: note
   tags: [pflow, relevant-tag]
   date: YYYY-MM-DD
   status: active
   related: [pflow-overview]
   ---
   ```
3. Write the content with `[[wikilinks]]` to related notes
4. Check for existing notes on the same topic first — prefer appending over duplicating

### Append to existing
If the content belongs in an existing note:
1. Read the existing note
2. Append a new section with a date header: `## YYYY-MM-DD — Topic`
3. Preserve existing content and frontmatter

## Connect
After saving, identify 1-3 existing notes that should link to the new content. Mention the connections in the response so the user can verify.
