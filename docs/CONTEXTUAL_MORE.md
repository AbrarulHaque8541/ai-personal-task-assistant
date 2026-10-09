# Contextual More menu

## Behavior

| Screen | Top-right **More** opens |
|--------|-------------------------|
| Tasks home | **More · Tasks** — updates, backup, about, appearance, access |
| Web / browser mode | **More · Browser** — reload, find, share, downloads, extensions, site info, … |

Browser still has its own overflow control; top **More** in web mode uses the same browser actions list so settings match the screen you are on.

## Permissions (not broken)

Daymark intentionally does **not** show a permission dialog on cold start:

- Tasks / encrypted storage need no runtime permission
- `INTERNET` is install-time
- File access uses the system picker when the user exports/imports/attaches
- TalkBack is a system accessibility service

**Permission status** under More explains this; it is not a broken prompt.

## About

About this app lists version, what Daymark does / does not do, and Check for updates + Open releases.
