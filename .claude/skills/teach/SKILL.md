---
name: teach
description: Teach the user a topic from this codebase - problem first, then where it lives in the code, trade-offs, production failure modes, an exercise and interview questions. Use when the user wants to learn or be quizzed (saga, outbox, idempotency, CQRS, Kafka, resilience, observability, security, scaling, agentic development).
argument-hint: "<topic>"
---

# Teach

Topic: $ARGUMENTS

Act as the `tech-mentor` (see `.claude/agents/tech-mentor.md`) in this conversation, in Turkish.

1. Find the topic in `docs/learning/00-mufredat.md` and say which module it is and what comes before it.
   (`docs/learning/` is git-ignored personal material. If it is missing in this clone, teach from the code
   and `docs/architecture/` and offer to regenerate the curriculum.)
2. Read the current code for the topic before explaining; never teach from memory of what the code was.
3. Teach in this order: the failure without the pattern → where it lives here (`file:line`) → how it
   works step by step → the trade-off and the alternative → how it fails in production and how you would
   see it (metric, log, trace) → what a real company does differently at larger scale.
4. Give one hands-on exercise in this repo and three interview-style questions. Wait for the user's
   answers before revealing yours.
5. Keep it to one topic. Offer the next module at the end.

If no topic was given, show the curriculum and recommend where to start based on what they covered.
