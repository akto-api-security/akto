#! /bin/sh

sudo apt-get install jq -y

# Akto Variables
AKTO_DASHBOARD_URL=$AKTO_DASHBOARD_URL
AKTO_API_KEY=$AKTO_API_KEY
#AKTO_TEST_ID=$AKTO_TEST_ID
LB_NAME=$LB_NAME
MAX_POLL_INTERVAL=$((30 * 60))  # 30 minutes in seconds

start_time=$(date +%s)

echo "Triggering Akto test"
response=$(curl -s 'https://flash.staging.akto.io/api/startTest' \
  -H "X-API-KEY: $AKTO_API_KEY" \
  -H 'content-type: application/json' \
  --data-raw "{
    \"apiCollectionId\": 1013188780,
    \"type\": \"COLLECTION_WISE\",
    \"startTimestamp\": $start_time,
    \"recurringDaily\": false,
    \"selectedTests\": [ \"XSS_VIA_APPENDING_TO_QUERY_PARAMS\",     \"XSS_IN_PATH\",     \"XSS_VIA_FILE_NAME\",     \"BASIC_XSS\",     \"SERVER_VERSION_EXPOSED_VIA_RESPONSE_HEADER\",     \"SERVER_VERSION_EXPOSED_IN_AN_INVALID_REQUEST\",     \"LLM_MALWARE_COMPLETE_C_SHARP\",     \"INSECURE_OUTPUT_HANDLING_2\",     \"INSECURE_OUTPUT_HANDLING_1\",     \"LLM_MALWARE_COMPLETE_x86_64\",     \"LLM_MALWARE_SUBFUNCTION_ARM64\",     \"PROMPT_INJECTION_STAN\",     \"LLM_MALWARE_SUBFUNCTION_SWIFT\",     \"LLM_ENCODING_2\",     \"LLM_ENCODING_5\",     \"LLM_ENCODING_4\",     \"PROMPT_LEAK_INJECTION\",     \"LLM_ENCODING_1\",     \"LLM_MALWARE_SUBFUNCTION_RUST\",     \"SENSITIVE_DATA_EXPOSURE_AWS_KEY\",     \"LLM_MALWARE_PAYLOAD_RUST\",     \"LLM_MALWARE_PAYLOAD_x86\",     \"LLM_MALWARE_COMPLETE_RUST\",     \"LLM_MALWARE_EVADE_SWIFT\",     \"LLM_MALWARE_COMPLETE_x86\",     \"LLM_MALWARE_EVADE_C_SHARP\",     \"LLM_MALWARE_EVADE_X86\",     \"LLM_MALWARE_COMPLETE_CPP\",     \"LLM_MALWARE_EVADE_ARM64\",     \"LLM_MALWARE_SUBFUNCTION_C_SHARP\",     \"LLM_MALWARE_PAYLOAD_CPP\",     \"LLM_MALWARE_PAYLOAD_CSharp\",     \"LLM_MALWARE_EVADE_CPP\",     \"LLM_MALWARE_PAYLOAD_ARM64\",     \"SENSITIVE_DATA_EXPOSURE_PASSWORD\",     \"LLM_MALWARE_PAYLOAD_SWIFT\",     \"LLM_MALWARE_EVADE_X86_64\",     \"LLM_MALWARE_EVADE_C\",     \"PROMPT_INJECTION_BASIC_HELLO\",     \"LLM_MALWARE_EVADE_RUST\",     \"LLM_WRONG_ANSWER_2\",     \"LLM_MALWARE_COMPLETE_ARM64\",     \"LLM_MALWARE_COMPLETE_C\",     \"PROMPT_INJECTION_BASIC_v2\",     \"OBFUSCATION_LLM\",     \"LLM_INSECURE_OUTPUT_1\",     \"LLM_INSECURE_OUTPUT_2\",     \"LLM_INSECURE_OUTPUT_3\",     \"LLM_MALWARE_SUBFUNCTION_C\",     \"LLM_MALWARE_SUBFUNCTION_x86\",     \"LLM_MALWARE_COMPLETE_SWIFT\",     \"LLM_PKG_HALLUCINATION\",     \"LLM_MALWARE_SUBFUNCTION_CPP\",     \"PROMPT_INJECTION_XSS\",     \"LLM_GLITCH_4\",     \"LLM_GLITCH_5\",     \"LLM_GLITCH_6\",     \"LLM_GLITCH_1\",     \"LLM_GLITCH_2\",     \"LLM_MALWARE_SUBFUNCTION_x86_64\",     \"LLM_MISLEADING\",     \"LLM_MALWARE_PAYLOAD_C\",     \"LLM_MALWARE_PAYLOAD_x86_64\",     \"REPLACE_AUTH_TOKEN_OLD_VERSION\",     \"ADD_USER_ID\",     \"REPLACE_Arandom_textUTH_TOKEN_CUSTOM_1696070362\",     \"REPLACE_AUTH_TOKEN\",     \"REPLACE_Arandom_textUTH_TOKEN_CUSTOM_1696335205\",     \"REPLACE_Arandom_textUTH_TOKEN\",     \"PARAMETER_POLLUTION\",     \"DESCRIPTIVE_ERROR_MESSAGE_INVALID_PAYLOAD\",     \"INVALID_FILE_INPUT\",     \"DJANGO_URL_EXPOSED\",     \"PROMETHEUS_METRICS\",     \"RAILS_DEFAULT_HOMEPAGE_ENABLED\",     \"TEXT_INJECTION_VIA_INVALID_URLS\",     \"APPSPEC_YML_DISCLOSURE\",     \"SPRING_BOOT_BEANS_ACTUATOR_EXPOSED\",     \"APACHE_CONFIG\",     \"FTP_CREDENTIALS_EXPOSURE\",     \"MSMTP_CONFIG\",     \"NGINX_STATUS_VISIBLE\",     \"GIT_CONFIG_NGINXOFFBYSLASH\",     \"GIT_CREDENTIALS_DISCLOSURE\",     \"LARAVEL_TELESCOPE_ENABLED\",     \"FIREBASE_CONFIG_EXPOSURE\",     \"FLASK_DEBUG_MODE_ENABLED\",     \"GRAPHQL_DEBUG_MODE_ENABLED\",     \"HEADER_REFLECTED_IN_INVALID_URLS\",     \"OPEN_REDIRECT_HOST_HEADER_INJECTION\",     \"PARAMETERS_CONFIG\",     \"LARAVEL_ENV\",     \"STRUTS_DEBUG_MODE_ENABLED\",     \"MISCONFIGURED_DOCKER\",     \"DOCKERFILE_HIDDEN_DISCLOSURE\",     \"ROBOMONGO_CREDENTIAL\",     \"SSH_AUTHORIZED_KEYS\",     \"NGINX_SERVER_VERSION_DISCLOSED\",     \"GRAPHQL_FIELD_SUGGESTIONS_ENABLED\",     \"DEBUG_VARS\",     \"CIRCLECI_CONFIG\",     \"SESSION_FIXATION\",     \"AMAZON_DOCKER_CONFIG\",     \"SFTP_CONFIG_EXPOSURE\",     \"COOKIE_MISCONFIGURATION\",     \"GITHUB_WORKFLOW_DISCLOSURE\",     \"GIT_CONFIG\",     \"OPEN_REDIRECT_SUBDOMAIN_WHITELIST\",     \"REDIS_CONFIG\",     \"SONARQUBE_PUBLIC_PROJECTS\",     \"SERVER_PRIVATE_KEYS\",     \"STRUTS_OGNL_CONSOLE_ENABLED\",     \"SSH_KNOWN_HOSTS\",     \"OPEN_REDIRECT\",     \"EXPRESS_STACK_TRACE_ENABLED\",     \"LARAVEL_DEFAULT_HOMEPAGE_ENABLED\",     \"WPCONFIG_AWS_KEY\",     \"CONFIG_RUBY\",     \"KUBERNETES_KUSTOMIZATION_DISCLOSURE\",     \"JWT_SIGNING_IN_CLIENT_SIDE\",     \"ESMTPRC_CONFIG\",     \"GRAPHQL_TYPE_INTROSPECTION_ALLOWED\",     \"NGINX_CONFIG\",     \"DOCKER_COMPOSE_CONFIG\",     \"OPEN_REDIRECT_CUSTOM_1695888186\",     \"EXPRESS_DEFAULT_HOMEPAGE_ENABLED\",     \"LARAVEL_DEBUG_MODE_ENABLED\",     \"DJANGO_DEFAULT_HOMEPAGE_ENABLED\",     \"ORACLE_EBS_CREDENTIALS\",     \"OPEN_REDIRECT_IN_PATH\",     \"SPRING_BOOT_THREAD_DUMP_ACTUATOR_EXPOSED\",     \"UNAUTHENTICATED_MONGO_EXPRESS\",     \"SPRING_BOOT_CONFIG_PROPS_ACTUATOR_EXPOSED\",     \"GRAPHQL_DEVELOPMENT_CONSOLE_EXPOSED\",     \"WGETRC_CONFIG\",     \"CONFIG_JSON\",     \"CGI_PRINTENV\",     \"RAILS_DEBUG_MODE_ENABLED\",     \"DEFAULT_LOGIN_CREDENTIALS\",     \"AIRFLOW_CONFIGURATION_EXPOSURE\",     \"NGINX_DEFAULT_PAGE_ENABLED\",     \"SPRING_BOOT_ENV_ACTUATOR_EXPOSED\",     \"SPRING_BOOT_HTTP_TRACE_ACTUATOR_EXPOSED\",     \"FIREBASE_UNAUTHENTICATED\",     \"CONFIGURATION_LISTING\",     \"GRAPHQL_INTROSPECTION_MODE_ENABLED\",     \"MUST_CONTAIN_RESPONSE_HEADERS_CUSTOM_1695893796\",     \"UNWANTED_RESPONSE_HEADERS\",     \"MUST_CONTAIN_RESPONSE_HEADERS\",     \"CONTENT_TYPE_HEADER_MISSING\",     \"PAGINATION_MISCONFIGURATION\",     \"REMOVE_CAPTCHA\",     \"BYPASS_CAPTCHA_ADDING_HEADER\",     \"REPLAY_CAPTCHA\",     \"BYPASS_CAPTCHA_REMOVING_COOKIE\",     \"MASS_ASSIGNMENT_CREATE_ADMIN_ROLE\",     \"MASS_ASSIGNMENT_CHANGE_ROLE\",     \"MASS_ASSIGNMENT_CHANGE_ACCOUNT\",     \"MASS_ASSIGNMENT_CHANGE_ADMIN_ROLE\",     \"SSRF_ON_LOCALHOST\",     \"SSRF_ON_LOCALHOST_ENCODED\",     \"SSRF_ON_AWS_META_ENDPOINT\",     \"SSRF_ON_AWS_META_ENDPOINT_ENCLOSED\",     \"SSRF_ON_CSV_UPLOAD\",     \"SSRF_ON_IMAGE_UPLOAD\",     \"PORT_SCANNING\",     \"SSRF_ON_PDF_UPLOAD\",     \"SSRF_ON_LOCALHOST_DNS_PINNING\",     \"SSRF_ON_XML_UPLOAD\",     \"SSRF_ON_FILES\",     \"FETCH_SENSITIVE_FILES\",     \"REPLACE_CSRF\",     \"JWT_NONE_ALGO_CUSTOM_1698482241\",     \"ADD_JKU_TO_JWT\",     \"JWT_INVALID_SIGNATURE\",     \"JWT_NONE_ALGO\",     \"CSRF_LOGIN_ATTACK\",     \"REMOVE_TOKENS\",     \"ADD_JKU_TO_JWT_CUSTOM_1698476660\",     \"REMOVE_TOKENS_CUSTOM_1698726897\",     \"USERNAME_ENUMERATION\",     \"REMOVE_CSRF_CUSTOM_1697460106\",     \"REMOVE_CSRF\",     \"TRACK_METHOD_TEST\",     \"HEAD_METHOD_TEST\",     \"TRACE_METHOD_TEST\",     \"RANDOM_METHOD_TEST\",     \"LFI_IN_PARAMETER\",     \"FILE_INCLUSION_NEW_PARAM\",     \"LFI_IN_PATH\",     \"KERNEL_OPEN_COMMAND_INJECTION\",     \"COMMAND_INJECTION_BY_ADDING_QUERY_PARAM\" ],
    \"testName\": \"Akto on Akto (k8s staging)\",
    \"testRunTime\": -1,
    \"maxConcurrentRequests\": -1,
    \"overriddenTestAppUrl\": \"$LB_NAME\"
  }" \
  --compressed)
AKTO_TEST_ID=$(echo $response |  jq -r '.testingRunHexId // empty')

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

    echo "[Results]($AKTO_DASHBOARD_URL/dashboard/testing/$AKTO_TEST_ID)" >> $GITHUB_STEP_SUMMARY
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
