import func from "@/util/func";
import api from "./api";
import {ResourcesMajor,
  CollectionsMajor,
  CreditCardSecureMajor,
  MarketingMajor,
  FraudProtectMajor, RiskMajor,
  CircleCancelMajor,
  CalendarMinor,
  ReplayMinor,
  PlayMinor,
  LockMajor
} from '@shopify/polaris-icons';
import React from 'react'
import { Text,HorizontalStack, Badge, Link, List, Box, Icon, Avatar, Tag, Tooltip} from '@shopify/polaris';
import { history } from "@/util/history";
import PersistStore from "../../../main/PersistStore";
import observeFunc from "../observe/transform";
import TooltipText from "../../components/shared/TooltipText";
import TestingStore from "./testingStore";
import IssuesCheckbox from "../issues/IssuesPage/IssuesCheckbox";
import { CellType } from "@/apps/dashboard/components/tables/rows/GithubRow";
import LocalStore from "../../../main/LocalStorageStore";
import GetPrettifyEndpoint from "@/apps/dashboard/pages/observe/GetPrettifyEndpoint";
import JiraTicketDisplay from "../../components/shared/JiraTicketDisplay";
import { getMethod } from "../observe/GetPrettifyEndpoint";
import { getDashboardCategory, mapLabel } from "../../../main/labelHelper";

let headers = [
    {
      title: '',
      type: CellType.COLLAPSIBLE
    },
    {
      value: "nameComp",
      title: 'Issue name',
      tooltipContent: 'Name of the test as in our test editor'
    },
    {
      title: 'Severity',
      value: 'severityComp',
      sortActive: true
    },
    {
      value: 'testCategory',
      title: 'Category',
      type: CellType.TEXT,
      tooltipContent: 'Name of the subcategory of the test'
    },
    {
      title: 'CWE tags',
      value: 'cweDisplayComp',
      tooltipContent: "CWE tags associated with the test from akto's test library"
    },
    {
      title: 'Number of ' + mapLabel('URLs', getDashboardCategory()),
      value: 'totalUrls',
      type: CellType.TEXT
    },
    {
      value: "scanned_time_comp",
      title: 'Scanned',
      sortActive: true
    },
]

const MAX_SEVERITY_THRESHOLD = 100000;

function getStatus(state) {
  if (state)
    return state._name ? state._name : (state.name ? state.name : state)
  return "UNKNOWN"
}

function getOrderPriority(state) {
  let status = getStatus(state);
  switch (status) {
    case "RUNNING": return 1;
    case "SCHEDULED": return 2;
    case "STOPPED": return 4;
    case "FAILED":
    case "FAIL": return 5;
    default: return 3;
  }
}

function getTestingRunType(testingRun, testingRunResultSummary, cicd) {
  if (testingRunResultSummary.metadata != null || cicd) {
    return 'CI/CD';
  }
  if (testingRun.periodInSeconds > 0) {
    return 'Recurring';
  }
  return 'One-time'
}

function getTotalSeverity(countIssues) {
  let ts = 0;
  if (countIssues == null) {
    return 0;
  }
  ts = MAX_SEVERITY_THRESHOLD * (countIssues['High'] * MAX_SEVERITY_THRESHOLD + countIssues['Medium']) + countIssues['Low']
  return ts;
}

function getTotalSeverityTestRunResult(severity) {
  if (severity == null || severity.length == 0) {
    return 0;
  }
  let ts = MAX_SEVERITY_THRESHOLD * ((severity[0].includes("High")) * MAX_SEVERITY_THRESHOLD + (severity[0].includes('Medium'))) + (severity[0].includes('Low'))
  return ts;
}

function getRuntime(scheduleTimestamp, endTimestamp, state) {
  let status = getStatus(state);
  if (status === 'RUNNING') {
    return <div data-testid="test_run_status">Currently running</div>;
  }
  if (status === 'SCHEDULED') {
    return <div data-testid="test_run_status">Scheduled for {func.prettifyFutureEpoch(scheduleTimestamp, true)}</div>;
  }
  if (endTimestamp <= 0) {
    return <div data-testid="test_run_status">Last run {func.prettifyEpoch(scheduleTimestamp)}</div>;
  }
  return <div data-testid="test_run_status">Last run {func.prettifyEpoch(endTimestamp)}</div>;
}

function getAlternateTestsInfo(state) {
  let status = getStatus(state);
  switch (status) {
    case "RUNNING": return "Tests are still running";
    case "SCHEDULED": return "Tests have been scheduled";
    case "STOPPED": return "Tests have been stopped";
    case "FAILED":
    case "FAIL": return "Test execution has failed during run";
    default: return "Information unavailable";
  }
}

function getTestsInfo(testResultsCount, state){
    return (testResultsCount == null) ? getAlternateTestsInfo(state) : testResultsCount
}

function minimizeTagList(items){
  if(items.length>1){

    let ret = items.slice(0,1)
    ret.push(`+${items.length-1} more`)
    return ret;
  }
  return items;
}

function checkTestFailure(summaryState, testRunState) {
  if (testRunState === 'COMPLETED' && summaryState !== 'COMPLETED' && summaryState !== "STOPPED") {
    return true;
  }
  return false;
}

function getCweLink(item) {
  let linkUrl = ""
  let cwe = item.split("-")
  if (cwe[1]) {
    linkUrl = `https://cwe.mitre.org/data/definitions/${cwe[1]}.html`
  }
  return linkUrl;
}

function getCveLink(item) {
  return `https://nvd.nist.gov/vuln/detail/${item}`
}

function getScanFrequency(periodInSeconds) {
  if (periodInSeconds === -1) {
    return "Continuous"
  }
  else if (periodInSeconds === 0) {
    return "Once"
  } else if (periodInSeconds <= 86400) {
    return "Daily"
  } else if (periodInSeconds <= 604800) {
    return "Weekly"
  } else if (periodInSeconds <= 2678400) {
    return "Monthly"
  } else {
    return "-"
  }
}

const transform = {

  tagList: (list, linkType) => {

    let ret = list?.map((tag, index) => {

        let linkUrl = ""
        switch(linkType){
          case "CWE":
            linkUrl = getCweLink(tag)
            break;
          case "CVE":
            linkUrl = getCveLink(tag)
            break;
            default:
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
      const iconObj = func.getTestingRunIconObj(state)
      obj['orderPriority'] = getOrderPriority(state)
      obj['icon'] = iconObj.icon;
      obj['iconColor'] = iconObj?.iconColor || ''
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
        'Critical': (data && data['CRITICAL']) ? data['CRITICAL'] : 0,
        'High': (data && data['HIGH']) ? data['HIGH'] : 0,
        'Medium':(data && data['MEDIUM']) ? data['MEDIUM'] : 0,
        'Low': (data && data['LOW']) ? data['LOW'] : 0
      };
      return obj;
    },
    prettifyTestName: (testName, icon, iconColor, iconToolTipContent)=>{
      return(
        <HorizontalStack wrap={false} gap={4}>
          <Tooltip content={iconToolTipContent} hoverDelay={"300"} dismissOnMouseOut>
            <Box><Icon source={icon} color={iconColor}/></Box>
          </Tooltip>
          <Box maxWidth="350px">
            <TooltipText text={testName} tooltip={testName} textProps={{fontWeight: 'medium'}} />
          </Box>
        </HorizontalStack>
      )
    },
    filterObjectByValueGreaterThanZero: (obj)=> {
      const result = {};
    
      for (const key in obj) {
        if (obj.hasOwnProperty(key) && obj[key] > 0) {
          result[key] = obj[key];
        }
      }
    
      return result;
    },
  
  prepareTestRun: (data, testingRunResultSummary, cicd,prettified) => {
    let obj = {};
    if (testingRunResultSummary == null) {
      testingRunResultSummary = {};
    }

    let state = cicd ? testingRunResultSummary.state : data.state ;
    if (cicd !== true && checkTestFailure(testingRunResultSummary.state, state)) {
      state = 'FAIL'
    }

    let apiCollectionId = -1
    if(Object.keys(data).length > 0){
      if(data?.testingEndpoints?.apiCollectionId !== undefined){
        apiCollectionId = data?.testingEndpoints?.apiCollectionId
      }else if(data?.testingEndpoints?.workflowTest?.apiCollectionId !== undefined){
        apiCollectionId = data?.testingEndpoints?.workflowTest?.apiCollectionId 
      }else{
        apiCollectionId = data?.testingEndpoints?.apisList[0]?.apiCollectionId
      }
    }
    const iconObj = func.getTestingRunIconObj(state)

      obj['id'] = data.hexId;
      obj['testingRunResultSummaryHexId'] = testingRunResultSummary?.hexId;
      obj['orderPriority'] = getOrderPriority(state)
      obj['icon'] = iconObj.icon;
      obj['iconColor'] = iconObj.color
      obj['name'] = data.name || "Test"
      obj['number_of_tests'] = data.testIdConfig == 1 ? "-" : getTestsInfo(testingRunResultSummary?.testResultsCount, state)
      obj['run_type'] = getTestingRunType(data, testingRunResultSummary, cicd);
      obj['run_time_epoch'] = Math.max(data.scheduleTimestamp, (cicd ? testingRunResultSummary.endTimestamp : data.endTimestamp))
      obj['scheduleTimestamp'] = data.scheduleTimestamp
      obj['pickedUpTimestamp'] = data.pickedUpTimestamp
      obj['run_time'] = getRuntime(data.scheduleTimestamp , (cicd ? testingRunResultSummary.endTimestamp : getStatus(state) === "SCHEDULED" ? data.scheduledTimestamp:  data.endTimestamp), state)
      obj['severity'] = func.getSeverity(testingRunResultSummary.countIssues)
      obj['total_severity'] = getTotalSeverity(testingRunResultSummary.countIssues);
      obj['severityStatus'] = func.getSeverityStatus(testingRunResultSummary.countIssues)
      obj['runTypeStatus'] = [obj['run_type']]
      obj['nextUrl'] = "/dashboard/testing/"+data.hexId
      obj['testRunState'] = state
      obj['summaryState'] = testingRunResultSummary.state
      obj['startTimestamp'] = testingRunResultSummary?.startTimestamp
      obj['endTimestamp'] = testingRunResultSummary?.endTimestamp
      obj['metadata'] = func.flattenObject(testingRunResultSummary?.metadata)
      obj['apiCollectionId'] = apiCollectionId
      obj['userEmail'] = data.userEmail
      obj['scan_frequency'] = getScanFrequency(data.periodInSeconds)
      obj['total_apis'] = testingRunResultSummary.totalApis
      obj['miniTestingServiceName'] = data?.miniTestingServiceName
      if(prettified){
        
        const prettifiedTest={
          ...obj,
          testName: transform.prettifyTestName(data.name || "Test", iconObj.icon,iconObj.color, iconObj.tooltipContent),
          severity: observeFunc.getIssuesList(transform.filterObjectByValueGreaterThanZero(testingRunResultSummary.countIssues || {"CRITICAL": 0, "HIGH" : 0, "MEDIUM": 0, "LOW": 0}))
        }
        return prettifiedTest
      }else{
        return obj
      }
    },
    processData: (testingRuns, latestTestingRunResultSummaries, cicd) => {
      let testRuns = transform.prepareTestRuns(testingRuns, latestTestingRunResultSummaries, cicd, true);
      return testRuns;
    },
    prepareTestRuns : (testingRuns, latestTestingRunResultSummaries, cicd, prettified) => {
      let testRuns = []
      testingRuns.forEach((data)=>{
        let obj={};
        let testingRunResultSummary = latestTestingRunResultSummaries[data['hexId']] || {};
        obj = transform.prepareTestRun(data, testingRunResultSummary, cicd, prettified)
        testRuns.push(obj);
    })
    return testRuns;
    },
    prepareTestRunResult : (hexId, data, subCategoryMap, subCategoryFromSourceConfigMap, issuesDescriptionMap, jiraIssuesMapForResults) => {
      let obj = {};
      obj['id'] = data.hexId;
      obj['name'] = func.getRunResultSubCategory(data, subCategoryFromSourceConfigMap, subCategoryMap, "testName")
      obj['detected_time'] = (data['vulnerable'] ? "Detected " : "Tried ") + func.prettifyEpoch(data.endTimestamp)
      obj["endTimestamp"] = data.endTimestamp
      obj['testCategory'] = func.getRunResultCategory(data, subCategoryMap, subCategoryFromSourceConfigMap, "shortName")
      obj['url'] = (data.apiInfoKey.method._name || data.apiInfoKey.method) + " " + data.apiInfoKey.url 
      obj['severity'] = data.vulnerable ? [func.toSentenceCase(func.getRunResultSeverity(data, subCategoryMap))] : []
      obj['total_severity'] = getTotalSeverityTestRunResult(obj['severity'])
      obj['severityStatus'] = obj["severity"].length > 0 ? [obj["severity"][0]] : []
      obj['categoryFilter'] = [obj['testCategory']]
      obj['testFilter'] = [obj['name']]
      obj['testResults'] = data['testResults'] || []
      obj['errors'] = obj['testResults'].filter((res) => (res.errors && res.errors.length > 0)).map((res) => res.errors.join(", "))
      obj['singleTypeInfos'] = data['singleTypeInfos'] || []
      obj['vulnerable'] = data['vulnerable'] || false
      obj['nextUrl'] = "/dashboard/testing/"+ hexId + "?result=" + data.hexId;
      obj['cwe'] = subCategoryMap[data.testSubType]?.cwe ? subCategoryMap[data.testSubType]?.cwe : []
      obj['cweDisplay'] = minimizeTagList(obj['cwe'])
      obj['cve'] = subCategoryMap[data.testSubType]?.cve ? subCategoryMap[data.testSubType]?.cve : []
      obj['cveDisplay'] = minimizeTagList(obj['cve'])
      obj['errorsList'] = data.errorsList || []
      obj['testCategoryId'] = data.testSubType
      obj['conversationId'] = data?.conversationId

      let testingRunResultHexId = data.hexId;

      if (issuesDescriptionMap && Object.keys(issuesDescriptionMap).length > 0) {
        if (issuesDescriptionMap[testingRunResultHexId]) {
          obj['description'] = issuesDescriptionMap[testingRunResultHexId];
        }
      }

      if (jiraIssuesMapForResults && Object.keys(jiraIssuesMapForResults).length > 0) {
        if (jiraIssuesMapForResults[testingRunResultHexId]) {
          obj['jiraIssueUrl'] = jiraIssuesMapForResults[testingRunResultHexId];
        }
      }

      return obj;
    },
    prepareTestRunResults : (hexId, testingRunResults, subCategoryMap, subCategoryFromSourceConfigMap, issuesDescriptionMap, jiraIssuesMapForResults) => {
      let testRunResults = []
      testingRunResults.forEach((data) => {
        let obj = transform.prepareTestRunResult(hexId, data, subCategoryMap, subCategoryFromSourceConfigMap, issuesDescriptionMap, jiraIssuesMapForResults);
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

  getJiraComponent(jiraIssueUrl) {
    var key = /[^/]*$/.exec(jiraIssueUrl)[0];
    return jiraIssueUrl?.length > 0 ? (
      <Box>
              <Tag>
                  <HorizontalStack gap={1}>
                    <Avatar size="extraSmall" shape='round' source="/public/logo_jira.svg" />
                    <Link url={jiraIssueUrl}>
                      <Text>
                        {key}
                      </Text>
                    </Link>
                  </HorizontalStack>
                </Tag>
          </Box>
    ) : <Text> No Jira ticket created. Click on the top right button to create a new ticket.</Text>
  },

  fillMoreInformation(category, moreInfoSections, affectedEndpoints, jiraIssueUrl, createJiraTicket) {
    const jiraComponent = this.getJiraComponent(jiraIssueUrl)
    
    //<Box width="300px"><Button onClick={createJiraTicket} plain disabled={window.JIRA_INTEGRATED != "true"}>Click here to create a new ticket</Button></Box>
    let filledSection = []
    moreInfoSections.forEach((section) => {
      let sectionLocal = {...section}
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
            <Text>
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
        case "Compliance":
          if (category?.compliance?.mapComplianceToListClauses && Object.keys(category?.compliance?.mapComplianceToListClauses).length > 0) {
            sectionLocal.content = (
              <HorizontalStack gap="2">
                {
                  Object.keys(category?.compliance?.mapComplianceToListClauses).map((compliance, index) => {
                    return (
                      <Tag key={index} background="white">
                        <HorizontalStack wrap="false" gap={1}>
                          <Avatar source={func.getComplianceIcon(compliance)} shape="square"  size="extraSmall"/>
                          <Text>{compliance}</Text>
                        </HorizontalStack>  
                      </Tag>
                    )
                  })
                }
              </HorizontalStack>
            )
          }
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
                        <Text>
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
                      <TooltipText text={item.id.apiInfoKey.method + " "  + item.id.apiInfoKey.url}  tooltip={item.id.apiInfoKey.method + " "  + item.id.apiInfoKey.url} />
                    </List.Item>)
                })
              }
            </List>
          )
          break;
          case "Jira":
              sectionLocal.content = jiraComponent
              break;
          default:
            sectionLocal.content = section.content
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
              apiKeyInfoList.push({ 'url': apiKeyInfo['url'], 'method': apiKeyInfo['method'], 'apiCollectionId': Number(apiKeyInfo['apiCollectionId']) })
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
            url: valueFromPredicate[index]['url'],
            apiCollectionId: valueFromPredicate[index]['apiCollectionId']
          }
          apiInfoKeyList.push(apiEndpoint)
        }
        valueForCondition[collectionId] = apiInfoKeyList
        conditions.push({ operator: operator, type: e.type, value: valueForCondition })
      } else {
        conditions.push({ operator: operator, type: e.type, value: valueFromPredicate })
      }
    })
  },

  createConditions(data) {
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
  async getAllSubcategoriesData(fetchActive,type){
    let finalDataSubCategories = [], promises = [], categories = [];
    let testSourceConfigs = []
    const limit = 50;
    for(var i = 0 ; i < 40; i++){
      promises.push(
        api.fetchAllSubCategories(fetchActive, type, i * limit, limit)
      )
    }
    const allResults = await Promise.allSettled(promises);
    for (const result of allResults) {
      if (result.status === "fulfilled"){
        if(result?.value?.subCategories && result?.value?.subCategories !== undefined && result?.value?.subCategories.length > 0){
          finalDataSubCategories.push(...result.value.subCategories);
        }

        if(result?.value?.categories && result?.value?.categories !== undefined && result?.value?.categories.length > 0){
          if(categories.length === 0){
            categories.push(...result.value.categories);
          }
        }

        if (result?.value?.testSourceConfigs &&
          result?.value?.testSourceConfigs !== undefined &&
          result?.value?.testSourceConfigs.length > 0) {
          testSourceConfigs = result?.value?.testSourceConfigs
        }
      }
    }
    return {
      categories: categories,
      subCategories: finalDataSubCategories,
      testSourceConfigs: testSourceConfigs
    }
  },
  async setTestMetadata() {
    const resp = await this.getAllSubcategoriesData(false, "Dashboard")
    let subCategoryMap = {};
    resp.subCategories.forEach((x) => {
      func.trimContentFromSubCategory(x)
      subCategoryMap[x.name] = x;
    });
    let subCategoryFromSourceConfigMap = {};
    resp.testSourceConfigs.forEach((x_1) => {
      subCategoryFromSourceConfigMap[x_1.id] = x_1;
    });
    let categoryMap = {};
    resp.categories.forEach((category) => {
      categoryMap[category.name] = category;
    });
    LocalStore.getState().setSubCategoryMap(subCategoryMap);
    PersistStore.getState().setSubCategoryFromSourceConfigMap(subCategoryFromSourceConfigMap);
    LocalStore.getState().setCategoryMap(categoryMap);
  },
  prettifySummaryTable(summaries) {
    summaries = summaries.map((obj) => {
      const date = new Date(obj.startTimestamp * 1000)
      return{
        ...obj,
        prettifiedSeverities: observeFunc.getIssuesList(obj.countIssues || {"CRITICAL": 0, "HIGH" : 0, "MEDIUM": 0, "LOW": 0}),
        startTime: date.toLocaleString('en-US',{timeZone: window.TIME_ZONE === 'Us/Pacific' ? 'America/Los_Angeles' : window.TIME_ZONE}) + " on " +  date.toLocaleDateString('en-US',{timeZone: window.TIME_ZONE === 'Us/Pacific' ? 'America/Los_Angeles' : window.TIME_ZONE}),
        id: obj.hexId
      }
    })
    return summaries;
},

getInfoSectionsHeaders(){
  let moreInfoSections = [
    {
      icon: RiskMajor,
      title: "Impact",
      content: "",
      tooltipContent: 'The impact of the test on apis in general scenario.'
    },
    {
      icon: CollectionsMajor,
      title: "Tags",
      content: "",
      tooltipContent: 'Category info about the test.'
    },
    {
      icon: LockMajor,
      title: "Compliance",
      content: "",
      tooltipContent: "Compliances for the above test"
    },
    {
      icon: CreditCardSecureMajor,
      title: "CWE",
      content: "",
      tooltipContent: "CWE tags associated with the test from akto's test library"
    },
    {
      icon: FraudProtectMajor,
      title: "CVE",
      content: "",
      tooltipContent: "CVE tags associated with the test from akto's test library"
    },
    {
      icon: MarketingMajor,
      title: "API endpoints affected",
      content: "",
      tooltipContent: "Affecting endpoints in your inventory for the same test"
    },
    {
      icon: ResourcesMajor,
      title: "References",
      content: "",
      tooltipContent: "References for the above test"
    },
    {
      icon: ResourcesMajor,
      title: "Jira",
      content: "",
      tooltipContent: "Jira ticket number attached to the testing run issue"
    }
  ]
  return moreInfoSections
  },
convertSubIntoSubcategory(resp){
  let obj = {}
  let countObj = {
    CRITICAL: 0,
    HIGH: 0,
    MEDIUM: 0,
    LOW: 0,
  }
  const subCategoryMap = LocalStore.getState().subCategoryMap
  Object.keys(resp).forEach((key)=>{
    const objectKey = subCategoryMap[key] ? subCategoryMap[key].superCategory.shortName : key;
    const objectKeyName = subCategoryMap[key] ? subCategoryMap[key].superCategory.name : key;
    if(obj.hasOwnProperty(objectKey)){
      let tempObj =  JSON.parse(JSON.stringify(obj[objectKey]));
      let newObj = {
        ...tempObj,
        text: resp[key] + tempObj.text
      }
      obj[objectKey] = newObj;
      countObj[subCategoryMap[key].superCategory.severity._name]+=resp[key]
    }
    else if(!subCategoryMap[key]){
      obj[objectKey] = {
        text: resp[key],
        color: func.getColorForCharts(key),
        filterKey: objectKeyName
      }
      countObj.HIGH+=resp[key]
    }else{
      obj[objectKey] = {
        text: resp[key],
        color: func.getColorForCharts(subCategoryMap[key].superCategory.name),
        filterKey: objectKeyName
      }
      countObj[subCategoryMap[key].superCategory.severity._name]+=resp[key]
    }

  })

  const sortedEntries = Object.entries(obj).sort(([, val1], [, val2]) => {
    const prop1 = val1['text'];
    const prop2 = val2['text'];
    return prop2 - prop1 ;
  });
  
  return {
    subCategoryMap: Object.fromEntries(sortedEntries),
    countMap: countObj
  }

},
getUrlComp(url){
  let arr = url.split(' ')
  const endpoint = arr[1]
  const method = getMethod(endpoint, arr[0]);
  const finalEndpoint = observeFunc.getTruncatedUrl(endpoint)

  return(
    <HorizontalStack gap={1}>
      <Box width="54px">
        <HorizontalStack align="end">
          <Text variant="bodyMd" fontWeight="medium" color="subdued">{method}</Text>
        </HorizontalStack>
      </Box>
      <div style={{fontSize: '14px', lineHeight: '20px', color: '#202223'}} data-testid="affected_endpoints">{finalEndpoint}</div>
    </HorizontalStack>
  )
},

getCollapsibleRow(urls, severity) {
    const borderStyle = '4px solid ' + func.getHexColorForSeverity(severity?.toUpperCase());
    return(
      <tr style={{background: "#FAFBFB", borderLeft: borderStyle, padding: '0px !important', borderTop: '1px solid #dde0e4'}}>
        <td colSpan={8} style={{padding: '0px !important', width: '100%'}}>
          {urls.map((ele,index)=>{
            const jiraKey = ele?.jiraIssueUrl && ele?.jiraIssueUrl?.length > 0 ? ele.jiraIssueUrl?.split('/').pop() : "";
            const borderStyle = index < (urls.length - 1) ? {borderBlockEndWidth : 1} : {}
            return(
              <Box
                padding={"2"}
                paddingInlineStart={"4"}
                key={index}
                borderColor="border-subdued"
                {...borderStyle}
                width="100%"
              >
                <div style={{ display: "flex", alignItems: "center", width: "100%" }}>
                  <HorizontalStack gap="2" align="start" blockAlign="center">
                    <IssuesCheckbox id={ele.testRunResultsId} />
                    <Link monochrome onClick={() => history.navigate(ele.nextUrl)} removeUnderline>
                      {transform.getUrlComp(ele.url)}
                    </Link>
                    {ele.jiraIssueUrl && <JiraTicketDisplay jiraTicketUrl={ele.jiraIssueUrl} jiraKey={jiraKey} />}
                    <Box maxWidth="250px" paddingInlineStart="3">
                      <TooltipText
                        text={ele.issueDescription}
                        tooltip={ele.issueDescription}
                        textProps={{ color: "subdued"}}
                      />
                    </Box>
                  </HorizontalStack>
                  <div style={{ marginLeft: "auto" }}>
                    <Text color="subdued" fontWeight="semibold">
                    {ele.statusCode  || "-"}
                    {/* add a tooltip to show the response body */}
                    {/* handle the case where the response body is null */}
                    {ele.responseBody ? <TooltipText tooltip={ele.responseBody} text={ele.responseBody} /> : "-"}
                    </Text>
                  </div>
                </div>
              </Box>
            )
          })}
        </td>
      </tr>
    )
},

getTestErrorType(message){
  const errorsObject = TestingStore.getState().errorsObject
  for(var key in errorsObject){
    if(errorsObject[key] === message || message.includes(errorsObject[key])){
      return key
    }
  }
  return "UNKNOWN_ERROR_OCCURRED"
},

getPrettifiedTestRunResults(testRunResults){
  const errorsObject = TestingStore.getState().errorsObject
  let testRunResultsObj = {}
  testRunResults.forEach((test)=>{
    let key = test.name + ': ' + test.vulnerable + ": " + test.severity
    let error_message = ""
    if(test?.errorsList.length > 0){
      const errorType = this.getTestErrorType(test.errorsList[0])
      key = key + ': ' + errorType
      if(errorType === "ROLE_NOT_FOUND"){
        const baseUrl = window.location.origin+"/dashboard/testing/roles/details?system="
        const missingConfigs = func.toSentenceCase(test.errorsList[0].split(errorsObject["ROLE_NOT_FOUND"])[0]).split(" ");
        error_message = (
          <HorizontalStack gap={"1"}>
            {missingConfigs.map((config, index) => {
              return(
                config.length > 0 ?
                  <div className="div-link" onClick={(e) => {e.stopPropagation();window.open(baseUrl + config.toUpperCase(), "_blank")}} key={index}>
                    <span style={{ lineHeight: '16px', fontSize: '14px', color: "#B98900"}}>{func.toSentenceCase(config || "")}</span>
                  </div>
                : null
              )
            })}
          </HorizontalStack>
        )
      } else{
        error_message = errorsObject[errorType]
      }
    }

    const listOfTestResults = test.testResults
    let statusCode = null
    let responseBody = null
    if (listOfTestResults && listOfTestResults.length > 0){
      listOfTestResults.forEach((testResult) => {
        let message = testResult.message
        if (message) {
          try {
            let obj = JSON.parse(message)
            statusCode = obj?.response?.statusCode
            responseBody = obj?.response?.body?.slice(0, 50) + "..."
          } catch (e) {
          }
        }
      })
    }
    if(testRunResultsObj.hasOwnProperty(key)){
      let endTimestamp = Math.max(test.endTimestamp, testRunResultsObj[key].endTimestamp)
      let urls = testRunResultsObj[key].urls
      urls.push({url: test.url, nextUrl: test.nextUrl, testRunResultsId: test.id, statusCode: statusCode, responseBody: responseBody, issueDescription: test.description, jiraIssueUrl: test.jiraIssueUrl})
      let obj = {
        ...test,
        urls: urls,
        endTimestamp: endTimestamp,
        errorMessage: error_message,
      }
      delete obj["nextUrl"]
      delete obj["url"]
      delete obj["errorsList"]
      testRunResultsObj[key] = obj
    }else{
      let urls = [{url: test.url, nextUrl: test.nextUrl, testRunResultsId: test.id, statusCode: statusCode, responseBody: responseBody, issueDescription: test.description, jiraIssueUrl: test.jiraIssueUrl}]
      let obj={
        ...test,
        urls:urls,
        errorMessage: error_message,
      }
      delete obj["nextUrl"]
      delete obj["url"]
      delete obj["errorsList"]
      testRunResultsObj[key] = obj
    }
  })
  let prettifiedResults = []
  Object.keys(testRunResultsObj).forEach((key)=>{
    let obj = testRunResultsObj[key]
    let prettifiedObj = {
      ...obj,
      nameComp: <div data-testid={obj.name}><Box maxWidth="250px"><TooltipText tooltip={obj.name} text={obj.name} textProps={{fontWeight: 'medium'}}/></Box></div>,
      severityComp: obj?.vulnerable === true ? <div className={`badge-wrapper-${obj?.severity[0].toUpperCase()}`}>
      <Badge size="small" status={func.getTestResultStatus(obj?.severity[0])}>{obj?.severity[0]}</Badge>
  </div>: <Text>-</Text>,
      cweDisplayComp: obj?.cweDisplay?.length > 0 ? <HorizontalStack gap={1} wrap={false}>
        {obj.cweDisplay.map((ele,index)=>{
          return(
            <Badge size="small" status={func.getTestResultStatus(ele)} key={index}>{ele}</Badge>
          )
        })}
      </HorizontalStack> : <Text>-</Text>,
      totalUrls: obj.urls.length,
      scanned_time_comp: <Text variant="bodyMd">{func.prettifyEpoch(obj?.endTimestamp)}</Text>,
      collapsibleRow: this.getCollapsibleRow(obj.urls, obj?.severity[0]),
      urlFilters: obj.urls.map((ele) => ele.url)
    }
    prettifiedResults.push(prettifiedObj)
  })
  for (let prettifiedObj of prettifiedResults){
    let testingRunResultsIds = []
    for (let url of prettifiedObj.urls) {
      testingRunResultsIds.push(url.testRunResultsId)
    }
    prettifiedObj["id"] = testingRunResultsIds
    
  }
  return prettifiedResults
},
getTestingRunResultUrl(testingResult){
  let urlString = testingResult.url
  const methodObj = func.toMethodUrlObject(urlString)
  const finalMethod = getMethod(methodObj.url, methodObj.method);
  const truncatedUrl = observeFunc.getTruncatedUrl(methodObj.url)
  
  return finalMethod + " " + truncatedUrl
  
},
getRowInfo(severity, apiInfo,jiraIssueUrl, sensitiveData, isIgnored, azureBoardsWorkItemUrl, serviceNowTicketUrl, servicenowTicketId){
  if(apiInfo == null || apiInfo === undefined){
    apiInfo = {
      allAuthTypesFound: [],
      apiAccessTypes: [],
      lastSeen: 0,
      id: {
        method: "NA",
        url: "NA"
      }
    }
  }
  let auth_type = apiInfo["allAuthTypesFound"].join(", ")
  let access_type = null
  let access_types = apiInfo["apiAccessTypes"]
  if (!access_types || access_types.length == 0) {
      access_type = "No access type"
  } else if (access_types.indexOf("PUBLIC") !== -1) {
      access_type = "Public"
  } else if (access_types.indexOf("PARTNER") !== -1) {
      access_type = "Partner"
  } else if (access_types.indexOf("THIRD_PARTY") !== -1) {
      access_type = "Third-party"
  } else {
      access_type = "Private"
  }

  function TextComp ({value}) {
    return <Text breakWord variant="bodyMd">{value}</Text>
  }
  const key = /[^/]*$/.exec(jiraIssueUrl)[0];
  const jiraComponent = jiraIssueUrl?.length > 0 ? (
    <Box>
      <Tag>
          <HorizontalStack gap={1}>
            <Avatar size="extraSmall" shape='round' source="/public/logo_jira.svg" />
            <Link target="_blank" url={jiraIssueUrl}>
              <Text>
                {key}
              </Text>
            </Link>
          </HorizontalStack>
        </Tag>
    </Box>
  ) : null


  const azureBoardsComp = azureBoardsWorkItemUrl?.length > 0 ? (
    <Box>
      <Tag>
        <HorizontalStack gap={1}>
          <Avatar size="extraSmall" shape='round' source="/public/azure-boards.svg" />
          <Link target="_blank" url={azureBoardsWorkItemUrl}>
            <Text>
              {azureBoardsWorkItemUrl?.split("/")?.[azureBoardsWorkItemUrl?.split("/")?.length - 1]}
            </Text>
          </Link>
        </HorizontalStack>
      </Tag>
    </Box>
  ) : null

  const serviceNowComp = serviceNowTicketUrl?.length > 0 ? (
    <Box>
      <Tag>
        <HorizontalStack gap={1}>
          <Avatar size="extraSmall" shape='round' source="/public/servicenow.svg" />
          <Link target="_blank" url={serviceNowTicketUrl}>
            <Text>
              {servicenowTicketId || "View Ticket"}
            </Text>
          </Link>
        </HorizontalStack>
      </Tag>
    </Box>
  ) : null

  const rowItems = [
    {
      title: 'Severity',
      value: isIgnored ? <Text fontWeight="semibold">Ignored</Text> : <Text fontWeight="semibold"><span style={{color: observeFunc.getColor(severity)}}>{severity}</span></Text>,
      tooltipContent: "Severity of the test run result"
    },
    {
      title: mapLabel('API', getDashboardCategory()),
      value: (
          <GetPrettifyEndpoint methodBoxWidth="38px" maxWidth="180px" method={apiInfo.id.method} url={apiInfo.id.url} />
      ),
      tooltipContent: "Name of the api on which test is run"
    },
    {
      title: 'Hostname',
      value: <TextComp value={observeFunc.getHostName(apiInfo.id.url)} />,
      tooltipContent: "Hostname of the api on which test is run"
    },
    {
      title: "Auth type",
      value:<TextComp value={(auth_type || "").toLowerCase()} />,
      tooltipContent: "Authentication type of the api on which test is run"
    },
    {
      title: "Access type",
      value: <TextComp value={access_type} />,
      tooltipContent: "Access type of the api on which test is run"
    },
    {
      title: "Sensitive Data",
      value: <TextComp value={sensitiveData} />,
      tooltipContent: "Sensitive parameters detected in API data"
    },
    {
      title: "Detected",
      value: <TextComp value={func.prettifyEpoch(apiInfo.lastSeen)} />,
      tooltipContent: "Discovered time of the API"
    }
  ]

  if(jiraComponent != null) {
    rowItems.push({
      title: "Jira ticket",
      value: jiraComponent,
      tooltipContent:"Jira ticket number attached to the testing run issue"
    })
  }

  if(azureBoardsComp != null) {
    rowItems.push({
      title: "Azure work item",
      value: azureBoardsComp,
      tooltipContent: "Azure boards work item number attached to the testing run issue"
    })
  }

  if(serviceNowComp != null) {
    rowItems.push({
      title: "ServiceNow ticket",
      value: serviceNowComp,
      tooltipContent: "ServiceNow ticket attached to the testing run issue"
    })
  }

  return rowItems
},

stopTest(hexId){
  api.stopTest(hexId).then((resp) => {
    func.setToast(true, false, mapLabel("Test", getDashboardCategory()) + " run stopped")
  }).catch((resp) => {
    func.setToast(true, true, "Unable to stop test run")
  });
},

rerunTest(hexId, refreshSummaries, shouldRefresh, selectedTestRunForRerun, testingRunResultSummaryHexId){
  api.rerunTest(hexId, selectedTestRunForRerun, testingRunResultSummaryHexId).then((resp) => {
    window.location.reload()
    func.setToast(true, false, mapLabel("Test", getDashboardCategory()) + " re-run initiated")
    if(shouldRefresh){
      setTimeout(() => {
        refreshSummaries();
      }, 2000)
    }
  }).catch((resp) => {
    let additionalMessage = "";
    const { data } = resp?.response
    const { actionErrors } = data
    if (actionErrors !== null && actionErrors !== undefined && actionErrors.length > 0) {
      additionalMessage = ", " + actionErrors[0]
    }
    func.setToast(true, true, "Unable to re-" + mapLabel("run test", getDashboardCategory()) + additionalMessage);
  });
},
getActionsList(hexId){
  return [
  {
      content: 'Schedule test',
      icon: CalendarMinor,
      onAction: () => {console.log("schedule test function")},
  },
  {
      content: 'Re-run',
      icon: ReplayMinor,
      onAction: () => TestingStore.getState().setRerunModal(true),
  },
  {
      content: 'Add to CI/CD pipeline',
      icon: PlayMinor,
      onAction: () => {window.open('https://docs.akto.io/testing/run-tests-in-cicd', '_blank');},
  },
  {
      content: 'Stop',
      icon: CircleCancelMajor,
      destructive:true,
      onAction: () => {this.stopTest(hexId || ""); window.location.reload();},
      disabled: true,
  }
]},
getActions(item){
  let arr = []
  let section1 = {title: 'Actions', items:[]}
  let actionsList = this.getActionsList(item.id);
  if(item.orderPriority === 1){
    actionsList[1].disabled = true
  }
  section1.items.push(actionsList[1])
  if(item['run_type'] !== 'CI/CD'){
    section1.items.push(actionsList[2])
  }
  if(item.orderPriority < 3){
    actionsList[3].disabled = false
  }
  section1.items.push(actionsList[3]);
  arr.push(section1)
  return arr
},
getHeaders: (tab)=> {
  switch(tab){

      case "vulnerable":
          return headers;
      
      case "no_vulnerability_found":
          return headers.filter((header) => header.title !== "Severity")

      case "skipped":
          return headers.filter((header) => header.title !== "CWE tags").map((header) => {
              if (header.title === "Severity") {
                  // Modify the object as needed
                  return { type: CellType.TEXT, title: "Error message", value: 'errorMessage' };
              }
              return header;
          })

      case "need_configurations":
        return headers.filter((header) => header.title !== "CWE tags").map((header) => {
          if (header.title === "Severity") {
              // Modify the object as needed
              return { type: CellType.TEXT, title: "Configuration missing", value: 'errorMessage' };
          }
          return header;
      })

      default:
          return headers
  }
},
getMissingConfigs(testResults){
  const errorsObject = TestingStore.getState().errorsObject
  if(Object.keys(errorsObject).length === 0){
    return []
  }
  const configsSet = new Set();
  testResults.forEach((res) => {
    if(res?.errorsList.length > 0 && res.errorsList[0].includes(errorsObject["ROLE_NOT_FOUND"])){
      const config = func.toSentenceCase(res.errorsList[0].split(errorsObject["ROLE_NOT_FOUND"])[0])
      if(config.length > 0){
        let allConfigs = config.split(" ")
        allConfigs.filter(x => x.length > 1).forEach((x) => configsSet.add(func.toSentenceCase(x)))
      }
    }
  })

  return [...configsSet]
},
  prepareConditionsForTesting(conditions){
    let tempObj = {};
    conditions.forEach((condition) => {
      if(tempObj.hasOwnProperty(condition?.operator?.type)){
        tempObj[condition?.operator?.type].push(condition.data)
      }else{
        tempObj[condition?.operator?.type] = [condition.data]
      }
    })

    return Object.keys(tempObj).map((key) => {
      return {
        operatorType: key,
        operationsGroupList: tempObj[key],
      };
    });
  },
  prepareEditableConfigObject(testRun,settings,hexId,testSuiteIds=[],autoTicketingDetails){
    const tests = testRun.tests;
    const selectedTests = []
    Object.keys(tests).forEach(category => {
        tests[category].forEach(test => {
            if (test.selected) selectedTests.push(test.value)
        })
    })

    return {
      configsAdvancedSettings:settings,
      testRoleId: testRun.testRoleId,
      testSubCategoryList: testSuiteIds?.length === 0? selectedTests : [],
      overriddenTestAppUrl: testRun.hasOverriddenTestAppUrl ? testRun.overriddenTestAppUrl : "",
      maxConcurrentRequests: testRun.maxConcurrentRequests,
      testingRunHexId: hexId,
      testRunTime: testRun.testRunTime,
      sendSlackAlert: testRun.sendSlackAlert,
      sendMsTeamsAlert:testRun.sendMsTeamsAlert,
      recurringDaily: testRun.recurringDaily,
      continuousTesting: testRun.continuousTesting,
      scheduleTimestamp: testRun?.hourlyLabel === 'Now' && ((testRun.startTimestamp - func.getStartOfTodayEpoch()) < 86400) ? 0 : testRun.startTimestamp,
      recurringWeekly: testRun.recurringWeekly,
      recurringMonthly: testRun.recurringMonthly,
      miniTestingServiceName: testRun.miniTestingServiceName,
      testSuiteIds: testSuiteIds,
      autoTicketingDetails: autoTicketingDetails,
      selectedSlackChannelId: testRun?.slackChannel || 0,
    }
  },
  prepareTestingEndpointsApisList(apiEndpoints) {
    const collectionsMap = PersistStore.getState().collectionsMap;
    const testingEndpointsApisList = apiEndpoints.map(api => ({
      ...api,
      id: api.method + "###" + api.url + "###" + api.apiCollectionId + "###" + Math.random(),
      apiEndpointComp: <GetPrettifyEndpoint method={api.method} url={api.url} isNew={false} maxWidth="15vw" />,
      apiCollectionName: collectionsMap?.[api.apiCollectionId] || ""
    }));
    return testingEndpointsApisList;
  },
  prepareConversationsList(agentConversationResults){
    let conversationsListCopy = []
    agentConversationResults.forEach(conversation => {
        let commonObj = {
            creationTimestamp: conversation.timestamp,
            conversationId: conversation.conversationId,
        }
        conversationsListCopy.push({
            ...commonObj,
            _id: "user_" + conversation.prompt,
            message: conversation.prompt,
            role: "user"
        })
        conversationsListCopy.push({
            ...commonObj,
            _id: "system_" + conversation.response,
            message: conversation.response,
            role: "system"
        })
    })
    return conversationsListCopy;
  }
}

export default transform