#!/bin/bash

sanitize_numeric() {
  local value="$1"
  if [[ -z "$value" || "$value" == "-" ]]; then
    echo 0
  else
    echo "$value"
  fi
}

# Define the base directory where the Gatling reports are located
base_dir="build/reports/gatling"

# Find the full path of the `index.html` file
REPORT=$(find "$base_dir" -type f -name "index.html" | head -n 1)

# Check if the file was found
if [[ -z "$REPORT" ]]; then
  echo "Error: No index.html file found in $base_dir."
  exit 1
fi

# Debugging: Print the input file path
echo "Input file: $REPORT"

# Output file for the Markdown table
output_file="gatling_stats.md"
output_JSON="Report_tmp.json"

# Date/Duration/Description
echo "Date: "$(awk '/Date: <\/span>/ {getline; print; exit}' "$REPORT" | sed -E 's/<span[^>]*>|<\/span>//g') > "$output_file"
echo "Duration: "$(awk '/Duration: <\/span>/ {getline; print; exit}' "$REPORT" | sed -E 's/<span[^>]*>|<\/span>//g') >> "$output_file"
echo "Description: "$(awk '/Description: <\/span>/ {getline; print; exit}' "$REPORT" | sed -E 's/<span[^>]*>|<\/span>//g') >> "$output_file"
echo "" >> "$output_file"
# Count the number of requests in report
counter=$(grep -o '<td class="value total col-2">' "$REPORT" | wc -l)

# Initialize the Markdown table
echo "------------------------------------------------------------------------------------------------------------------------------------------------------" >> "$output_file"
echo "| Requests                         | Total |  OK  |  KO  | %KO | Cnt/s | Min   | 50th pct | 75th pct | 95th pct | 99th pct | Max   | Mean  | Std Dev |" >> "$output_file"
echo "|----------------------------------|-------|------|------|-----|-------|-------|----------|----------|----------|----------|-------|-------|---------|" >> "$output_file"
echo "[" > "$output_JSON"

for ((i=1; i<=counter; i++)); do
  requests=$(grep -o '<span[^>]*class="ellipsed-name">[^<]*</span>' "$REPORT" | sed 's/.*class="ellipsed-name">\([^<]*\)<\/span>.*/\1/' | sed -n "${i}p")
  total=$(sanitize_numeric "$(grep -o '<td class="value total col-2">[^<]*</td>' "$REPORT" | sed 's/.*class="value total col-2">\([^<]*\)<\/td>.*/\1/' | sed -n "${i}p")")
  ok=$(sanitize_numeric "$(grep -o '<td class="value ok col-3">[^<]*</td>' "$REPORT" | sed 's/.*class="value ok col-3">\([^<]*\)<\/td>.*/\1/' | sed -n "${i}p")")
  ko=$(sanitize_numeric "$(grep -o '<td class="value ko col-4">[^<]*</td>' "$REPORT" | sed 's/.*class="value ko col-4">\([^<]*\)<\/td>.*/\1/' | sed -n "${i}p")")
  percent_ko=$(sanitize_numeric "$(grep -o '<td class="value ko col-5">[^<]*</td>' "$REPORT" | sed 's/.*class="value ko col-5">\([^<]*\)<\/td>.*/\1/' | sed -n "${i}p")")
  cnt_s=$(sanitize_numeric "$(grep -o '<td class="value total col-6">[^<]*</td>' "$REPORT" | sed 's/.*class="value total col-6">\([^<]*\)<\/td>.*/\1/' | sed -n "${i}p")")
  min=$(sanitize_numeric "$(grep -o '<td class="value total col-7">[^<]*</td>' "$REPORT" | sed 's/.*class="value total col-7">\([^<]*\)<\/td>.*/\1/' | sed -n "${i}p")")
  pct_50=$(sanitize_numeric "$(grep -o '<td class="value total col-8">[^<]*</td>' "$REPORT" | sed 's/.*class="value total col-8">\([^<]*\)<\/td>.*/\1/' | sed -n "${i}p")")
  pct_75=$(sanitize_numeric "$(grep -o '<td class="value total col-9">[^<]*</td>' "$REPORT" | sed 's/.*class="value total col-9">\([^<]*\)<\/td>.*/\1/' | sed -n "${i}p")")
  pct_95=$(sanitize_numeric "$(grep -o '<td class="value total col-10">[^<]*</td>' "$REPORT" | sed 's/.*class="value total col-10">\([^<]*\)<\/td>.*/\1/' | sed -n "${i}p")")
  pct_99=$(sanitize_numeric "$(grep -o '<td class="value total col-11">[^<]*</td>' "$REPORT" | sed 's/.*class="value total col-11">\([^<]*\)<\/td>.*/\1/' | sed -n "${i}p")")
  max=$(sanitize_numeric "$(grep -o '<td class="value total col-12">[^<]*</td>' "$REPORT" | sed 's/.*class="value total col-12">\([^<]*\)<\/td>.*/\1/' | sed -n "${i}p")")
  mean=$(sanitize_numeric "$(grep -o '<td class="value total col-13">[^<]*</td>' "$REPORT" | sed 's/.*class="value total col-13">\([^<]*\)<\/td>.*/\1/' | sed -n "${i}p")")
  std_dev=$(sanitize_numeric "$(grep -o '<td class="value total col-14">[^<]*</td>' "$REPORT" | sed 's/.*class="value total col-14">\([^<]*\)<\/td>.*/\1/' | sed -n "${i}p")")
  # Append to Markdown table
  printf "| %-32s | %-5s | %-4s | %-4s | %-3s | %-5s | %-5s | %-8s | %-8s | %-8s | %-8s | %-5s | %-5s | %-7s |\n" \
      "$requests" "$total" "$ok" "$ko" "$percent_ko" "$cnt_s" "$min" "$pct_50" "$pct_75" "$pct_95" "$pct_99" "$max" "$mean" "$std_dev" >> "$output_file"
  # Append to JSON report
  echo "{\"name\": \"$requests\", \"total\": $total, \"ok\": $ok, \"ko\": $ko, \"rps\": $cnt_s, \"min_v\": $min, \"pct50\": $pct_50, \"pct75\": $pct_75, \"pct95\": $pct_95, \"pct99\": $pct_99, \"max_v\": $max, \"mean\": $mean, \"stdev\": $std_dev}" >> "$output_JSON"
  if [ $i -lt $counter ]; then
    echo "," >> "$output_JSON"
  fi
done
echo "]" >> "$output_JSON"
tr -d '\n' < "$output_JSON" > Report.json
rm "$output_JSON"
echo "------------------------------------------------------------------------------------------------------------------------------------------------------" >> "$output_file"
echo "Markdown table has been generated in $output_file"
cat "$output_file"
