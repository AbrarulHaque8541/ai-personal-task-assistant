# Agent status index

**Purpose:** A stable landing page for time-sensitive project facts. The rules in `AGENTS.md` are durable; the snapshots linked here are dated and can go stale.

## Latest reviewed snapshot

- [Daymark status snapshot — 2026-10-13 13:55 UTC](CURRENT_STATUS_2026-10-13_1355Z.md)
- [Earlier 2026-10-13 snapshot](CURRENT_STATUS_2026-10-13.md) — historical, captured before identity migration PR #286 merged. Do not use its pre-merge statements as current state.

## Before acting

1. Open the latest snapshot for context.
2. Re-check live `main`, the current release/tag, relevant issues and PRs, exact PR head SHA, and Actions runs.
3. If live evidence disagrees with the snapshot, trust live evidence for current state, record the discrepancy, and create/update the next dated snapshot. Never copy old SHA values or status labels as if they are current.

## Snapshot maintenance

- Keep durable policy in root `AGENTS.md`; do not repeat it in every snapshot.
- Put current versions, releases, PR/issue state, agent assignments, CI results, blockers, and device-test status in dated snapshots.
- Preserve old snapshots as historical evidence. Add a new snapshot rather than rewriting the past to make it appear current.
- Always include the verification timestamp, evidence links, exact commit/PR heads where relevant, and what was **not** tested.
- Do not report “PASS”, “merged”, “published”, or “device verified” without evidence for the exact object/build being discussed.
