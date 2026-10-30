#!/usr/bin/env bash
set -euo pipefail
umask 077
backup_dir=/var/lib/xiaopiao/backups
install -d -m 0700 "$backup_dir"
target="$backup_dir/api-$(date -u +%Y%m%dT%H%M%S)-${RANDOM}.sqlite"
DB_PATH=/var/lib/xiaopiao/api.sqlite /opt/xiaopiao/runtime/bin/node /opt/xiaopiao/current/deploy/backup.mjs "$target"
# Keep thirty days. Backups and the source stay on the same instance.
find "$backup_dir" -maxdepth 1 -type f -name 'api-*.sqlite' -mtime +30 -delete
