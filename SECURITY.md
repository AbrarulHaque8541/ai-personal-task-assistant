# Security Policy

## Version and report-triage status

**Checked:** 2026-10-13. This table describes vulnerability-report handling, not a promise of ongoing support, release qualification, or device compatibility. Verify the live [GitHub Releases](https://github.com/AbrarulHaque8541/daymark/releases) page for the latest published artifacts. PR #295 prepared the future `daymark` URL, but the actual GitHub slug remains `AbrarulHaque8541/ai-personal-task-assistant` until the owner applies the separate rename; the prepared link may not resolve before then. This review does not rename the repository.

| Version/source | Verified boundary | Security-report status |
|---|---|---|
| [Latest published: v1.0.5](https://github.com/AbrarulHaque8541/daymark/releases/tag/v1.0.5) | Production APK is published; it targets API 35 and uses the previous application identity. It is not an update for the current source identity. | Reports accepted; best-effort triage. This is not a support or device-qualification guarantee. |
| Current `main` source | `com.abrar.daymark`, `versionName` 1.0 / `versionCode` 7, target API 36; no production release for this identity has been published. | Reports accepted; best-effort triage. Source status is not a published or qualified release. |
| Earlier tags/builds | Historical; attached assets and verification vary by tag. | Reports accepted; best-effort triage. Check the exact release and artifact before assessing exposure. |

## Reporting a Vulnerability

We take the security of Daymark and its users seriously. If you believe you have found a security vulnerability in this repository, please do not disclose it publicly.

1. **Email reports:** Contact `abrarbhai441@gmail.com` with the subject line `[SECURITY] <Brief Description>`.
2. **Details:** Include reproduction steps, affected versions, and potential impact. Do not include another person's private data or credentials.
3. **Response:** The project aims to acknowledge reports within 48 hours and provide a remediation timeline; this is an aim, not a guaranteed service-level commitment.
4. **Data-protection boundaries:**
   - Task and attachment payloads use at-rest encryption; unencrypted metadata/settings and key/recovery limits are documented.
   - Updates must preserve user data and signing identity, but failure boundaries remain; do not promise zero data loss.
   - Network features use HTTPS. Verify downloaded artifacts against trusted publisher and digest data; HTTPS alone does not establish artifact authenticity.
