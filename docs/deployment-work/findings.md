# Findings

- Prior deployment never mutated the target. Old source archive is stale.
- Current source requires Node >=24.14.0; preserve system Node 22 via /opt/xiaopiao runtime.
- Current server suite: 63/63 passed, npm run check passed.
- Production requires one HTTPS origin; TRUST_PROXY only accepts the private gateway header.

- Live target differed from old notes: old private release 6f41d712f246 was already running on Node 24.14.0, no public gateway. Preserved old code and made pre-update SQLite online backup.
- New release 1e9574723f61 activated, loopback health passed. Added 15 IPv4 Cloudflare-only TCP/8443 ingress rules; preexisting rules untouched.
- Cloudflare zone active/free, SSL full. Created host-only ECC Origin CA certificate (expires 2041-10-26), key generated on origin and never exported.
- Initial KooCLI filter used old parameter spelling; corrected from live help to security_group_id.1. Remote rg unavailable, used grep.
- An accidentally invoked first-install script without arguments exited at its argument guard; no changes. Correct update script then succeeded.
- Full apt update stalled on existing foreign repositories; terminated only our apt update and retried the Ubuntu source with bounded timeout.

- HTTPS /healthz=200; allowed Origin=200; foreign Origin=403; allowed preflight=204 from Mac.
- Real client IP was compared with Cloudflare /cdn-cgi/trace and the DB's rate-limit key; match=true, spoofed private gateway/XFF headers ignored=true. Python's default User-Agent received an edge 403; curl worked, and the check used curl.
- Huawei internal DNS 100.125.1.250 timed out; direct UDP queries to 223.5.5.5 / 119.29.29.29 succeed. Existing Tailscale INPUT drops 100.64.0.0/10 from eth0, including Huawei DNS replies. Service-only resolver mount fixed DNS without altering host networking.
- AgentMail from service namespace: authenticated GET returns CloudFront HTML 403 with Request blocked, with both app and browser User-Agent; same API/key on Mac works. Model authenticated /models returns 200 from service namespace. Native registration returns safe MAIL_UNAVAILABLE and rolls back account.
- Sent coordinator blocking approval request for a free, hostname/path-scoped Cloudflare mail relay Worker. No Worker created yet.

- tt approved exact-path free Worker relay via coordinator. Worker xiaopiao-mail-relay created, route 5a4af9bb4ff04005b727f10ae36a71c7, workers.dev/previews/observability/logpush disabled. Secret stored root 600 only on ECS and in Worker encrypted secret binding; AgentMail provider key remains persisted only on ECS.
- Initial Worker failed before upstream fetch: Cloudflare Workers rejects redirect:error (Node supports it). Added a runtime-compatibility regression seam, observed 503 vs 200 red, changed Worker to redirect:manual and treat all 3xx as failure; tests green. Official Request docs confirm platform limitation. Temporary diagnostic header removed.
- Worker update multipart initially omitted module filename and returned HTTP 400; corrected the Content-Disposition filename (initial successful deployment already had it).

- Final takeover read-back confirmed exact-path route, free zone, disabled workers.dev/previews/logpush/observability, ECS paired relay config and 600 root-owned secret. Worker source bytes match reviewed module.
- Existing self-inbox QA account from previous run blocked fresh registration with ACCOUNT_EXISTS. Recovered only that QA account via real mailbox; native login/ledger/circle/logout passed. Took online DB backups (quick_check=ok) before targeted isolated QA cleanup, then reran fresh registration.
- One run was interrupted after ledger success by an overlapping service restart; recovered by backing up and clearing only the isolated QA fixture, then reran sequentially with no restart overlap. Final fresh native registration, actual mailbox code, verify, login, ledger push/changes, circle create/list/leave, logout and revoked-session 401 all passed. No external recipients used. Test transaction tombstoned, no active test circle; verified QA account retained without live sessions.
- Public health=200, allowed Origin=200, foreign Origin=403, unauthorized/spoofed-IP relay=403. Restart check: API/nginx/backup timer enabled+active; subsequent public health=200. Local 68/68 tests and npm check passed. Native AccountClient protocol matches public API; physical-device UI not retested.

## Backend defense audit
- Remote listeners: project API loopback 8787, nginx 8443; SSH/Tailscale/cloud agents inventoried read-only and excluded from changes. API unit already unprivileged, private tmp, read-only system, restricted address families, CPU/memory caps.
- Source gaps: malformed URL constructed outside handler try; JSON body string-concatenates split UTF-8; body reads happen before auth for ledger/chat; auth concurrency acquired only after body; no total request/model/mail budget; provider error reader buffers entire body before slicing; model deadline stops aborting once output starts.
- QMuse is a separate cloud persistence surface in web/src/services. Platform configuration and actual deployed permissions require separate inspection; client-side owner query alone does not prove tenant isolation.

- Final defense implementation: bounded request/IP/auth/chat pools; database-backed global request/chat/mail quotas and address quota; body deadline/size/depth/UTF-8 checks; pre-body auth; bounded sessions; safe URL errors; bounded upstream discovery/error/SSE plus redirects denied; total model timeout and backpressure abort; fixed aggregate-only security metrics. Auth work slots survive client disconnect until the actual task settles.
- Existing circle callers required a default body-size wrapper after extracting readJson; retained it and added an authenticated oversize regression. Mail issue mutex prevents parallel code replacement.
- Final suite: 81/81 tests; npm syntax check passed; QMuse artifact validator passed. Candidate nginx and systemd verified on target without replacing production config. CF read-back reconfirmed one exact relay route, no alternative routes, no logging/dev/preview endpoints.
- QMuse declarations contain transactions, merchant_revenue, receipt_prefs and no custom functions. Runtime facade sets owner ACL; effective platform table READ/SUBMIT/EDIT/DELETE cannot be established from source. Browser task inventory showed no existing QMuse session/target; unrelated task left untouched. Platform permissions and two-identity isolation remain unverified.

- Approved defense release e5873e0ee535 deployed successfully. Public denial/error status checks passed; real self-inbox reset/native login, free-model SSE, ledger, circle lifecycle and revoked-session check passed. One initial mail failure was followed by successful namespace-specific send and complete repro; cause unproven, no code change or weakened controls. Private config unchanged, runtime bytes match archive, new systemd restrictions effective. Post-release backup quick_check=ok, no QA sessions/live transactions/circles remain. QMuse permissions still require separate platform verification.
