#!/usr/bin/env bash
# Run as root on the inspected host; takes a source-only SHA256-checked archive.
set -euo pipefail
stage=${1:?Provide staging directory}
release=${2:?Provide release id}
[[ "$release" =~ ^[a-f0-9]{12}$ && "$stage" == /tmp/xiaopiao-* ]] || exit 1
[[ $(id -u) == 0 && -L /opt/xiaopiao/current ]] || exit 1
[[ ! -e /opt/xiaopiao/releases/$release ]] || { echo 'Release exists; inspect first' >&2; exit 1; }
cd "$stage"
sha256sum --check SHA256SUMS
previous=$(readlink -f /opt/xiaopiao/current)
config_backup="/var/backups/xiaopiao/config-before-$release"
install -d -m 0700 "$config_backup"
cp -p /etc/systemd/system/xiaopiao-api.service "$config_backup/xiaopiao-api.service"
if [[ -f /etc/xiaopiao/resolv.conf ]]; then cp -p /etc/xiaopiao/resolv.conf "$config_backup/resolv.conf"; fi
# Online backup captures WAL before the new version can change schema.
install -d -m 0700 -o xiaopiao -g xiaopiao /var/lib/xiaopiao/backups
runuser -u xiaopiao -- env DB_PATH=/var/lib/xiaopiao/api.sqlite /opt/xiaopiao/runtime/bin/node "$previous/deploy/backup.mjs" "/var/lib/xiaopiao/backups/pre-$release.sqlite"
install -d -m 0755 "/opt/xiaopiao/releases/$release"
tar -xzf source.tar.gz -C "/opt/xiaopiao/releases/$release"
/opt/xiaopiao/runtime/bin/node --check "/opt/xiaopiao/releases/$release/src/index.mjs"
install -m 0644 "/opt/xiaopiao/releases/$release/deploy/resolv.conf" /etc/xiaopiao/resolv.conf
install -m 0644 "/opt/xiaopiao/releases/$release/deploy/xiaopiao-api.service" /etc/systemd/system/xiaopiao-api.service
systemctl daemon-reload
ln -s "/opt/xiaopiao/releases/$release" /opt/xiaopiao/current.next
mv -Tf /opt/xiaopiao/current.next /opt/xiaopiao/current
if ! systemctl restart xiaopiao-api.service || ! curl --fail --silent --show-error --retry 10 --retry-delay 1 --retry-connrefused http://127.0.0.1:8787/healthz; then
  ln -s "$previous" /opt/xiaopiao/current.next
  mv -Tf /opt/xiaopiao/current.next /opt/xiaopiao/current
  cp -p "$config_backup/xiaopiao-api.service" /etc/systemd/system/xiaopiao-api.service
  if [[ -f "$config_backup/resolv.conf" ]]; then
    cp -p "$config_backup/resolv.conf" /etc/xiaopiao/resolv.conf
  else
    rm -f /etc/xiaopiao/resolv.conf
  fi
  systemctl daemon-reload
  systemctl restart xiaopiao-api.service
  echo 'Health check failed; previous code restored. Inspect schema before restoring data.' >&2
  exit 1
fi
printf '\nRelease active: %s; previous: %s\n' "$release" "$previous"
