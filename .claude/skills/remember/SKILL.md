---
name: remember
description: Save a durable fact to the shared project memory in .claude/memory so future sessions and all agents know it. Use when a non-obvious gotcha cost real time, a convention was agreed, a tool or version quirk was discovered, or the user says "remember this".
argument-hint: "<what to remember>"
---

# Remember

To remember: $ARGUMENTS

Shared memory is committed to git, so everyone who clones the repo inherits it. Write accordingly.

1. **Is it worth keeping?** Keep: things you could not have known from reading the code (why, gotchas,
   verified versions, agreed conventions, current state). Skip: what the code or git history already
   says, one-off details, anything uncertain.
2. **Never store** secrets, tokens, personal data, machine-specific paths or the user's private details.
3. **Pick the file**:
   - `lessons.md` – gotchas and verified technical facts (problem → cause → fix).
   - `project-state.md` – what exists, what is in progress, what is next.
   - `glossary.md` – domain and architecture terms as used in this project.
   - `open-questions.md` – undecided things and who should decide.
   - An architectural decision is not a memory: use `/write-adr`.
4. **Check for an existing entry first** and update it instead of adding a duplicate. Delete entries that
   turned out to be wrong.
5. **Format**: one fact per entry, dated (`YYYY-MM-DD`), with how it was verified
   (`verified: ./mvnw test`, `verified: Docker Hub tag exists`, or `unverified`).
6. **Index**: `MEMORY.md` gets a new line only for a new file or a fact every session must see. Keep
   `MEMORY.md` under 150 lines; it is loaded into every session.

Confirm to the user in one line what was saved and where.
