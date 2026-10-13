# Natural-language quick add

The single-line task capture field parses supported scheduling words locally; no network call is used.

Supported examples:
- `call dentist tomorrow 4pm high #work` → title `call dentist`, tomorrow at 16:00, high priority, tag `work`
- `pay rent next week 09:30 #home` → due in seven days at 09:30, tag `home`
- `review notes Monday low` → next Monday, low priority
- `call clinic 12am` → today at 00:00

Supported date words: today, tomorrow, next week, weekday names and common three-letter weekday forms.
Supported time formats: `4pm`, `4:30pm`, and `16:00`.
Supported priorities: high, urgent, important, medium, med, low.
Tags use `#tag` syntax and are stored in the existing task tags field.

Unknown words remain part of the title. Only the quick-add capture path uses this parser; the regular task editor is not rewritten. Automated parser smoke tests run as part of `sh android-app/tools/run-core-tests.sh`.

---
Work by: ChatGPT
Model: GPT-6
Tooling: GitHub MCP tools
Timestamp (UTC): 2026-10-13T14:07:28.373Z
