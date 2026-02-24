#!/bin/bash
set -euo pipefail

if [ "$#" -lt 2 ]; then
  echo "Usage: $0 <Report.json path> <output line protocol path>" >&2
  exit 1
fi

REPORT_PATH="$1"
OUTPUT_PATH="$2"

if [ ! -f "$REPORT_PATH" ]; then
  echo "Report file '$REPORT_PATH' not found" >&2
  exit 1
fi

ENVIRONMENT_TAG=${ENV:-unknown}
DEPLOYMENT_TAG=${DEPLOYMENT_NAME:-unknown}
SIMULATION_TAG=${SIMULATION:-unknown}
BUILD_TAG=${BUILD:-unknown}
TIMESTAMP=${REPORT_TIMESTAMP:-$(date +%s)}

jq -r --arg env "$ENVIRONMENT_TAG" \
      --arg dep "$DEPLOYMENT_TAG" \
      --arg sim "$SIMULATION_TAG" \
      --arg build "$BUILD_TAG" \
      --arg ts "$TIMESTAMP" '
  def esc($s):
    ($s // "unknown")
    | gsub("\\s"; "\\ ")
    | gsub(","; "\\,")
    | gsub("="; "\\=");

  map(select(.name != null))
  | .[]
  | "gatling_metrics,environment=\(esc($env)),deployment=\(esc($dep)),simulation=\(esc($sim)),request=\(esc(.name)),build=\(esc($build)) "
    + "total=\(.total // 0),ok=\(.ok // 0),ko=\(.ko // 0),rps=\(.rps // 0),"
    + "min=\(.min_v // 0),pct50=\(.pct50 // 0),pct75=\(.pct75 // 0),pct95=\(.pct95 // 0),pct99=\(.pct99 // 0),"
    + "max=\(.max_v // 0),mean=\(.mean // 0),stdev=\(.stdev // 0) \($ts)"
' "$REPORT_PATH" > "$OUTPUT_PATH"

if [ ! -s "$OUTPUT_PATH" ]; then
  echo "No line protocol data was generated from $REPORT_PATH" >&2
  exit 1
fi

