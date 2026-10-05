# Security Policy

## Supported Versions

| Version | Supported          |
| ------- | ------------------ |
| 1.0.x   | :white_check_mark: |
| < 1.0   | :x:                |

## Reporting a Vulnerability

We take the security of Daymark and its users seriously. If you believe you have found a security vulnerability in this repository, please do not disclose it publicly.

1. **Email Reports:** Contact `abrarbhai441@gmail.com` with the subject line `[SECURITY] <Brief Description>`.
2. **Details:** Include reproduction steps, affected versions, and potential impact.
3. **Response Time:** We aim to acknowledge reports within 48 hours and provide a remediation timeline.
4. **Data Protection Philosophy:**
   - On-device data must remain encrypted at rest.
   - Updates must never cause data loss or invalidate user Keystore keys.
   - All network calls (e.g. GitHub update checks) must enforce strict HTTPS and verify integrity (SHA-256).
