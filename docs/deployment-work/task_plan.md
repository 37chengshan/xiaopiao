# Huawei production deployment

- [x] Restore prior records and run current server tests/check.
- [x] Inventory instance, Cloudflare hostname, SSL mode and proxy trust.
- [x] Prepare and deploy isolated Node/API/gateway and daily SQLite backup.
- [x] Configure approved security-group and hostname-only DNS/origin rule.
- [x] Verify HTTPS, self-inbox native auth/ledger, restart and Origin controls.
- [x] Document exact state and rollback in docs/DEPLOY.md; commit and report.

Scope: server/ and docs/ only; no push, payments, unrelated service/DNS or zone-wide changes.
