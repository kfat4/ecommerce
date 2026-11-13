---
name: handoff
description: Write a handoff note so a fresh session or another agent can continue the current work. Use before ending a long session, when context is getting full, or when the user says they will continue later.
disable-model-invocation: true
---

# Handoff

Write `.claude/memory/handoffs/YYYY-MM-DD-kisa-konu.md` (today's date) with:

- **Goal** – what the user asked for, in their terms.
- **State** – done and verified / done but unverified / not started. Name the commands you ran.
- **Branch and files** – current branch, uncommitted changes (`git status --short`), key files touched.
- **Decisions** – choices made and why; link ADRs.
- **Dead ends** – what was tried and failed, so nobody repeats it.
- **Next steps** – ordered, concrete, starting with the very next command or edit.
- **Open questions** – what needs the user's answer.

Then update `.claude/memory/project-state.md` and point to the note from the "Son devir notu" line in
`MEMORY.md` (replace the previous pointer). No secrets, no personal data. Delete handoff notes older than
the last three once their work has landed.
