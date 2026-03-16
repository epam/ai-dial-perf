#!/bin/bash
set -euo pipefail

echo "=== Installing nginx ==="
sudo apt-get update -qq && sudo apt-get install -y -qq nginx curl

echo "=== Downloading x2i ${X2I_VERSION} ==="
curl -sL "https://github.com/perfana/x2i/releases/download/${X2I_VERSION}/x2i-linux-amd64" -o /usr/local/bin/x2i
sudo chmod +x /usr/local/bin/x2i
x2i --version

echo "=== Downloading Telegraf ${TELEGRAF_VERSION} ==="
curl -sL "https://dl.influxdata.com/telegraf/releases/telegraf-${TELEGRAF_VERSION}_linux_amd64.tar.gz" -o /tmp/telegraf.tar.gz
tar xzf /tmp/telegraf.tar.gz -C /tmp/
sudo cp /tmp/telegraf-${TELEGRAF_VERSION}/usr/bin/telegraf /usr/local/bin/telegraf
sudo chmod +x /usr/local/bin/telegraf
rm -rf /tmp/telegraf*
telegraf --version
