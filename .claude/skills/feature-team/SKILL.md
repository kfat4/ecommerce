---
name: feature-team
description: Run a feature through the whole agent team - design, implement, test, review. Use when the user asks to build a feature "with the team" or wants the full pipeline.
argument-hint: "<feature description>"
disable-model-invocation: true
---

# Feature pipeline

Feature: $ARGUMENTS

You are the tech lead. Coordinate the specialists in `.claude/agents/`; do not do their work yourself.
Give each agent a self-contained brief: the goal, the files or area, constraints from CLAUDE.md, and
what "done" looks like. Agents do not see this conversation.

## Stages

1. **Clarify** – restate the feature in two or three sentences and list assumptions. Ask the user only
   about what would change the design.
2. **Design** – `solution-architect`: options, decision, events and API, failure modes, impact.
   If it is an architectural decision, an ADR draft under `docs/adr/`.
   **Stop here and show the design to the user. Continue only after they agree.**
3. **Implement** – `java-backend-developer` with the approved design. One agent per independent slice;
   never two writers on the same files.
4. **Test** – `test-engineer`: unhappy paths first (duplicate, late, timeout, partial failure).
5. **Review** – `code-reviewer`, plus `security-reviewer` when auth, payment, personal data or external
   calls are involved, plus `sre-observability` when a new flow needs a metric or alert.
6. **Fix** – send must-fix findings back to the developer; re-run the reviewer on the fix only.
7. **Verify** – run `./mvnw test` yourself. Do not trust a report you did not check.
8. **Teach and record** – explain to the user in Turkish what was built, which patterns were applied and
   the trade-offs. Update `.claude/memory/project-state.md`; add lessons with `/remember`.

Never commit or push as part of this pipeline; the user decides that.

Small change (one file, no new behaviour across services)? Skip the pipeline and say so.
