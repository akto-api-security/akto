#!/bin/sh

# Install the jq tool for JSON processing
sudo apt-get install jq -y

# Akto Configuration
AKTO_DASHBOARD_URL=$AKTO_DASHBOARD_URL
AKTO_API_KEY=$AKTO_API_KEY
AKTO_TEST_ID=$AKTO_TEST_ID
MAX_POLL_INTERVAL=$((30 * 60))  # Maximum polling interval: 30 minutes in seconds

# Email Configuration for Notifications
NOTIFICATION_EMAIL="youremail@example.com"

# Function to send an email notification
send_notification() {
  local subject="$1"
  local message="$2"
  echo "$message" | mail -s "$subject" "$NOTIFICATION_EMAIL"
}

# Record the start time of the script
start_time=$(date +%s)

# Log summary for GitHub Actions
echo "### Akto Test Summary" >> $GITHUB_STEP_SUMMARY

while true; do
  # Calculate elapsed time
  current_time=$(date +%s)
  elapsed_time=$((current_time - start_time))
  
  # Check if the maximum polling interval has been reached
  if ((elapsed_time >= MAX_POLL_INTERVAL)); then
    echo "Max poll interval reached. Exiting."
    break
  fi

  # Calculate time window for querying Akto results
  current_time=$(date +%s)
  recency_period=$((60 * 24 * 60 * 60))
  start_timestamp=$((current_time - recency_period / 9))
  end_timestamp=$current_time
  
  # Make an API request to Akto
  response=$(curl -s "$AKTO_DASHBOARD_URL/api/fetchTestingRunResultSummaries" \
      --header 'content-type: application/json' \
      --header "X-API-KEY: $AKTO_API_KEY" \
      --data "{
          \"startTimestamp\": \"$start_timestamp\",
          \"endTimestamp\": \"$end_timestamp\",
          \"testingRunHexId\": \"$AKTO_TEST_ID\"
      }")

  # Extract the state of the testing run from the response
  state=$(echo "$response" | jq -r '.testingRunResultSummaries[0].state // empty')

  # Check the state of the testing run
  if [[ "$state" == "COMPLETED" ]]; then
    # Extract issue counts
    count=$(echo "$response" | jq -r '.testingRunResultSummaries[0].countIssues // empty')
    high=$(echo "$response" | jq -r '.testingRunResultSummaries[0].countIssues.HIGH // empty')
    medium=$(echo "$response" | jq -r '.testingRunResultSummaries[0].countIssues.MEDIUM // empty')
    low=$(echo "$response" | jq -r '.testingRunResultSummaries[0].countIssues.LOW // empty')

    # Log test results
    echo "[Results]($AKTO_DASHBOARD_URL/dashboard/testing/$AKTO_TEST_ID/results)" >> $GITHUB_STEP_SUMMARY
    echo "HIGH: $high" >> $GITHUB_STEP_SUMMARY
    echo "MEDIUM: $medium" >> $GITHUB_STEP_SUMMARY
    echo "LOW: $low"  >> $GITHUB_STEP_SUMMARY

    # Check for vulnerabilities and send notifications
    if [ "$high" -gt 0 ] || [ "$medium" -gt 0 ] || [ "$low" -gt 0 ] ; then
        echo "Vulnerabilities found!!" >> $GITHUB_STEP_SUMMARY
        send_notification "Akto Test Completed with Vulnerabilities" "HIGH: $high, MEDIUM: $medium, LOW: $low\n[Results]($AKTO_DASHBOARD_URL/dashboard/testing/$AKTO_TEST_ID/results)"
        #exit 1
        exit 0
    fi
    break
  elif [[ "$state" == "STOPPED" ]]; then
    # Log test stopped
    echo "Test stopped" >> $GITHUB_STEP_SUMMARY
    send_notification "Akto Test Stopped" "The Akto test has been stopped."
    exit 1
    break
  else
    # Continue polling until the test is completed or stopped
    echo "Waiting for Akto test to be completed..."
    sleep 5  # Adjust the polling interval as needed
  fi
done
