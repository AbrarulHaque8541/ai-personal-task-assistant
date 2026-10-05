# Contributing to Daymark

Thank you for your interest in improving Daymark! Whether you are reporting a bug, proposing a new feature, or submitting code, your contributions are welcome.

## Code of Conduct

All contributors are expected to follow our [Code of Conduct](CODE_OF_CONDUCT.md).

## Core Principles

1. **Zero Data Loss Guarantee:** No update, migration, or refactoring may delete, corrupt, or expose user tasks or attachment data.
2. **Offline-First & Privacy First:** Daymark functions completely offline without requiring mandatory third-party accounts.
3. **Strict Integrity:** Any remote feature (such as the in-app updater) must cryptographically verify downloads against expected hashes before prompting the user.

## Pull Request Guidelines

1. Fork the repo and create your branch from `main`.
2. Ensure any new features include unit/smoke tests under `android-app/tools/`.
3. Verify that changes run cleanly against the test suite (`npm test` for web, `sh android-app/tools/run-core-tests.sh` for Android).
4. Clearly state what problem your PR solves and verify backward compatibility.
