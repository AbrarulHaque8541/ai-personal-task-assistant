# Natural-language quick-add

Parses tokens from the capture title **before** `TaskLogic.create` validates fields.

Supported:
- Dates: `today`, `tomorrow`, weekday names (next occurrence)
- Times: `4pm`, `4:30pm`, `16:00`
- Priority: `high` / `urgent`, `medium` / `med`, `low`
- Tags: `#work` `#home` (stored in notes as `#tag` lines for search without schema bump)

Example: `call dentist tomorrow 4pm high #work`
→ title `call dentist`, due tomorrow, 16:00, high, notes `#work`

Explicit dueDate/priority/dueTime passed into `create` still win when already set (except medium→parsed priority upgrade).

---
Work by: Grok | Grok 4.5 (xAI) | 2026-10-11T18:30Z
