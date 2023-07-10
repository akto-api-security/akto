import func from "@/util/func";

const MAX_SEVERITY_THRESHOLD = 100000;

function getOrderPriority(state){
    switch(state._name){
        case "RUNNING": return 1;
        case "SCHEDULED": return 2;
        case "STOPPED": return 4;
        default: return 3;
    }
}

function getTestingRunType(testingRun, testingRunResultSummary){
    if(testingRunResultSummary.metadata!=null){
        return 'CI/CD';
    }
    if(testingRun.periodInSeconds==0){
        return 'One-time'
    }
    return 'Recurring';
}

function getTotalSeverity(countIssues){
    let ts = 0;
    if(countIssues==null){
        return 0;
    }
    ts = MAX_SEVERITY_THRESHOLD*(countIssues['High']*MAX_SEVERITY_THRESHOLD + countIssues['Medium']) + countIssues['Low']
    return ts;
}

function getTotalSeverityTestRunResult(severity){
  if(severity==null || severity.length==0){
      return 0;
  }
  let ts = MAX_SEVERITY_THRESHOLD*((severity[0].confidence=='High')*MAX_SEVERITY_THRESHOLD + (severity[0].confidence=='Medium')) + (severity[0].confidence=='Low')
  return ts;
}

function getRuntime(scheduleTimestamp ,endTimestamp, state){
    if(state._name=='RUNNING'){
        return "Currently running";
    }
    const currTime = Date.now();
    if(endTimestamp == -1){
      if(currTime > scheduleTimestamp){
        return "Was scheduled for " + func.prettifyEpoch(scheduleTimestamp)
    } else {
        return "Next run in " + func.prettifyEpoch(scheduleTimestamp)
    }
}
return 'Last run ' + func.prettifyEpoch(endTimestamp);
}

function getAlternateTestsInfo(state){
    switch(state._name){
        case "RUNNING": return "Tests are still running";
        case "SCHEDULED": return "Tests have been scheduled";
        case "STOPPED": return "Tests have been stopper";
        default: return "Information unavailable";
    }
}

function getTestsInfo(testResultsCount, state){
    return (testResultsCount == null) ? getAlternateTestsInfo(state) : testResultsCount + " tests"
}

const transform = {
    prepareTestRun : (data, testingRunResultSummary) => {
      let obj={};
      if(testingRunResultSummary==null){
        testingRunResultSummary = {};
      }
      if(testingRunResultSummary.countIssues!=null){
          testingRunResultSummary.countIssues['High'] = testingRunResultSummary.countIssues['HIGH']
          testingRunResultSummary.countIssues['Medium'] = testingRunResultSummary.countIssues['MEDIUM']
          testingRunResultSummary.countIssues['Low'] = testingRunResultSummary.countIssues['LOW']
          delete testingRunResultSummary.countIssues['HIGH']
          delete testingRunResultSummary.countIssues['MEDIUM']
          delete testingRunResultSummary.countIssues['LOW']
      }
      obj['hexId'] = data.hexId;
      obj['testingRunResultSummaryHexId'] = testingRunResultSummary.hexId;
      obj['orderPriority'] = getOrderPriority(data.state)
      obj['icon'] = func.getTestingRunIcon(data.state);
      obj['name'] = data.name || "Test"
      obj['number_of_tests_str'] = getTestsInfo(testingRunResultSummary.testResultsCount, data.state)
      obj['run_type'] = getTestingRunType(data, testingRunResultSummary);
      obj['run_time_epoch'] = data.endTimestamp == -1 ? data.scheduleTimestamp : data.endTimestamp
      obj['scheduleTimestamp'] = data.scheduleTimestamp
      obj['pickedUpTimestamp'] = data.pickedUpTimestamp
      obj['run_time'] = getRuntime(data.scheduleTimestamp ,data.endTimestamp, data.state)
      obj['severity'] = func.getSeverity(testingRunResultSummary.countIssues)
      obj['total_severity'] = getTotalSeverity(testingRunResultSummary.countIssues);
      obj['severityStatus'] = func.getSeverityStatus(testingRunResultSummary.countIssues)
      obj['runTypeStatus'] = [obj['run_type']]
      return obj;
    },
    prepareTestRuns : (testingRuns, latestTestingRunResultSummaries) => {
      let testRuns = []
      testingRuns.forEach((data)=>{
        let obj={};
        let testingRunResultSummary = latestTestingRunResultSummaries[data['hexId']];
        obj = transform.prepareTestRun(data, testingRunResultSummary)
        testRuns.push(obj);
    })
    return testRuns;
    },
    prepareTestRunResult : (data, subCategoryMap, subCategoryFromSourceConfigMap) => {
      let obj = {};
      obj['hexId'] = data.hexId;
      obj['name'] = func.getRunResultSubCategory(data, subCategoryFromSourceConfigMap, subCategoryMap, "testName")
      obj['detected_time'] = "Detected " + func.prettifyEpoch(data.endTimestamp)
      obj["endTimestamp"] = data.endTimestamp
      obj['testCategory'] = func.getRunResultCategory(data, subCategoryMap, subCategoryFromSourceConfigMap, "shortName")
      obj['url'] = "Detected in " + (data.apiInfoKey.method._name || data.apiInfoKey.method) + " " + data.apiInfoKey.url 
      obj['severity'] = data.vulnerable ? [{confidence : func.toSentenceCase(func.getRunResultSeverity(data, subCategoryMap))}] : []
      obj['total_severity'] = getTotalSeverityTestRunResult(obj['severity'])
      obj['severityStatus'] = obj["severity"].length > 0 ? [obj["severity"][0].confidence] : []
      obj['apiFilter'] = [(data.apiInfoKey.method._name || data.apiInfoKey.method) + " " + data.apiInfoKey.url]
      obj['categoryFilter'] = [obj['testCategory']]
      obj['testFilter'] = [obj['name']]
      obj['testResults'] = data['testResults'] || []
      obj['singleTypeInfos'] = data['singleTypeInfos'] || []
      obj['vulnerable'] = data['vulnerable'] || false
      return obj;
    },
    prepareTestRunResults : (testingRunResults, subCategoryMap, subCategoryFromSourceConfigMap) => {
      let testRunResults = []
      testingRunResults.forEach((data) => {
        let obj = transform.prepareTestRunResult(data, subCategoryMap, subCategoryFromSourceConfigMap);
        if(obj['name'] && obj['testCategory']){
          testRunResults.push(obj);
        }
      })
      return testRunResults;
    },
    prepareFilters: (data, filters) => {
        let localFilters = filters;
        localFilters.forEach((filter, index) => {
          localFilters[index].availableChoices = new Set()
          localFilters[index].choices = []
        })
        data.forEach((obj) => {
        localFilters.forEach((filter, index) => {
          let key = filter["key"]
          obj[key].map((item) => filter.availableChoices.add(item));
          localFilters[index] = filter
          })
        })
        localFilters.forEach((filter, index) => {
          let choiceList = []
          filter.availableChoices.forEach((choice) => {
            choiceList.push({label:choice, value:choice})
          })
          localFilters[index].choices = choiceList
        })
        return localFilters
    },
    issueSummaryTable(issuesDetails, subCategoryMap) {
      if (issuesDetails) {
          return [
              {
                  title: 'Issue category',
                  description: subCategoryMap[issuesDetails.id.testSubCategory].superCategory.displayName
              },
              {
                  title: 'Test run',
                  description: subCategoryMap[issuesDetails.id.testSubCategory].testName
              },
              {
                  title: 'Severity',
                  description: subCategoryMap[issuesDetails.id.testSubCategory].superCategory.severity._name
              },
              {
                  title: 'Endpoint',
                  description: {
                      method: issuesDetails.id.apiInfoKey.method,
                      url: issuesDetails.id.apiInfoKey.url
                  }
              },
              // {
              //     title: 'Collection',
              //     description: this.mapCollectionIdToName[issuesDetails.id.apiInfoKey.apiCollectionId]
              // }
          ]
      }
      return []
  },
}

export default transform