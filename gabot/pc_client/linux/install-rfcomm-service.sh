#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"

sudo install -o root -g root -m 755 \
    "$SCRIPT_DIR/gabot-rfcomm-bind" \
    /usr/local/sbin/gabot-rfcomm-bind
sudo install -o root -g root -m 644 \
    "$SCRIPT_DIR/gabot-rfcomm.service" \
    /etc/systemd/system/gabot-rfcomm.service
sudo systemctl daemon-reload
sudo systemctl enable gabot-rfcomm.service
sudo systemctl restart gabot-rfcomm.service

echo "gabot-rfcomm.service installed and started"
