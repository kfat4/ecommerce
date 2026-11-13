# Shared project memory (index)

Loaded into every session through `CLAUDE.md`. Keep it under 150 lines: one line per pointer.
Entries are claims with a date, not truth. Verify against the code before acting. How to write here: `/remember`.

## Files

- [project-state.md](project-state.md) – what is built, what is in progress, what comes next
- [lessons.md](lessons.md) – gotchas and verified technical facts (versions, API quirks, tooling)
- [glossary.md](glossary.md) – domain and architecture terms as used in this project
- [open-questions.md](open-questions.md) – undecided things waiting for the user or an ADR
- `handoffs/` – session handoff notes (`/handoff`); Son devir notu: none yet

## Always remember

- Decisions live in `docs/adr/` (index: `docs/adr/README.md`). Memory points to them, never duplicates them.
- Roadmap and gap analysis versus a real e-commerce platform: `docs/roadmap.md`.
- The Docker stack has never been started on the owner's machine (as of 2026-11-14). Compose,
  collector config, alert rules and dashboards are statically validated only.
- Integration tests (`*IT`) run in `./mvnw verify` (failsafe, since 2026-11-14). They run in CI; locally
  they need a working Docker (the owner's Docker Desktop was hung on 2026-11-14).
- `docs/learning/` (curriculum, lesson notes) is the owner's personal material: git-ignored, never commit it
  (user instruction, 2026-11-14). `tech-mentor` therefore uses local memory.
- Per-agent memory is separate: `.claude/agent-memory/<agent>/MEMORY.md` (each agent curates its own).
