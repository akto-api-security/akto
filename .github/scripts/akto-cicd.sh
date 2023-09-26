#! /bin/sh

sudo apt-get install jq -y

# Akto Variables
AKTO_DASHBOARD_URL=$AKTO_DASHBOARD_URL
AKTO_API_KEY=$AKTO_API_KEY
AKTO_TEST_ID=$AKTO_TEST_ID
MAX_POLL_INTERVAL=$((30 * 60))  # 30 minutes in seconds

start_time=$(date +%s)

echo "### Akto test summary" >> $GITHUB_STEP_SUMMARY

while true; do
  current_time=$(date +%s)
  elapsed_time=$((current_time - start_time))
  
  if ((elapsed_time >= MAX_POLL_INTERVAL)); then
    echo "Max poll interval reached. Exiting."
    break
  fi

  current_time=$(date +%s)
  recency_period=$((60 * 24 * 60 * 60))
  start_timestamp=$((current_time - recency_period / 9))
  end_timestamp=$current_time
  
  response=$(curl -s "$AKTO_DASHBOARD_URL/api/fetchTestingRunResultSummaries" \
      --header 'content-type: application/json' \
      --header "X-API-KEY: $AKTO_API_KEY" \
      --data "{
          \"startTimestamp\": \"$start_timestamp\",
          \"endTimestamp\": \"$end_timestamp\",
          \"testingRunHexId\": \"$AKTO_TEST_ID\"
      }")

  state=$(echo "$response" | jq -r '.testingRunResultSummaries[0].state // empty')

  if [[ "$state" == "COMPLETED" ]]; then
    count=$(echo "$response" | jq -r '.testingRunResultSummaries[0].countIssues // empty')
    high=$(echo "$response" | jq -r '.testingRunResultSummaries[0].countIssues.HIGH // empty')
    medium=$(echo "$response" | jq -r '.testingRunResultSummaries[0].countIssues.MEDIUM // empty')
    low=$(echo "$response" | jq -r '.testingRunResultSummaries[0].countIssues.LOW // empty')

    echo "[Results]($AKTO_DASHBOARD_URL/dashboard/testing/$AKTO_TEST_ID/results)" >> $GITHUB_STEP_SUMMARY
    echo "HIGH: $high" >> $GITHUB_STEP_SUMMARY
    echo "MEDIUM: $medium" >> $GITHUB_STEP_SUMMARY
    echo "LOW: $low"  >> $GITHUB_STEP_SUMMARY

    if [ "$high" -gt 0 ] || [ "$medium" -gt 0 ] || [ "$low" -gt 0 ] ; then
        echo "Vulnerabilities found!!" >> $GITHUB_STEP_SUMMARY
        #exit 1
        exit 0
    fi
    break
  elif [[ "$state" == "STOPPED" ]]; then
    echo "Test stopped" >> $GITHUB_STEP_SUMMARY
    exit 1
    break
  else
    echo "Waiting for akto test to be completed..."
    sleep 5  # Adjust the polling interval as needed
  fi
done
