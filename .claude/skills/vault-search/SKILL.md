---
name: vault-search
description: Searches the Obsidian vault for relevant notes, decisions, or context. Use when user asks to find notes, search vault, look up decisions, or recall previous context.
argument-hint: "[search-term]"
user-invocable: true
allowed-tools: "Read Grep Glob"
---

# Vault Search

Search the Obsidian vault at `H:\Dropbox\obsidian-vault\` for: `$ARGUMENTS`

## Search Strategy

1. **Filename match** — Search for files matching the query:
   ```
   Glob: H:\Dropbox\obsidian-vault\**/*{query}*.md
   ```

2. **Content search** — Search inside files for the query term:
   ```
   Grep: pattern=$ARGUMENTS, path=H:\Dropbox\obsidian-vault\, glob=*.md
   ```

3. **Wiki priority** — Search `wiki/` first:
   - `_index.md` — master index of all wiki pages
   - `pflow-overview.md` — architecture, tech stack, gotchas
   - `pflow-codebase-map.md` — file locations
   - `pflow-pipeline.md` — step-by-step runbook
   - `pflow-refactoring-plan.md` — tech debt items
   - `pflow-decisions.md` — ADRs
   - `pflow-glossary.md` — domain terms

4. **Follow wikilinks** — If a found note contains `[[related-note]]`, read the linked note too for context.

5. **Tag search** — If the query matches a common tag, search frontmatter:
   ```
   Grep: pattern="tags:.*{query}", path=H:\Dropbox\obsidian-vault\
   ```

## Output Format
For each matching note, report:
- **File path** (relative to vault root)
- **Title** (first `#` heading)
- **Type & tags** (from frontmatter)
- **Relevant excerpt** (2-3 lines of matching content)
- **Connected notes** (wikilinks found in the note)
