#!/usr/bin/env bash
# First installation only. Run AFTER explicit approval on the inspected Huawei/Aliyun host.
set -euo pipefail
stage=${1:?Provide the reviewed staging directory}
release=${2:?Provide the reviewed release id}
[[ "$release" =~ ^[a-f0-9]{12}$ ]] || { echo 'Invalid release id' >&2; exit 1; }
[[ "$stage" == /tmp/xiaopiao-* ]] || { echo 'Unexpected staging directory' >&2; exit 1; }
[[ $(id -u) == 0 ]] || { echo 'Root required for systemd service installation' >&2; exit 1; }
for target in /opt/xiaopiao /var/lib/xiaopiao /etc/systemd/system/xiaopiao-api.service; do
  [[ ! -e "$target" ]] || { echo 'Target already exists; inspect and prepare an update plan first' >&2; exit 1; }
done
! id xiaopiao >/dev/null 2>&1 || { echo 'Service account already exists; inspect first' >&2; exit 1; }
[[ -s /etc/xiaopiao/server.env ]] || { echo 'Private service environment missing' >&2; exit 1; }
cd "$stage"
sha256sum --check SHA256SUMS
useradd --system --home-dir /var/lib/xiaopiao --shell /usr/sbin/nologin xiaopiao
install -d -m 0755 /opt/xiaopiao/releases /opt/xiaopiao/node-runtime
install -d -m 0700 -o xiaopiao -g xiaopiao /var/lib/xiaopiao
mkdir "/opt/xiaopiao/releases/$release"
tar -xzf source.tar.gz -C "/opt/xiaopiao/releases/$release"
tar -xJf node-v24.14.0-linux-x64.tar.xz -C /opt/xiaopiao/node-runtime
ln -s /opt/xiaopiao/node-runtime/node-v24.14.0-linux-x64 /opt/xiaopiao/runtime
ln -s "/opt/xiaopiao/releases/$release" /opt/xiaopiao/current
/opt/xiaopiao/runtime/bin/node --check /opt/xiaopiao/current/src/index.mjs
install -m 0644 /opt/xiaopiao/current/deploy/xiaopiao-api.service /etc/systemd/system/xiaopiao-api.service
install -m 0644 /opt/xiaopiao/current/deploy/resolv.conf /etc/xiaopiao/resolv.conf
systemctl daemon-reload
systemctl enable --now xiaopiao-api.service
curl --fail --silent --show-error --retry 5 --retry-delay 1 --retry-connrefused http://127.0.0.1:8787/healthz
printf '\nPrivate API installed. No DNS, firewall, or existing service was changed.\n'
