#!/usr/bin/env bash
set -euo pipefail

TARGET_FILE="${1:-Grafana.json}"
PLACEHOLDER='${DS_INFLUXDB}'
DASHBOARD_UID="${2:-auto-generated-uid}"

if [[ ! -f "${TARGET_FILE}" ]]; then
  echo "[grafana-validator] File not found: ${TARGET_FILE}" >&2
  exit 1
fi

TMP_FILE="$(mktemp)"
cleanup() { rm -f "${TMP_FILE}"; }
trap cleanup EXIT

if ! jq --arg placeholder "$PLACEHOLDER" --arg uid "$DASHBOARD_UID" '
  (.id = null | .uid = $uid | .version = ((.version // 0) + 1)) |
  def walk(f):
    . as $in
    | if type == "object" then
        reduce keys[] as $k
          ({}; . + { ($k): ($in[$k] | walk(f)) })
        | f
      elif type == "array" then
        map(walk(f)) | f
      else
        f
      end;
  walk(
    if type == "object"
       and has("datasource")
       and (.datasource | type == "object")
       and (.datasource.type == "influxdb")
       and (.datasource.uid != $placeholder)
    then
      .datasource.uid = $placeholder
    else
      .
    end
  )
' "${TARGET_FILE}" > "${TMP_FILE}"; then
  echo "[grafana-validator] Invalid JSON in ${TARGET_FILE}" >&2
  exit 1
fi

if ! cmp -s "${TARGET_FILE}" "${TMP_FILE}"; then
  mv "${TMP_FILE}" "${TARGET_FILE}"
  trap - EXIT
  echo "[grafana-dashboard-validator] Updated ID to null, UID of Dashboard to ${DASHBOARD_UID}, influxdb datasource UID in ${TARGET_FILE}"
else
  echo "[grafana-dashboard-validator] No changes required in ${TARGET_FILE}"
fi
