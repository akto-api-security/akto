import func from "@/util/func";
import api from "./api";
import React, {  } from 'react'
import {
  Text,
  HorizontalStack, Badge, Link, List
  } from '@shopify/polaris';

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
  let ts = MAX_SEVERITY_THRESHOLD*((severity[0].includes("High"))*MAX_SEVERITY_THRESHOLD + (severity[0].includes('Medium'))) + (severity[0].includes('Low'))
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
      obj['id'] = data.hexId;
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
      obj['nextUrl'] = "/dashboard/testing/"+data.hexId
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
    prepareTestRunResult : (hexId, data, subCategoryMap, subCategoryFromSourceConfigMap) => {
      let obj = {};
      obj['id'] = data.hexId;
      obj['name'] = func.getRunResultSubCategory(data, subCategoryFromSourceConfigMap, subCategoryMap, "testName")
      obj['detected_time'] = "Detected " + func.prettifyEpoch(data.endTimestamp)
      obj["endTimestamp"] = data.endTimestamp
      obj['testCategory'] = func.getRunResultCategory(data, subCategoryMap, subCategoryFromSourceConfigMap, "shortName")
      obj['url'] = "Detected in " + (data.apiInfoKey.method._name || data.apiInfoKey.method) + " " + data.apiInfoKey.url 
      obj['severity'] = data.vulnerable ? [func.toSentenceCase(func.getRunResultSeverity(data, subCategoryMap))] : []
      obj['total_severity'] = getTotalSeverityTestRunResult(obj['severity'])
      obj['severityStatus'] = obj["severity"].length > 0 ? [obj["severity"][0]] : []
      obj['apiFilter'] = [(data.apiInfoKey.method._name || data.apiInfoKey.method) + " " + data.apiInfoKey.url]
      obj['categoryFilter'] = [obj['testCategory']]
      obj['testFilter'] = [obj['name']]
      obj['testResults'] = data['testResults'] || []
      obj['singleTypeInfos'] = data['singleTypeInfos'] || []
      obj['vulnerable'] = data['vulnerable'] || false
      obj['nextUrl'] = "/dashboard/testing/"+ hexId + "/result/" + data.hexId;
      return obj;
    },
    prepareTestRunResults : (hexId, testingRunResults, subCategoryMap, subCategoryFromSourceConfigMap) => {
      let testRunResults = []
      testingRunResults.forEach((data) => {
        let obj = transform.prepareTestRunResult(hexId, data, subCategoryMap, subCategoryFromSourceConfigMap);
        if(obj['name'] && obj['testCategory']){
          testRunResults.push(obj);
        }
      })
      return testRunResults;
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
  async fillMoreInformation(runIssues, subCategoryMap, moreInfoSections){
    moreInfoSections[0].content = (
        <Text color='subdued'>
          {subCategoryMap[runIssues.id?.testSubCategory]?.issueImpact || "No impact found"}
        </Text>
      )
      moreInfoSections[1].content = (
        <HorizontalStack gap="2">
          {
            subCategoryMap[runIssues.id.testSubCategory]?.issueTags.map((tag, index) => {
              return (
                <Badge progress="complete" key={index}>{tag}</Badge>
              )
            })
          }
        </HorizontalStack>
      )
      moreInfoSections[3].content = (
        <List type='bullet' spacing="extraTight">
          {
            subCategoryMap[runIssues.id?.testSubCategory]?.references.map((reference) => {
              return (
                <List.Item key={reference}>
                  <Link key={reference} url={reference} monochrome removeUnderline>
                    <Text color='subdued'>
                      {reference}
                    </Text>
                  </Link>
                </List.Item>
              )
            })
          }
        </List>
      )
      await api.fetchAffectedEndpoints(runIssues.id).then((resp1) => {
        let similarlyAffectedIssues = resp1['similarlyAffectedIssues'];
        moreInfoSections[2].content = (
          <List type='bullet'>
            {
              similarlyAffectedIssues.map((item, index) => {
                return (
                  <List.Item key={index}>
                    <Text color='subdued'>
                      {item.id.apiInfoKey.method} {item.id.apiInfoKey.url}
                    </Text>
                  </List.Item>)
              })
            }
          </List>
        )
    })
    return moreInfoSections;
  },

  filterContainsConditions(conditions, operator) { //operator is string as 'OR' or 'AND'
    let filteredCondition = {}
    let found = false
    filteredCondition['operator'] = operator
    filteredCondition['predicates'] = []
    conditions.forEach(element => {
        if (element.value && element.operator === operator) {
            if (element.type === 'CONTAINS') {
                filteredCondition['predicates'].push({ type: element.type, value: element.value })
                found = true
            } else if (element.type === 'BELONGS_TO' || element.type === 'NOT_BELONGS_TO') {
                let collectionMap = element.value
                let collectionId = Object.keys(collectionMap)[0]

                if (collectionMap[collectionId]) {
                    let apiKeyInfoList = []
                    collectionMap[collectionId].forEach(apiKeyInfo => {
                        apiKeyInfoList.push({ 'url': apiKeyInfo['url'], 'method': apiKeyInfo['method'], 'apiCollectionId': Number(collectionId) })
                        found = true
                    })
                    if (apiKeyInfoList.length > 0) {
                        filteredCondition['predicates'].push({ type: element.type, value: apiKeyInfoList })
                    }
                }
            }
        }
    });
    if (found) {
        return filteredCondition;
    }
},

fillConditions(conditions, predicates, operator) {
    predicates.forEach(async (e, i) => {
        let valueFromPredicate = e.value
        if (Array.isArray(valueFromPredicate) && valueFromPredicate.length > 0) {
            let valueForCondition = {}
            let collectionId = valueFromPredicate[0]['apiCollectionId']
            let apiInfoKeyList = []
            for (var index = 0; index < valueFromPredicate.length; index++) {
                let apiEndpoint = {
                    method: valueFromPredicate[index]['method'],
                    url: valueFromPredicate[index]['url']
                }
                apiInfoKeyList.push({
                        method: apiEndpoint.method,
                        url: apiEndpoint.url
                })
            }
            valueForCondition[collectionId] = apiInfoKeyList
            conditions.push({ operator: operator, type: e.type, value: valueForCondition })
        } else {
            conditions.push({ operator: operator, type: e.type, value: valueFromPredicate })
        }
    })
},

createConditions(data){
    let testingEndpoint = data
        let conditions = []
        if (testingEndpoint?.andConditions) {
            transform.fillConditions(conditions, testingEndpoint.andConditions.predicates, 'AND')
        }
        if (testingEndpoint?.orConditions) {
            transform.fillConditions(conditions, testingEndpoint.orConditions.predicates, 'OR')
        }
        return conditions;
}

}

export default transform