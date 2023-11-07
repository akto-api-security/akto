import func from "@/util/func";
import api from "./api";
import React, {  } from 'react'
import {
  Text,
  HorizontalStack, Badge, Link, List
  } from '@shopify/polaris';
  import PersistStore from "../../../main/PersistStore";

const MAX_SEVERITY_THRESHOLD = 100000;

function getStatus(state){
  return state._name ? state._name : ( state.name ? state.name : state )
}

function getOrderPriority(state){
  let status = getStatus(state);
    switch(status){
        case "RUNNING": return 1;
        case "SCHEDULED": return 2;
        case "STOPPED": return 4;
        case "FAIL": return 5;
        default: return 3;
    }
}

function getTestingRunType(testingRun, testingRunResultSummary, cicd){
    if(testingRunResultSummary.metadata!=null || cicd){
        return 'CI/CD';
    }
    if(testingRun.scheduleTimestamp >= func.timeNow() && testingRun.scheduleTimestamp < func.timeNow() + 86400){
      return 'Recurring';
    }
    return 'One-time'
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
  let status = getStatus(state);
    if(status==='RUNNING'){
        return "Currently running";
    }
    const currTime = Date.now();
    if(endTimestamp <= 0 ){
     if(currTime > scheduleTimestamp){
        return "Was scheduled for " + func.prettifyEpoch(scheduleTimestamp)
    } else {
        return "Next run in " + func.prettifyEpoch(scheduleTimestamp)
    }
}
return 'Last run ' + func.prettifyEpoch(endTimestamp);
}

function getAlternateTestsInfo(state){
  let status = getStatus(state);
    switch(status){
        case "RUNNING": return "Tests are still running";
        case "SCHEDULED": return "Tests have been scheduled";
        case "STOPPED": return "Tests have been stopped";
        case "FAIL": return "Test execution has failed during run";
        default: return "Information unavailable";
    }
}

function getTestsInfo(testResultsCount, state){
    return (testResultsCount == null) ? getAlternateTestsInfo(state) : testResultsCount + " tests"
}

function minimizeTagList(items){
  if(items.length>2){

    let ret = items.slice(0,2)
    ret.push(`+${items.length-2} more`)
    return ret;
  }
  return items;
}

function checkTestFailure(summaryState, testRunState){
  if(testRunState=='COMPLETED' && summaryState!='COMPLETED'){
    return true;
  }
  return false;
}

function getCweLink(item){
  let linkUrl = ""
  let cwe = item.split("-")
  if(cwe[1]){
      linkUrl = `https://cwe.mitre.org/data/definitions/${cwe[1]}.html`
  }
  return linkUrl;
}

function getCveLink(item){
  return `https://nvd.nist.gov/vuln/detail/${item}`
}

const transform = {
    tagList : (list, linkType) => {

      let ret = list?.map((tag, index) => {

        let linkUrl = ""
        switch(linkType){
          case "CWE":
            linkUrl = getCweLink(tag)
            break;
          case "CVE":
            linkUrl = getCveLink(tag)
            break;
        }

        return (
          <Link key={index} url={linkUrl} target="_blank">
            <Badge progress="complete" key={index}>{tag}</Badge>
          </Link>
        )
      })
      return ret;
    },
    prepareDataFromSummary : (data, testRunState) => {
      let obj={};
      obj['testingRunResultSummaryHexId'] = data?.hexId;
      let state = data?.state;
      if(checkTestFailure(state, testRunState)){
        state = 'FAIL'
      }
      obj['orderPriority'] = getOrderPriority(state)
      obj['icon'] = func.getTestingRunIcon(state);
      obj['iconColor'] = func.getTestingRunIconColor(state)
      obj['summaryState'] = getStatus(state)
      obj['startTimestamp'] = data?.startTimestamp
      obj['endTimestamp'] = data?.endTimestamp
      obj['severity'] = func.getSeverity(data?.countIssues)
      obj['severityStatus'] = func.getSeverityStatus(data?.countIssues)
      obj['metadata'] = func.flattenObject(data?.metadata)
      return obj;
    },
    prepareCountIssues : (data) => {
      let obj={
        'High': data['HIGH'] || 0,
        'Medium': data['MEDIUM'] || 0,
        'Low': data['LOW'] || 0
      };
      return obj;
    },
    prepareTestRun : (data, testingRunResultSummary, cicd) => {
      let obj={};
      if(testingRunResultSummary==null){
        testingRunResultSummary = {};
      }
      if(testingRunResultSummary.countIssues!=null){
          testingRunResultSummary.countIssues = transform.prepareCountIssues(testingRunResultSummary.countIssues);
      }

      let state = data.state;
      if(checkTestFailure(testingRunResultSummary.state, state)){
        state = 'FAIL'
      }

      obj['id'] = data.hexId;
      obj['testingRunResultSummaryHexId'] = testingRunResultSummary?.hexId;
      obj['orderPriority'] = getOrderPriority(state)
      obj['icon'] = func.getTestingRunIcon(state);
      obj['iconColor'] = func.getTestingRunIconColor(state)
      obj['name'] = data.name || "Test"
      obj['number_of_tests_str'] = getTestsInfo(testingRunResultSummary?.testResultsCount, state)
      obj['run_type'] = getTestingRunType(data, testingRunResultSummary, cicd);
      obj['run_time_epoch'] = Math.max(data.scheduleTimestamp,data.endTimestamp)
      obj['scheduleTimestamp'] = data.scheduleTimestamp
      obj['pickedUpTimestamp'] = data.pickedUpTimestamp
      obj['run_time'] = getRuntime(data.scheduleTimestamp ,data.endTimestamp, state)
      obj['severity'] = func.getSeverity(testingRunResultSummary.countIssues)
      obj['total_severity'] = getTotalSeverity(testingRunResultSummary.countIssues);
      obj['severityStatus'] = func.getSeverityStatus(testingRunResultSummary.countIssues)
      obj['runTypeStatus'] = [obj['run_type']]
      obj['nextUrl'] = "/dashboard/testing/"+data.hexId
      obj['testRunState'] = data.state
      obj['summaryState'] = state
      obj['startTimestamp'] = testingRunResultSummary?.startTimestamp
      obj['endTimestamp'] = testingRunResultSummary?.endTimestamp
      obj['metadata'] = func.flattenObject(testingRunResultSummary?.metadata)
      return obj;
    },
    prepareTestRuns : (testingRuns, latestTestingRunResultSummaries, cicd) => {
      let testRuns = []
      testingRuns.forEach((data)=>{
        let obj={};
        let testingRunResultSummary = latestTestingRunResultSummaries[data['hexId']] || {};
        obj = transform.prepareTestRun(data, testingRunResultSummary, cicd)
        testRuns.push(obj);
    })
    return testRuns;
    },
    prepareTestRunResult : (hexId, data, subCategoryMap, subCategoryFromSourceConfigMap) => {
      let obj = {};
      obj['id'] = data.hexId;
      obj['name'] = func.getRunResultSubCategory(data, subCategoryFromSourceConfigMap, subCategoryMap, "testName")
      obj['detected_time'] = (data['vulnerable'] ? "Detected " : "Tried ") + func.prettifyEpoch(data.endTimestamp)
      obj["endTimestamp"] = data.endTimestamp
      obj['testCategory'] = func.getRunResultCategory(data, subCategoryMap, subCategoryFromSourceConfigMap, "shortName")
      obj['url'] = (data['vulnerable'] ? "Detected in " : "Tried in ") + (data.apiInfoKey.method._name || data.apiInfoKey.method) + " " + data.apiInfoKey.url 
      obj['severity'] = data.vulnerable ? [func.toSentenceCase(func.getRunResultSeverity(data, subCategoryMap))] : []
      obj['total_severity'] = getTotalSeverityTestRunResult(obj['severity'])
      obj['severityStatus'] = obj["severity"].length > 0 ? [obj["severity"][0]] : []
      obj['apiFilter'] = [(data.apiInfoKey.method._name || data.apiInfoKey.method) + " " + data.apiInfoKey.url]
      obj['categoryFilter'] = [obj['testCategory']]
      obj['testFilter'] = [obj['name']]
      obj['testResults'] = data['testResults'] || []
      obj['errors'] = obj['testResults'].filter((res) => (res.errors && res.errors.length > 0)).map((res) => res.errors.join(", "))
      obj['singleTypeInfos'] = data['singleTypeInfos'] || []
      obj['vulnerable'] = data['vulnerable'] || false
      obj['nextUrl'] = "/dashboard/testing/"+ hexId + "/result/" + data.hexId;
      obj['cwe'] = subCategoryMap[data.testSubType]?.cwe ? subCategoryMap[data.testSubType]?.cwe : []
      obj['cweDisplay'] = minimizeTagList(obj['cwe'])
      obj['cve'] = subCategoryMap[data.testSubType]?.cve ? subCategoryMap[data.testSubType]?.cve : []
      obj['cveDisplay'] = minimizeTagList(obj['cve'])
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

  replaceTags(details, vulnerableRequests) {
    let percentageMatch = 0;
    vulnerableRequests?.forEach((request) => {
      let testRun = request['testResults']
      testRun?.forEach((runResult) => {
        if (percentageMatch < runResult.percentageMatch) {
          percentageMatch = runResult.percentageMatch
        }
      })
    })
    return details.replace(/{{percentageMatch}}/g, func.prettifyShort(percentageMatch))
  },

  fillMoreInformation(category, moreInfoSections, affectedEndpoints) {

    let filledSection = []
    moreInfoSections.forEach((section) => {
      let sectionLocal = {}
      sectionLocal.icon = section.icon
      sectionLocal.title = section.title
      switch (section.title) {
        case "Description":

        if(category?.issueDetails == null || category?.issueDetails == undefined){
          return;
        }

          sectionLocal.content = (
            <Text color='subdued'>
              {transform.replaceTags(category?.issueDetails, category?.vulnerableTestingRunResults) || "No impact found"}
            </Text>
          )
          break;
        case "Impact":
          
          if(category?.issueImpact == null || category?.issueImpact == undefined){
            return;
          }

          sectionLocal.content = (
            <Text color='subdued'>
              {category?.issueImpact || "No impact found"}
            </Text>
          )
          break;
        case "Tags":
          if (category?.issueTags == null || category?.issueTags == undefined || category?.issueTags.length == 0) {
            return;
          }

          sectionLocal.content = (
            <HorizontalStack gap="2">
              {
                transform.tagList(category?.issueTags)
              }
            </HorizontalStack>
          )

          break;
        case "CWE":
          if (category?.cwe == null || category?.cwe == undefined || category?.cwe.length == 0) {
            return;
          }
          sectionLocal.content = (
            <HorizontalStack gap="2">
              {
                transform.tagList(category?.cwe, "CWE")
              }
            </HorizontalStack>
          )
          break;
        case "CVE":
          if (category?.cve == null || category?.cve == undefined || category?.cve.length == 0) {
            return;
          }
          sectionLocal.content = (
            <HorizontalStack gap="2">
              {
                transform.tagList(category?.cve, "CVE")
              }
            </HorizontalStack>
          )
          break;
        case "References":

          if (category?.references == null || category?.references == undefined || category?.references.length == 0) {
            return;
          }

          sectionLocal.content = (
            <List type='bullet' spacing="extraTight">
              {
                category?.references?.map((reference) => {
                  return (
                    <List.Item key={reference}>
                      <Link key={reference} url={reference} monochrome removeUnderline target="_blank">
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
          break;
        case "API endpoints affected":

          if (affectedEndpoints == null || affectedEndpoints == undefined || affectedEndpoints.length == 0) {
            return;
          }

          sectionLocal.content = (
            <List type='bullet'>
              {
                affectedEndpoints?.map((item, index) => {
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
          break;
      }
      filledSection.push(sectionLocal)
    })

    return filledSection;
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
},
setTestMetadata () {
  api.fetchAllSubCategories().then((resp) => {
    let subCategoryMap = {}
    resp.subCategories.forEach((x) => {
      subCategoryMap[x.name] = x
    })
    let subCategoryFromSourceConfigMap = {}
    resp.testSourceConfigs.forEach((x) => {
      subCategoryFromSourceConfigMap[x.id] = x
    })
    PersistStore.getState().setSubCategoryMap(subCategoryMap)
    PersistStore.getState().setSubCategoryFromSourceConfigMap(subCategoryFromSourceConfigMap)
})
}

}

export default transform