# Security Policy

## Supported Versions

The table describes security-report triage policy, not a claim that a production release has been qualified. As of 2026-10-06, `v1.0.0` is published without an attached APK asset, and no signed production APK is established.

| Version | Security-report status |
| ------- | ---------------------- |
| Current `main` / unreleased source | Best-effort triage |
| `v1.0.0` debug release metadata | Report accepted; no production-support or device-qualification claim |

## Reporting a Vulnerability

We take the security of Daymark and its users seriously. If you believe you have found a security vulnerability in this repository, please do not disclose it publicly.

1. **Email Reports:** Contact `abrarbhai441@gmail.com` with the subject line `[SECURITY] <Brief Description>`.
2. **Details:** Include reproduction steps, affected versions, and potential impact.
3. **Response Time:** We aim to acknowledge reports within 48 hours and provide a remediation timeline.
4. **Data Protection Philosophy:**
   - Protect sensitive task and attachment payloads with at-rest encryption; document unencrypted metadata/settings and key or recovery limits.
   - Design and test updates to preserve user data and signing identity, but document failure boundaries and do not promise zero-loss behavior.
   - Use HTTPS for network features. Verify downloaded artifacts against trusted publisher and digest data; HTTPS alone does not establish artifact authenticity.
