# Future Milestones and Backlog

This document tracks roadmap proposals and research candidates for milestones after v1.0.3 (such as v1.1.0).

---

## Browser Enhancements (Candidate for v1.1.0)

*(Reference: Issue #167 — UC-style settings & search enhancements)*

1. **Home Page Default Engine Grid**:
   - A dedicated picker screen presenting available search engines in a visual grid layout (Google, DuckDuckGo, Yandex, Bing, Perplexity, ChatGPT, etc.).
2. **One-Tap Re-Search Row**:
   - On the browser search results page, display quick engine-switch chips to re-run the current query against another provider without retyping.
3. **Customizable Engine Ordering**:
   - Allow user reordering or subset selection for the primary provider list via local preferences while keeping `SearchEngine` enum fixed.
4. **Offline & Privacy Policy Alignment**:
   - Maintain Daymark's strict zero-telemetry, offline-first policy: no remote scraper or external server aggregation. Aggregated search refers to multi-provider client-side launch shortcuts, not backend HTML scraping.

---

## Task Management Enhancements (Candidate for v1.1.0)

*(Reference: Issue #168 & `docs/TASK_ADD_GAPS.md`)*

1. **Recurring Tasks**:
   - Recurrence state machine (`Task.recurrence`: NONE / DAILY / WEEKLY / MONTHLY).
   - On-completion instance advancement with due date normalization.
   - Schema v5 migration and portable backup manifest bump (v3).
   - Deterministic host tests in `TaskLogicSmoke` / `TaskSnapshotCodecSmoke`.
2. **Task Tags**:
   - Categorized tag dimensions (≤8 tags per task, ≤24 chars per tag).
   - Editor tag chips and filter bar integration.
   - Schema v5 inclusion with backward compatibility.
