# Huawei production deployment

- [x] Restore prior records and run current server tests/check.
- [x] Inventory instance, Cloudflare hostname, SSL mode and proxy trust.
- [x] Prepare and deploy isolated Node/API/gateway and daily SQLite backup.
- [x] Configure approved security-group and hostname-only DNS/origin rule.
- [x] Verify HTTPS, self-inbox native auth/ledger, restart and Origin controls.
- [x] Document exact state and rollback in docs/DEPLOY.md; commit and report.

Scope: server/ and docs/ only; no push, payments, unrelated service/DNS or zone-wide changes.

## Backend defense follow-up (2026-10-29)
- [x] Inventory all project backend entry points and remote service boundaries.
- [x] Audit auth, quotas, parsers, model/mail egress, database isolation and gateway.
- [x] Implement bounded defense controls and regression tests.
- [x] Run full server checks and validate deployment configuration.
- [x] Record findings, residual risks and a concrete rollout for tt review.

New remote deployment needs review of the finished changes; prior relay approval remains scoped.

- [x] Obtain approval for the concrete API/unit/nginx release, deploy, and perform sequential production acceptance.
- [ ] Verify effective QMuse table permissions and two-identity isolation through the platform.
