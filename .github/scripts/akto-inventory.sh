#! /bin/sh

#sudo apt-get install jq -y

# Akto Variables
AKTO_DASHBOARD_URL=$AKTO_DASHBOARD_URL
AKTO_API_KEY=$AKTO_API_KEY

response=$(curl -s "$AKTO_DASHBOARD_URL/api/generateOpenApiFile" \
    --header 'content-type: application/json' \
    --header "X-API-KEY: $AKTO_API_KEY" \
    --data "{
        \"apiCollectionId\": \"-1753579810\"
    }")

open_api_string=$(echo "$response" | jq -r '.openAPIString')
echo $open_api_string > test.json
jq 'del(.paths[] | .[] | .get.responses["200"].headers)' test.json > output.json


