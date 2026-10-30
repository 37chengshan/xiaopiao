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
