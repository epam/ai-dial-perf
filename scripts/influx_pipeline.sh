#!/bin/bash
set -euo pipefail

GATLING_RESULTS_DIR=${GATLING_RESULTS_DIR:-"build/reports/gatling"}
ENV_NAME=${ENV_NAME:-unknown}
INFLUX_ORG=${INFLUX_ORG:-dial}
INFLUX_BUCKET=${INFLUX_BUCKET:-default}
export INFLUX_ORG INFLUX_BUCKET

X2I_VERSION=${X2I_VERSION:-x2i-1.1.0-alpha-2}
X2I_STOP_TIMEOUT=${X2I_STOP_TIMEOUT:-900}
X2I_BIN="/usr/local/bin/x2i"
X2I_PIDFILE="${PWD}/x2i.pid"
X2I_LOGFILE="${PWD}/x2i.log"

TELEGRAF_VERSION=${TELEGRAF_VERSION:-1.33.0}
TELEGRAF_BIN="/usr/local/bin/telegraf"
TELEGRAF_CONFIG_FILE="${PWD}/telegraf/telegraf.conf"
TELEGRAF_PIDFILE="${PWD}/telegraf.pid"
TELEGRAF_LOGFILE="${PWD}/telegraf.log"

NGINX_X2I_PORT=8088
NGINX_CONFIG_FILE="${PWD}/nginx/nginx-x2i.conf"

wait_for_http() {
  while ! curl -sf "$1" >/dev/null 2>&1; do
    echo "Waiting for $2 to be ready at $1..."
    sleep 5
  done
}

wait_for_port() {
  while ! (echo > /dev/tcp/localhost/"$1") 2>/dev/null; do
    echo "Waiting for $2 to be ready on port $1..."
    sleep 2
  done
}

cmd_install() {
  echo "=== Installing nginx and curl ==="
  sudo apt-get update -qq && sudo apt-get install -y -qq nginx curl

  echo "=== Installing x2i ${X2I_VERSION} ==="
  curl -sL "https://github.com/perfana/x2i/releases/download/${X2I_VERSION}/x2i-linux-amd64" -o /tmp/x2i-linux-amd64
  sudo install -m 0755 /tmp/x2i-linux-amd64 "${X2I_BIN}"
  rm -f /tmp/x2i-linux-amd64
  "${X2I_BIN}" --version

  echo "=== Installing Telegraf ${TELEGRAF_VERSION} ==="
  local telegraf_tmp_dir
  telegraf_tmp_dir="$(mktemp -d /tmp/telegraf.XXXXXX)"
  trap 'rm -rf "${telegraf_tmp_dir}"' RETURN

  curl -sL "https://dl.influxdata.com/telegraf/releases/telegraf-${TELEGRAF_VERSION}_linux_amd64.tar.gz" -o "${telegraf_tmp_dir}/telegraf.tar.gz"
  tar --no-same-owner --no-same-permissions -xzf "${telegraf_tmp_dir}/telegraf.tar.gz" -C "${telegraf_tmp_dir}"
  sudo install -m 0755 "${telegraf_tmp_dir}/telegraf-${TELEGRAF_VERSION}/usr/bin/telegraf" "${TELEGRAF_BIN}"
  "${TELEGRAF_BIN}" --version
}

cmd_start() {
  [ -z "${INFLUX_HOST:-}" ] && echo "::error::INFLUX_HOST is not set" && exit 1
  [ -z "${INFLUX_TOKEN:-}" ] && echo "::error::INFLUX_TOKEN is not set" && exit 1

  echo "=== Starting Telegraf ==="
  start-stop-daemon --start --background \
    --pidfile "${TELEGRAF_PIDFILE}" --make-pidfile \
    --exec "${TELEGRAF_BIN}" \
    --output "${TELEGRAF_LOGFILE}" \
    -- --config "${TELEGRAF_CONFIG_FILE}"
  echo "Telegraf started with PID: $(cat "${TELEGRAF_PIDFILE}")"
  wait_for_port 8087 "Telegraf"

  echo "=== Starting nginx (x2i -> Telegraf proxy) ==="
  sudo nginx -c "${NGINX_CONFIG_FILE}"
  wait_for_http "http://localhost:${NGINX_X2I_PORT}/ping" "nginx"

  echo "=== Starting x2i ==="
  mkdir -p "${GATLING_RESULTS_DIR}"
  local x2i_pid
  x2i_pid=$(
    "${X2I_BIN}" "${GATLING_RESULTS_DIR}" \
      -a "http://localhost:${NGINX_X2I_PORT}" \
      -b "${INFLUX_BUCKET}" \
      -y "statgpt" \
      -t "${ENV_NAME}" \
      -s "${X2I_STOP_TIMEOUT}" \
      -m 5000 \
      -l INFO \
      -o "${X2I_LOGFILE}" \
      -d | awk '{print $2}'
  )
  echo "${x2i_pid}" > "${X2I_PIDFILE}"
  echo "x2i started with PID: ${x2i_pid}"
}

cmd_stop() {
  if [ -f "${X2I_PIDFILE}" ]; then
    local x2i_pid
    x2i_pid=$(cat "${X2I_PIDFILE}")
    echo "=== Stopping x2i (PID: ${x2i_pid}) ==="
    echo "Waiting 60s for x2i to ingest final entries from simulation.log..."
    sleep 60
    kill -INT "${x2i_pid}" 2>/dev/null || true
    echo "Waiting up to ${X2I_STOP_TIMEOUT}s for x2i to flush and exit..."
    for ((i = 0; i < X2I_STOP_TIMEOUT; i++)); do
      kill -0 "${x2i_pid}" 2>/dev/null || break
      sleep 1
    done
    if kill -0 "${x2i_pid}" 2>/dev/null; then
      echo "::warning::x2i did not stop within ${X2I_STOP_TIMEOUT}s; forcing shutdown"
      kill -KILL "${x2i_pid}" 2>/dev/null || true
    fi
    rm -f "${X2I_PIDFILE}"
    echo "x2i stopped"
  else
    echo "::warning::x2i pidfile not found (${X2I_PIDFILE}), skipping shutdown"
  fi

  if [ -f "${TELEGRAF_PIDFILE}" ]; then
    echo "=== Stopping Telegraf (PID: $(cat "${TELEGRAF_PIDFILE}")) ==="
    start-stop-daemon --stop --pidfile "${TELEGRAF_PIDFILE}" --remove-pidfile --retry "TERM/10/KILL/5"
    echo "Telegraf stopped"
  else
    echo "::warning::Telegraf pidfile not found (${TELEGRAF_PIDFILE}), skipping shutdown"
  fi

  echo "=== Stopping nginx ==="
  sudo nginx -c "${NGINX_CONFIG_FILE}" -s stop 2>/dev/null || true
  echo "nginx stopped"
}

# ---------------------------------------------------------------------------
# Dispatch
# ---------------------------------------------------------------------------
case "${1:-}" in
install) cmd_install ;;
start) cmd_start ;;
stop) cmd_stop ;;
*)
  echo "Usage: $0 {install|start|stop}"
  exit 1
  ;;
esac
