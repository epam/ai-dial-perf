#!/bin/bash

# Check if jq is installed, if not, install it (quietly)
if ! command -v jq >/dev/null 2>&1; then
  apt-get update -qq >/dev/null 2>&1 && apt-get install -y -qq jq >/dev/null 2>&1
  if ! command -v jq >/dev/null 2>&1; then
    echo "jq installation failed. Please install jq manually."
    exit 1
  fi
fi

# Check if bc is installed, if not, install it (quietly)
if ! command -v bc >/dev/null 2>&1; then
  apt-get update -qq >/dev/null 2>&1 && apt-get install -y -qq bc >/dev/null 2>&1
  if ! command -v bc >/dev/null 2>&1; then
    echo "bc installation failed. Please install bc manually."
    exit 1
  fi
fi

# Check if curl is installed, if not, install it (quietly)
if ! command -v curl >/dev/null 2>&1; then
  apt-get update -qq >/dev/null 2>&1 && apt-get install -y -qq curl >/dev/null 2>&1
  if ! command -v curl >/dev/null 2>&1; then
    echo "curl installation failed. Please install curl manually."
    exit 1
  fi
fi

# This script compares the current performance report with the previous one stored in GitLab packages.
if [[ -f "Old_Report.json" ]]; then
  if [[ $(cat OldReports/Old_Report.json ) != '{"message":"404 Not found"}' ]]; then
  echo "File Old_Report.json downloaded successfully"
  else
  echo "File download failed!"
  cat OldReports/Old_Report.json
  echo "There are no previous data in packages." > MailReport.json
  echo "There are no previous data in packages." > Comparison.txt
  exit 0
  fi
fi

if [[ -f "OldReports/Old_Report.json" ]]; then
  echo "Using existing Old_Report.json"
else
  echo "No previous report found, skipping comparison."
  echo "No previous report available for comparison." > MailReport.json
  echo "No previous report available for comparison." > Comparison.txt
  exit 0
fi

general_report='{"status": "STABLE", "items": []}'
string_report=""

# Attempt to execute the commands
if [[ -f "Report.json" && -f "OldReports/Old_Report.json" ]]; then
  # Read the JSON files using jq
  service_report_current=$(<Report.json)
  service_report_previous=$(<OldReports/Old_Report.json)
  current_length=$(echo "$service_report_current" | jq '. | length')

  for (( index=0; index<current_length; index++ )); do
    reportItem_name=$(echo "$service_report_current" | jq -r ".[$index].name")
    reportItem_pct50=$(echo "$service_report_current" | jq -r ".[$index].pct50")
    reportItem_pct75=$(echo "$service_report_current" | jq -r ".[$index].pct75")
    reportItem_pct95=$(echo "$service_report_current" | jq -r ".[$index].pct95")
    reportItem_pct99=$(echo "$service_report_current" | jq -r ".[$index].pct99")
    reportItem_mean=$(echo "$service_report_current" | jq -r ".[$index].mean")

    if [[ "$reportItem_name" != "All Requests" ]]; then
      it_last_pct50=$(echo "$service_report_previous" | jq -r ".[$index].pct50")
      it_last_pct75=$(echo "$service_report_previous" | jq -r ".[$index].pct75")
      it_last_pct95=$(echo "$service_report_previous" | jq -r ".[$index].pct95")
      it_last_pct99=$(echo "$service_report_previous" | jq -r ".[$index].pct99")
      it_last_mean=$(echo "$service_report_previous" | jq -r ".[$index].mean")

      pct50_change=$(echo "scale=4; ($reportItem_pct50 - $it_last_pct50) / $it_last_pct50 * 100" | bc)
      pct75_change=$(echo "scale=4; ($reportItem_pct75 - $it_last_pct75) / $it_last_pct75 * 100" | bc)
      pct95_change=$(echo "scale=4; ($reportItem_pct95 - $it_last_pct95) / $it_last_pct95 * 100" | bc)
      pct99_change=$(echo "scale=4; ($reportItem_pct99 - $it_last_pct99) / $it_last_pct99 * 100" | bc)
      mean_change=$(echo "scale=4; ($reportItem_mean - $it_last_mean) / $it_last_mean * 100" | bc)

      pct50_change_ms=$(echo "($reportItem_pct50 - $it_last_pct50)" | bc)
      pct75_change_ms=$(echo "($reportItem_pct75 - $it_last_pct75)" | bc)
      pct95_change_ms=$(echo "($reportItem_pct95 - $it_last_pct95)" | bc)
      pct99_change_ms=$(echo "($reportItem_pct99 - $it_last_pct99)" | bc)
      mean_change_ms=$(echo "($reportItem_mean - $it_last_mean)" | bc)

      pct50_change_rounded=$(printf "%.1f" "$pct50_change")
      pct75_change_rounded=$(printf "%.1f" "$pct75_change")
      pct95_change_rounded=$(printf "%.1f" "$pct95_change")
      pct99_change_rounded=$(printf "%.1f" "$pct99_change")
      mean_change_rounded=$(printf "%.1f" "$mean_change")

      # Append the item to the general report
      general_report=$(echo "$general_report" | jq --arg name "$reportItem_name" \
        --arg pct50 "$reportItem_pct50" --arg pct75 "$reportItem_pct75" --arg pct95 "$reportItem_pct95" --arg pct99 "$reportItem_pct99" \
        --arg mean "$reportItem_mean" --arg pct50_change "$pct50_change_rounded" --arg last_pct50 "$it_last_pct50" \
        --arg pct75_change "$pct75_change_rounded" --arg last_pct75 "$it_last_pct75" \
        --arg pct95_change "$pct95_change_rounded" --arg last_pct95 "$it_last_pct95" \
        --arg pct99_change "$pct99_change_rounded" --arg last_pct99 "$it_last_pct99" \
        --arg mean_change "$mean_change_rounded" --arg last_mean "$it_last_mean" \
        --arg pct50_change_ms "$pct50_change_ms" --arg pct75_change_ms "$pct75_change_ms" \
        --arg pct95_change_ms "$pct95_change_ms" --arg pct99_change_ms "$pct99_change_ms" --arg mean_change_ms "$mean_change_ms" \
        '.items += [{"name": $name, "pct50": $pct50, "last_pct50": $last_pct50, "pct50_change": $pct50_change, "pct50_change_ms": $pct50_change_ms, "pct75": $pct75, "last_pct75": $last_pct75, "pct75_change": $pct75_change, "pct75_change_ms": $pct75_change_ms, "pct95": $pct95, "last_pct95": $last_pct95, "pct95_change": $pct95_change, "pct95_change_ms": $pct95_change_ms, "pct99": $pct99, "last_pct99": $last_pct99, "pct99_change": $pct99_change, "pct99_change_ms": $pct99_change_ms, "mean": $mean, "last_mean": $last_mean, "mean_change": $mean_change, "mean_change_ms": $mean_change_ms}]')


      if (( $(echo "$pct50_change_rounded >= 20" | bc -l) || $(echo "$pct95_change_rounded >= 20" | bc -l) )); then
        general_report=$(echo "$general_report" | jq '.status = "DEGRADATION"')
      elif (( $(echo "$pct50_change_rounded >= 10" | bc -l) || $(echo "$pct95_change_rounded >= 10" | bc -l) )); then
        if [[ $(echo "$general_report" | jq -r '.status') != "DEGRADATION" ]]; then
          general_report=$(echo "$general_report" | jq '.status = "WARN"')
        fi
      fi
    fi
  done

  status=$(echo "$general_report" | jq -r ".status")
  string_report+="Result: $status \n\n"
  # Generate the summary string report
  for (( index=0; index<$(echo "$general_report" | jq '.items | length'); index++ )); do
    item_name=$(echo "$general_report" | jq -r ".items[$index].name")
    item_pct50=$(echo "$general_report" | jq -r ".items[$index].pct50")
    last_pct50=$(echo "$general_report" | jq -r ".items[$index].last_pct50")
    item_pct50_change=$(echo "$general_report" | jq -r ".items[$index].pct50_change")
    item_pct50_change_ms=$(echo "$general_report" | jq -r ".items[$index].pct50_change_ms")
    item_pct75=$(echo "$general_report" | jq -r ".items[$index].pct75")
    last_pct75=$(echo "$general_report" | jq -r ".items[$index].last_pct75")
    item_pct75_change=$(echo "$general_report" | jq -r ".items[$index].pct75_change")
    item_pct75_change_ms=$(echo "$general_report" | jq -r ".items[$index].pct75_change_ms")
    item_pct95=$(echo "$general_report" | jq -r ".items[$index].pct95")
    last_pct95=$(echo "$general_report" | jq -r ".items[$index].last_pct95")
    item_pct95_change=$(echo "$general_report" | jq -r ".items[$index].pct95_change")
    item_pct95_change_ms=$(echo "$general_report" | jq -r ".items[$index].pct95_change_ms")
    item_pct99=$(echo "$general_report" | jq -r ".items[$index].pct99")
    last_pct99=$(echo "$general_report" | jq -r ".items[$index].last_pct99")
    item_pct99_change=$(echo "$general_report" | jq -r ".items[$index].pct99_change")
    item_pct99_change_ms=$(echo "$general_report" | jq -r ".items[$index].pct99_change_ms")
    item_mean=$(echo "$general_report" | jq -r ".items[$index].mean")
    last_mean=$(echo "$general_report" | jq -r ".items[$index].last_mean")
    item_mean_change=$(echo "$general_report" | jq -r ".items[$index].mean_change")
    item_mean_change_ms=$(echo "$general_report" | jq -r ".items[$index].mean_change_ms")
    string_report+="$item_name:\n- 50pct: $item_pct50, last_50pct: $last_pct50, change: $item_pct50_change%, $item_pct50_change_ms ms\n- 75pct: $item_pct75, last_75pct: $last_pct75, change: $item_pct75_change%, $item_pct75_change_ms ms\n- 95pct: $item_pct95, last_95pct: $last_pct95, change: $item_pct95_change%, $item_pct95_change_ms ms\n- 99pct: $item_pct99, last_99pct: $last_pct99, change: $item_pct99_change%, $item_pct99_change_ms ms\n- mean: $item_mean, last_mean: $last_mean, change: $item_mean_change%, $item_mean_change_ms ms\n\n"
  done

else
  string_report="File Report.json or Old_Report.json are missing.\n"
fi
echo "$general_report" > MailReport.json
echo "$string_report" > Comparison.txt
cat Comparison.txt
