#! /bin/sh
#!/bin/bash
#**********************************************************************
#
# TESTSIGMA_API_KEY -> API key generated under Testsigma App >> Configuration >> API Keys
#
# TESTSIGMA_TEST_PLAN_ID -> Testsigma Testplan ID.
# You can get this from Testsigma App >> Test Plans >> <TEST_PLAN_NAME> >> CI/CD Integration
#
# MAX_WAIT_TIME_FOR_SCRIPT_TO_EXIT -> Maximum time in minutes the script will wait for TEST Plan execution to complete. 
# The sctript will exit if the Maximum time is exceeded. However, the Test Plan will continue to run. 
# You can check test results by logging to Testsigma.
#
# JUNIT_REPORT_FILE_PATH -> File name with directory path to save the report.
# For Example, <DIR_PATH>/report.xml, ./report.xml
# 
# RUNTIME_DATA_INPUT -> Specify runtime parameters/variables to be used in the tests in comma-separated manner
# For example, "url=https://the-internet.herokuapp.com/login,variable1=value1"
#
# BUILD_NO -> Specify Build number if you want to track the builds in Testsigma. It will show up in the Test Results page
# For example, we are using $(date +"%Y%m%d%H%M") to use current data and time as build number.
#
#********START USER_INPUTS*********
TESTSIGMA_API_KEY=${TESTSIGMA_API_KEY}
TESTSIGMA_TEST_PLAN_ID=${TESTSIGMA_TEST_PLAN_ID}
MAX_WAIT_TIME_FOR_SCRIPT_TO_EXIT=30
JUNIT_REPORT_FILE_PATH=./junit-report-$(date +"%Y%m%d%H%M").xml
RUNTIME_DATA_INPUT="url=https://the-internet.herokuapp.com/login,test=1221"
BUILD_NO=$(date +"%Y%m%d%H%M")
#********END USER_INPUTS***********
 
#********GLOBAL variables**********
POLL_COUNT=30
SLEEP_TIME=$(((MAX_WAIT_TIME_FOR_SCRIPT_TO_EXIT*60)/$POLL_COUNT))
JSON_REPORT_FILE_PATH=./testsigma.json
TESTSIGMA_TEST_PLAN_REST_URL=${TS_DASHBOARD_URL}/api/v1/test_plan_results
TESTSIGMA_JUNIT_REPORT_URL=${TS_DASHBOARD_URL}/api/v1/reports/junit
MAX_WAITTIME_EXCEEDED_ERRORMSG="Given Maximum Wait Time of $MAX_WAIT_TIME_FOR_SCRIPT_TO_EXIT minutes exceeded waiting for the Test Run completion. 
Please log-in to Testsigma to check Test Plan run results. You can visit the URL specified in \"app_url\" JSON parameter in the response to go to the Test Plan results page directly. 
For example, \"app_url\":\"https://dev.testsigma.com/#/projects/31/applications/53/version/72/report/executions/197/runs/819/environments\""
#**********************************
 
#Read arguments
for i in "$@"
  do
  case $i in
    -k=*|--apikey=*)
    TESTSIGMA_API_KEY="${i#*=}"
    shift
    ;;
    -i=*|--testplanid=*)
    TESTSIGMA_TEST_PLAN_ID="${i#*=}"
    shift
    ;;
    -t=*|--maxtimeinmins=*)
    MAX_WAIT_TIME_FOR_SCRIPT_TO_EXIT="${i#*=}"
    shift
    ;;
    -r=*|--reportfilepath=*)
    JUNIT_REPORT_FILE_PATH="${i#*=}"
    shift
    ;;
    -d=*|--runtimedata=*)
    RUNTIME_DATA_INPUT="${i#*=}"
    shift
    ;;
    -b=*|--buildno=*)
    BUILD_NO="${i#*=}"
    shift
    ;;
   -h|--help)
    echo "Arguments: "
    echo " [-k | --apikey] = <TESTSIGMA_API_KEY>"
    echo " [-i | --testplanid] = <TESTSIGMA_TEST_PLAN_ID>"
    echo " [-t | --maxtimeinmins] = <MAX_WAIT_TIME_IN_MINS>"
    echo " [-r | --reportfilepath] = <JUNIT_REPORT_FILE_PATH>"
    echo " [-d | --runtimedata] = <OPTIONAL COMMA SEPARATED KEY VALUE PAIRS>"
    echo " [-b | --buildno] = <BUILD_NO_IF_ANY>"
 
    shift
    exit 1
    ;;
  esac
done
 
get_status(){
  # Old method
  # RUN_RESPONSE=$(curl -u $TESTSIGMA_USER_NAME:$TESTSIGMA_PASSWORD --silent --write-out "HTTPSTATUS:%{http_code}" -X GET $TESTSIGMA_TEST_PLAN_RUN_URL/$HTTP_BODY/status)
 
  RUN_RESPONSE=$(curl --insecure -H "Authorization:Bearer $TESTSIGMA_API_KEY"\
    --silent --write-out "HTTPSTATUS:%{http_code}" \
    -X GET $TESTSIGMA_TEST_PLAN_REST_URL/$RUN_ID)
  
  # extract the body
  RUN_BODY=$(echo $RUN_RESPONSE | sed -e 's/HTTPSTATUS\:.*//g')
  # extract the response status
  RUN_STATUS=$(echo $RUN_RESPONSE | tr -d '\n' | sed -e 's/.*HTTPSTATUS://')
  echo "Test Plan Result Response: $RUN_BODY"
  # extract exec status
  EXECUTION_STATUS=$(echo $RUN_BODY | getJsonValue status)

}
 
function checkTestPlanRunStatus(){
  IS_TEST_RUN_COMPLETED=0
  for ((i=0;i<=POLL_COUNT;i++))
  do
    get_status
    echo " Exceution Status:: $EXECUTION_STATUS "
    if [[ $EXECUTION_STATUS =~ "STATUS_IN_PROGRESS" ]]; then
      echo "Poll #$(($i+1)) - Test Execution in progress... Wait for $SLEEP_TIME seconds before next poll.."
      sleep $SLEEP_TIME
    elif [[ $EXECUTION_STATUS =~ "STATUS_CREATED" ]]; then
      echo "Poll #$(($i+1)) - Test Execution/Re-run Created... Wait for $SLEEP_TIME seconds before next poll.."
      sleep $SLEEP_TIME  
    elif [[ $EXECUTION_STATUS =~ "STATUS_COMPLETED" ]]; then
      IS_TEST_RUN_COMPLETED=1
      echo "Poll #$(($i+1)) - Tests Execution completed..."
      TOTALRUNSECONDS=$(($(($i+1))*$SLEEP_TIME))
      echo "Total script run time: $(convertsecs $TOTALRUNSECONDS)"
      break
    else
      echo "Unexpected Execution status. Please check run results for more details."
    fi
  done
}
 
function saveFinalResponseToJSONFile(){
  if [ $IS_TEST_RUN_COMPLETED -eq 0 ]
    then
      echo "$MAX_WAITTIME_EXCEEDED_ERRORMSG"
  fi
  
  echo "$RUN_BODY" >> $JSON_REPORT_FILE_PATH
  echo "Saved response to JSON Reports file - $JSON_REPORT_FILE_PATH"
}
 
function saveFinalResponseToJUnitFile(){
  if [ $IS_TEST_RUN_COMPLETED -eq 0 ]
    then
      echo "$MAX_WAITTIME_EXCEEDED_ERRORMSG"
      exit 1
  fi
 
  echo ""
  echo "Downloading the Junit report..."
 
  curl --insecure --progress-bar -H "Authorization:Bearer $TESTSIGMA_API_KEY" \
    -H "Accept: application/xml" \
    -H "content-type:application/json" \
    -X GET $TESTSIGMA_JUNIT_REPORT_URL/$RUN_ID --output $JUNIT_REPORT_FILE_PATH
 
  echo "JUNIT Reports file - $JUNIT_REPORT_FILE_PATH"
}
 
function getJsonValue() {
  json_key=$1
  awk -F"[,:}]" '{for(i=1;i<=NF;i++){if($i~/\042'$json_key'\042/){print $(i+1)}}}' | tr -d '"'
}
 
function populateRuntimeData() {
  IFS=',' read -r -a VARIABLES <<< "$RUNTIME_DATA_INPUT"
  RUN_TIME_DATA='"runtimeData":{'
  DATA_VALUES=
  for element in "${VARIABLES[@]}"
  do
    DATA_VALUES=$DATA_VALUES","
    IFS='=' read -r -a VARIABLE_VALUES <<< "$element"
    DATA_VALUES="$DATA_VALUES"'"'"${VARIABLE_VALUES[0]}"'":"'"${VARIABLE_VALUES[1]}"'"'
  done
  DATA_VALUES="${DATA_VALUES:1}"
  RUN_TIME_DATA=$RUN_TIME_DATA$DATA_VALUES"}"
}
 
function populateBuildNo(){
  if [ -z "$BUILD_NO" ]
    then
      echo ""
  else
    BUILD_DATA='"buildNo":'$BUILD_NO
  fi
}
 
function populateJsonPayload(){
  JSON_DATA='{"testPlanId":'$TESTSIGMA_TEST_PLAN_ID
  populateRuntimeData
  populateBuildNo
  if [ -z "$BUILD_DATA" ];then
      JSON_DATA=$JSON_DATA,$RUN_TIME_DATA"}"
  elif [ -z "$RUN_TIME_DATA" ];then
      JSON_DATA=$JSON_DATA,$BUILD_DATA"}"
  elif [ -z "$BUILD_DATA" ] && [ -z "$RUN_TIME_DATA" ];then
      JSON_DATA=$JSON_DATA"}"
  else
     JSON_DATA=$JSON_DATA,$RUN_TIME_DATA,$BUILD_DATA"}"
  fi
  echo "InputData="$JSON_DATA
}
 
function convertsecs(){
  ((h=${1}/3600))
  ((m=(${1}%3600)/60))
  ((s=${1}%60))
  printf "%02d hours %02d minutes %02d seconds" $h $m $s
}
 
function setExitCode(){
  RESULT=$(echo $RUN_BODY | getJsonValue result)
  APPURL=$(echo $RUN_BODY | getJsonValue result)
  echo $RESULT
  echo $([[ $RESULT =~ "SUCCESS" ]])
  if [[ $RESULT =~ "SUCCESS" ]];then
    EXITCODE=0
  else
    EXITCODE=1
  fi
  echo "exit Code:$EXITCODE"
}

function setTestSummary() {
    RESULT=$(echo $RUN_BODY | getJsonValue result)
    TOTAL_COUNT=$(echo $RUN_BODY | getJsonValue total_count)
    PASSED_COUNT=$(echo $RUN_BODY | getJsonValue passed_count)
    FAILED_COUNT=$(echo $RUN_BODY | getJsonValue failed_count)
    echo $RESULT $TOTAL_COUNT $PASSED_COUNT $FAILED_COUNT
    echo "### Testsigma summary" >> $GITHUB_STEP_SUMMARY
    echo "[Results]($TS_DASHBOARD_URL/ui/td/runs/$RUN_ID)" >> $GITHUB_STEP_SUMMARY
    echo "Passed: $PASSED_COUNT/$TOTAL_COUNT" >> $GITHUB_STEP_SUMMARY
    echo "Failed: $FAILED_COUNT/$TOTAL_COUNT" >> $GITHUB_STEP_SUMMARY
    echo "Status: $RESULT"  >> $GITHUB_STEP_SUMMARY
}
#******************************************************
 
echo "************ Testsigma: Start executing automated tests ************"
 
populateJsonPayload
 
# store the whole response with the status at the end
HTTP_RESPONSE=$(curl --insecure -H "Authorization:Bearer $TESTSIGMA_API_KEY" \
  -H "Accept: application/json" \
  -H "content-type:application/json" \
  --silent --write-out "HTTPSTATUS:%{http_code}" \
  -d $JSON_DATA -X POST $TESTSIGMA_TEST_PLAN_REST_URL )
 
# extract the body from response
HTTP_BODY=$(echo $HTTP_RESPONSE | sed -e 's/HTTPSTATUS\:.*//g')
 
# extract run id from response
RUN_ID=$(echo $HTTP_RESPONSE | getJsonValue id)
 
# extract the status code from response
HTTP_STATUS=$(echo $HTTP_RESPONSE | tr -d '\n' | sed -e 's/.*HTTPSTATUS://')
 
# print the run ID or the error message
NUMBERS_REGEX="^[0-9].*"
if [[ $RUN_ID =~ $NUMBERS_REGEX ]]; then
  echo "Run ID: $RUN_ID"
else
  echo "$RUN_ID"
fi
 
EXITCODE=0
# example using the status
if [ ! $HTTP_STATUS -eq 200  ]; then
  echo "Failed to start Test Plan execution!"
  echo "$HTTP_RESPONSE"
  EXITCODE=1
  #Exit with a failure.
else
  echo "Number of maximum polls to be done: $POLL_COUNT"
  checkTestPlanRunStatus
  saveFinalResponseToJUnitFile
  saveFinalResponseToJSONFile
  setTestSummary
  setExitCode
fi
 
echo "************************************************"
echo "Result JSON Response: $RUN_BODY"
echo "************ Testsigma: Completed executing automated tests ************"
exit $EXITCODE
