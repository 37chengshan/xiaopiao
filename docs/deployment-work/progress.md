# Progress

2026-10-29: Restored coordinator records, read global/project/Orca/Cloudflare rules, indexed server, completed 63 tests and syntax checks. Preparing read-only target inventory.

Updated existing API safely with pre-release online backup and atomic code symlink. Secret env is root-owned 600, includes production APP_ORIGIN/DB_PATH/TRUST_PROXY and no XP_DEV_MAIL. Preparing nginx 8443 only and daily backup timer.

Mac health/Origin/IP checks passed; restart returns public 200 and API/nginx/timer are enabled+active. Backup copy was reopened with quick_check=ok and ledger schema present. Added rollback of unit/resolver in update script. Optional relay source and its 3 security tests are local drafts only, waiting for coordinator approval; native registration is still blocked by upstream CloudFront 403.

Ego-browser TaskSpace 148 completed and closed: same-origin OPTIONS 204 with matching ACAO; example.com cross-origin request returned network 403 and browser rejected it.

Final source archive f84e8fc06f26 built only after 68/68 tests and npm check; includes optional relay code but no relay environment, Worker or route has been activated. Updated production code/unit/resolver with pre-release SQLite backup and config snapshot. Secret scan found no configured API secret in modified/untracked files.

Coordinator delivered explicit tt approval for the free, exact-path mail relay in response to msg_c998df34df49. Created xiaopiao-mail-relay with only xiaopiao.citygenius.top/__mail/send route; workers.dev/previews/logpush/observability disabled and read back. Persisted relay secret root 600 on ECS and enabled paired private relay env; API active. Running real native self-inbox chain.

Final takeover: reconciled stale handoff with live deployed Worker. Reviewed exact module and regression tests; no additional Cloudflare mutation was necessary. Completed real self-inbox fresh-register → verify → native login → ledger push/changes → logout (revoked access 401), plus circle lifecycle and recovery mail. QA-only cleanup was preceded by SQLite online backups and integrity checks; no other account was changed. Updated DEPLOY.md with relay name/route, private secret paths, rollback and Android build/runtime requirements. Server suite 68/68 and syntax checks passed; preparing local commit only, no push.

Backend defense follow-up started: read-only ECS service/listener inventory and source audit. Only project services in scope; preserve SSH, Tailscale and cloud management agents.

2026-10-30 defense follow-up: local changes complete; final 81/81 tests pass, npm check passes, QMuse artifact validator passes. nginx candidate syntax and full config test pass on target version; systemd unit verification passes. Production API/unit/nginx/Worker unchanged, active services retained. SECURITY.md records exact policy, pending platform permissions, residual backup/SSL boundaries, deployment and rollback.
Errors resolved during this follow-up: one edit command ran from server/ with root-relative paths and failed before editing; rerun from worktree root. One long stdin Python command failed UTF-8 parsing before edits; applied the patch through apply_patch instead. First new chat-pool test waited until SSE completion because response headers were buffered; explicit flushHeaders made admission observable and timeout test deterministic. stdin nginx test was not seekable on the second read; temporary candidate file validation succeeded and was cleaned up.
Release preparation: source-only archive and configured-secret scan before local commit. Awaiting tt approval to replace API/unit/nginx; QMuse effective permission verification remains separate. No push.

2026-10-30: tt explicitly approved rollout in this conversation. Verified archive e5873e0ee535 matches reviewed 89fcf19 source, uploaded, backed up dedicated nginx/unit and private-config hashes, made pre-release SQLite online backup. update-private.sh activated API/unit; nginx syntax test and reload passed. All new systemd hardening settings read back; private env and relay key unchanged. All 29 runtime files match archive; API NRestarts=0.

Public checks passed: health/allowed Origin 200, foreign Origin/unauthorized relay 403, unauthenticated ledger/chat 401, invalid JSON/MIME 400. First forgot returned MAIL_UNAVAILABLE. Diagnosed with status-only probes: root namespace sender timed out (host DNS differs); service namespace sender returned 200. No code, DNS or defense relaxation applied. Original self-inbox recovery/native-login/ledger/circle/logout chain then passed, and a real free-model SSE response passed. Exact first-failure cause remains unproven. QA sessions/live transactions/circles all zero after cleanup. Deployment did not overlap account checks with restarts.

Both pre-release backup and post-release api-20261030T071810-5084.sqlite reopen with quick_check=ok. security_summary observed with only aggregate counters; request/auth/chat counts return to zero. API/nginx/timer remain enabled+active. Updated DEPLOY/SECURITY with actual state and rollback. QMuse effective table permissions and two-identity isolation remain outstanding; this approval did not establish platform isolation. No push or unrelated resource changes.
