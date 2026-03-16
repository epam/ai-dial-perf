#!/bin/bash
set -euo pipefail

echo "=== Starting Telegraf ==="
nohup telegraf --config telegraf/telegraf.conf > telegraf.log 2>&1 &
echo "$!" > telegraf.pid
echo "Telegraf started with PID: $(cat telegraf.pid)"
sleep 3
if curl -sf "http://localhost:${TELEGRAF_LISTEN_PORT}/ping" > /dev/null 2>&1; then
  echo "Telegraf listener is ready on port ${TELEGRAF_LISTEN_PORT}"
else
  echo "WARNING: Telegraf listener may not be ready yet"
fi

echo "=== Starting nginx (x2i -> Telegraf proxy) ==="
sudo nginx -c "$(pwd)/nginx/nginx-x2i.conf"
sleep 2
if curl -sf "http://localhost:${NGINX_X2I_PORT}/ping" > /dev/null 2>&1; then
  echo "Nginx x2i proxy is ready on port ${NGINX_X2I_PORT}"
else
  echo "WARNING: Nginx x2i proxy may not be ready yet"
fi

echo "=== Starting x2i ==="
mkdir -p ${GATLING_RESULTS_DIR}
X2I_PID=$(x2i ${GATLING_RESULTS_DIR} \
  -a "http://localhost:${NGINX_X2I_PORT}" \
  -b "${INFLUX_BUCKET}" \
  -y "statgpt" \
  -t "${ENV}" \
  -m 5000 \
  -l INFO \
  -o x2i.log \
  -d | awk '{print $2}')
echo "${X2I_PID}" > x2i.pid
echo "x2i started with PID: ${X2I_PID}"
