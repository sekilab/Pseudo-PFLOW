# Branch Policy for Pseudo-PFLOW Project

This document outlines the branch naming conventions and usage policies for the Pseudo-PFLOW project to ensure smooth collaboration between multiple developers across different servers.

## Branch Overview

| Branch Name            | Purpose                                | Maintainer                               |
|------------------------|----------------------------------------|------------------------------------------|
| `pseudo-pflow-v3-dev`  | Main development branch for experiments and debugging | Collaborating Researcher, Lead Developer |
| `refactor-pseudo-v3`   | Refactoring of core classes and system structure | Lead Developer                           |
| `main` or `master`     | Stable release branch (future use; not yet merged)     | Maintainer                               |
| `mdx`, `hms-server2`   | Deprecated / unused legacy branches     | N/A                                      |

## Rules

1. **Never push directly to `main` or `master`** (reserved for release snapshots).
2. **All refactoring must be done on `refactor-pseudo-v3`**. This branch is owned by the lead developer.
3. **All debugging, small improvements, and feature testing continue on `pseudo-pflow-v3-dev`**.
4. **Rebase instead of merge** to avoid cluttered histories:
   ```bash
   git pull --rebase origin <branch>
   ```
5. **Avoid force pushes** unless absolutely necessary. Communicate before doing so.
6. **All branches should be pushed to `origin` for backup and collaboration.**

## Naming Conventions (for future branches)

| Prefix        | Description                       |
|---------------|-----------------------------------|
| `feature/`     | New experimental features         |
| `bugfix/`      | Fixes for existing functionality  |
| `refactor/`    | Structural or design refactoring |
| `hotfix/`      | Urgent fixes to production        |

---

For questions or proposed changes to this policy, please consult with the lead developer.

