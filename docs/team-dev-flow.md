# Team Development Workflow for Pseudo-PFLOW Project

This document defines the workflow for multi-developer collaboration across the main and target servers in the Pseudo-PFLOW project.

## Developer Roles

- **Lead Developer (Main Server: hms-server2):** Responsible for core class refactoring, architecture updates, and integration.
- **Research Developer (Target Server: csis-server):** Responsible for debugging, running experiments, and secondary development.

## Branch Workflow

- `refactor-pseudo-v3`: Lead developer performs class and structure refactoring here.
- `pseudo-pflow-v3-dev`: Researcher continues experimental development, minor fixes, and debugging.

## Daily Workflow

### For Both Developers
```bash
# Start of work
git checkout <your-branch>
git pull --rebase origin <your-branch>

# End of work
git add .
git commit -m "Your update message"
git push origin <your-branch>
```

### For Lead Developer (Weekly Integration)
```bash
# Integrate updates from dev branch
git checkout refactor-pseudo-v3
git pull --rebase origin pseudo-pflow-v3-dev
# Test & validate before pushing
```

### Optional: Use Stash When Needed
```bash
# Save local uncommitted changes
git stash
# Restore later
git stash pop
```

## Conflict Resolution

- Always use `--rebase` to avoid unnecessary merge commits.
- If conflicts arise:
```bash
git status            # See which files are conflicted
# Manually resolve conflicts
git add <resolved-file>
git rebase --continue
```

## Communication

- Developers should **communicate before major structural changes**.
- Commit messages should be clear and descriptive.
- If in doubt, consult the lead developer before merging or rebasing.

---

This workflow aims to ensure smooth progress without blocking each other's work. Keep syncing regularly and commit often!

