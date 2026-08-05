#!/bin/bash

cat jobs.txt
count=0
JOB_ARRAY=()

if [[ "$(uname)" == "Darwin" ]]; then
  while IFS= read -r job; do
    JOB_ARRAY+=("$job")
  done < jobs.txt
else
  mapfile -t JOB_ARRAY < jobs.txt
fi
echo "$CI_PROJECT_URL/-/jobs/${JOB_ARRAY[0]}/artifacts/download"
results=$(cat Comparison.txt)
      cat << EOF > payload.json
      {
        "@type": "MessageCard",
        "@context": "http://schema.org/extensions",
        "themeColor": "0076D7",
        "summary": "Performance Test - ${SIMULATION} is FINISHED",
        "sections": [
          {
            "activityTitle": "AI Dial Admin Performance Test started by $CI_PIPELINE_SOURCE is done",
            "activitySubtitle": "Scenario: ${SCENARIO_NAME}",
            "activityImage": "https://alexandreesl.com/wp-content/uploads/2020/02/gatling.png",
            "facts": [
              {
                "name": "DIAL_CHART_VERSION",
                "value": "${DIAL_CHART_VERSION}"
              },
              {
                "name": "DIAL_ADMIN_CHART_VERSION",
                "value": "${DIAL_ADMIN_CHART_VERSION}"
              },
              {
                "name": "Parameters",
                "value": "Users = ${USERS}, Duration = ${DURATION}"
              },
              {
                "name": "Status",
                "value": "COMPLETED"
              },
              {
                "name": "Results",
                "value": "$results"
              }
            ],
            "markdown": true
          }
        ],
              "potentialAction": [
          {
            "@type": "ActionCard",
            "name": "GitLab",
            "actions": [{
              "@type": "OpenUri",
              "name": "Open Gitlab job",
              "targets": [{
                "os": "default",
                "uri": "${CI_PIPELINE_URL}"
              }]
            }]},
          {
            "@type": "ActionCard",
            "name": "Gatling Report",
            "actions": [{
              "@type": "OpenUri",
              "name": "Download Gatling Report",
              "targets": [{
                "os": "default",
                "uri": "$CI_PROJECT_URL/-/jobs/${JOB_ARRAY[0]}/artifacts/download"
              }]
            }]},
          {
            "@type": "ActionCard",
            "name": "Parsed data",
            "actions": [{
              "@type": "OpenUri",
              "name": "Open Parsed XLS Report",
              "targets": [{
                "os": "default",
                "uri": "$CI_PROJECT_URL/-/jobs/${JOB_ARRAY[1]}/artifacts/download"
              }]
            }]},
          {
            "@type": "ActionCard",
            "name": "Logs",
            "actions": [{
              "@type": "OpenUri",
              "name": "Open Logs",
              "targets": [{
                "os": "default",
                "uri": "$CI_PROJECT_URL/-/jobs/${JOB_ARRAY[0]}/artifacts/file/gatlingRunLogs.log"
              }]
            }]}
      ]
      }
EOF
      curl -H 'Content-Type: application/json' -d @payload.json ${TEAMS_WEBHOOK_URL}
