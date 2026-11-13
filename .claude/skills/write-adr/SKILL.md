---
name: write-adr
description: Record an architectural decision as an ADR in docs/adr. Use when a decision changes service boundaries, data ownership, a technology, a consistency model, a contract policy or one of the architecture rules in CLAUDE.md.
argument-hint: "<decision title>"
---

# Write an ADR

Decision: $ARGUMENTS

1. Find the next number: `ls docs/adr/` (four digits, never reuse).
2. Copy `${CLAUDE_SKILL_DIR}/template.md` to `docs/adr/NNNN-kisa-baslik.md` (Turkish, kebab-case).
3. Fill every section. An ADR is short (one screen) and honest:
   - **Bağlam** is the forces, not the solution.
   - **Seçenekler** lists real alternatives with their costs, including the one rejected.
   - **Karar** is one clear sentence, then the reasoning.
   - **Sonuçlar** includes the downsides we accept.
   - **Ne zaman yeniden düşünülür** names the trigger (scale, team, cost) that would reopen it.
4. Add the ADR to the table in `docs/adr/README.md`.
5. If it replaces an older ADR, set that one's status to `Yerini NNNN aldı` (do not delete or rewrite it).
6. Add a one-line pointer in `.claude/memory/MEMORY.md` only if agents must know it in every session.
