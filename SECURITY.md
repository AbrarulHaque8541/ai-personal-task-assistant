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
   - On-device data must remain encrypted at rest.
   - Updates must never cause data loss or invalidate user Keystore keys.
   - All network calls (e.g. GitHub update checks) must enforce strict HTTPS and verify integrity (SHA-256).
