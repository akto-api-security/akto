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
} from '@shopify/polaris-icons';
import React from 'react'
import { Text,HorizontalStack, Badge, Link, List, Box, Icon, Avatar, Tag, Tooltip} from '@shopify/polaris';
import { history } from "@/util/history";
import PersistStore from "../../../main/PersistStore";
import observeFunc from "../observe/transform";
import TooltipText from "../../components/shared/TooltipText";
import TestingStore from "./testingStore";

import { CellType } from "@/apps/dashboard/components/tables/rows/GithubRow";

let headers = [
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
      title: 'Number of urls',
      value: 'totalUrls',
      type: CellType.TEXT
    },
    {
      value: "scanned_time_comp",
      title: 'Scanned',
      sortActive: true
    },
    {
      title: '',
      type: CellType.COLLAPSIBLE
    }
]

const MAX_SEVERITY_THRESHOLD = 100000;

function getStatus(state) {
  return state._name ? state._name : (state.name ? state.name : state)
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
  if (testingRun.scheduleTimestamp >= func.timeNow() && testingRun.scheduleTimestamp < func.timeNow() + 86400) {
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
  const currTime = Date.now();
  if (endTimestamp <= 0) {
    if (currTime > scheduleTimestamp) {
      return <div data-testid="test_run_status">Was scheduled for {func.prettifyEpoch(scheduleTimestamp)}</div>;

    } else {
      return <div data-testid="test_run_status">Next run in {func.prettifyEpoch(scheduleTimestamp)}</div>;
    }
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
        'High': data['HIGH'] || 0,
        'Medium': data['MEDIUM'] || 0,
        'Low': data['LOW'] || 0
      };
      return obj;
    },
    prettifyTestName: (testName, icon, iconColor, iconToolTipContent)=>{
      return(
        <HorizontalStack gap={4}>
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
    if (testingRunResultSummary.countIssues != null) {
      testingRunResultSummary.countIssues = transform.prepareCountIssues(testingRunResultSummary.countIssues);
    }

    let state = data.state;
    if (checkTestFailure(testingRunResultSummary.state, state)) {
      state = 'FAIL'
    }

    let apiCollectionId = -1
    if(Object.keys(data).length > 0){
      apiCollectionId = data?.testingEndpoints?.apiCollectionId ||  data?.testingEndpoints?.workflowTest?.apiCollectionId || data?.testingEndpoints?.apisList[0]?.apiCollectionId
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
      obj['run_time_epoch'] = Math.max(data.scheduleTimestamp,data.endTimestamp)
      obj['scheduleTimestamp'] = data.scheduleTimestamp
      obj['pickedUpTimestamp'] = data.pickedUpTimestamp
      obj['run_time'] = getRuntime(data.scheduleTimestamp ,data.endTimestamp, state)
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
      if(prettified){
        
        const prettifiedTest={
          ...obj,
          testName: transform.prettifyTestName(data.name || "Test", iconObj.icon,iconObj.color, iconObj.tooltipContent),
          severity: observeFunc.getIssuesList(transform.filterObjectByValueGreaterThanZero(testingRunResultSummary.countIssues || {"HIGH" : 0, "MEDIUM": 0, "LOW": 0}))
        }
        return prettifiedTest
      }else{
        return obj
      }
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
    prepareTestRunResult : (hexId, data, subCategoryMap, subCategoryFromSourceConfigMap) => {
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
      obj['nextUrl'] = "/dashboard/testing/"+ hexId + "/result/" + data.hexId;
      obj['cwe'] = subCategoryMap[data.testSubType]?.cwe ? subCategoryMap[data.testSubType]?.cwe : []
      obj['cweDisplay'] = minimizeTagList(obj['cwe'])
      obj['cve'] = subCategoryMap[data.testSubType]?.cve ? subCategoryMap[data.testSubType]?.cve : []
      obj['cveDisplay'] = minimizeTagList(obj['cve'])
      obj['errorsList'] = data.errorsList || []
      obj['testCategoryId'] = data.testSubType
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

  fillMoreInformation(category, moreInfoSections, affectedEndpoints, jiraIssueUrl, createJiraTicket) {
    var key = /[^/]*$/.exec(jiraIssueUrl)[0];
    const jiraComponent = jiraIssueUrl?.length > 0 ? (
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
  async setTestMetadata() {
    const resp = await api.fetchAllSubCategories(true, "Dashboard");
    let subCategoryMap = {};
    resp.subCategories.forEach((x) => {
      subCategoryMap[x.name] = x;
    });
    let subCategoryFromSourceConfigMap = {};
    resp.testSourceConfigs.forEach((x_1) => {
      subCategoryFromSourceConfigMap[x_1.id] = x_1;
    });
    PersistStore.getState().setSubCategoryMap(subCategoryMap);
    PersistStore.getState().setSubCategoryFromSourceConfigMap(subCategoryFromSourceConfigMap);
  },
  prettifySummaryTable(summaries) {
    summaries = summaries.map((obj) => {
      const date = new Date(obj.startTimestamp * 1000)
      return{
        ...obj,
        prettifiedSeverities: observeFunc.getIssuesList(obj.countIssues || {"HIGH" : 0, "MEDIUM": 0, "LOW": 0}),
        startTime: date.toLocaleTimeString() + " on " +  date.toLocaleDateString(),
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
      tooltipContent: "References for the above test."
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
    HIGH: 0,
    MEDIUM: 0,
    LOW: 0,
  }
  const subCategoryMap = PersistStore.getState().subCategoryMap
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
  const method = arr[0]
  const endpoint = arr[1]

  return(
    <HorizontalStack gap={1}>
      <Box width="54px">
        <HorizontalStack align="end">
          <Text variant="bodyMd" fontWeight="medium" color="subdued">{method}</Text>
        </HorizontalStack>
      </Box>
      <div style={{fontSize: '14px', lineHeight: '20px', color: '#202223'}} data-testid="affected_endpoints">{endpoint}</div>
    </HorizontalStack>
  )
},

getCollapsibleRow(urls, severity){
  const borderStyle = '4px solid ' + func.getHexColorForSeverity(severity?.toUpperCase());
  return(
    <tr style={{background: "#FAFBFB", borderLeft: borderStyle, padding: '0px !important', borderTop: '1px solid #dde0e4'}}>
      <td colSpan={7} style={{padding: '0px !important'}}>
          {urls.map((ele,index)=>{
            const borderStyle = index < (urls.length - 1) ? {borderBlockEndWidth : 1} : {}
            return( 
              <Box padding={"2"} paddingInlineEnd={"4"} paddingInlineStart={"4"} key={index}
                  borderColor="border-subdued" {...borderStyle}
              >
                <Link monochrome onClick={() => history.navigate(ele.nextUrl)} removeUnderline >
                  {this.getUrlComp(ele.url)}
                </Link>
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
    let key = test.name + ': ' + test.vulnerable
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
      }else{
        error_message = errorsObject[errorType]
      }
    }

    if(testRunResultsObj.hasOwnProperty(key)){
      let endTimestamp = Math.max(test.endTimestamp, testRunResultsObj[key].endTimestamp)
      let urls = testRunResultsObj[key].urls
      urls.push({url: test.url, nextUrl: test.nextUrl})
      let obj = {
        ...test,
        urls: urls,
        endTimestamp: endTimestamp,
        errorMessage: error_message
      }
      delete obj["nextUrl"]
      delete obj["url"]
      delete obj["errorsList"]
      testRunResultsObj[key] = obj
    }else{
      let urls = [{url: test.url, nextUrl: test.nextUrl}]
      let obj={
        ...test,
        urls:urls,
        errorMessage: error_message
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
      severityComp: obj?.vulnerable === true ? <Badge size="small" status={func.getTestResultStatus(obj?.severity[0])}>{obj?.severity[0]}</Badge> : <Text>-</Text>,
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
  return prettifiedResults
},
getTestingRunResultUrl(testingResult){
  let urlString = testingResult.url
  const methodObj = func.toMethodUrlObject(urlString)
  const truncatedUrl = observeFunc.getTruncatedUrl(methodObj.url)
  
  return methodObj.method + " " + truncatedUrl
  
},
getRowInfo(severity, apiInfo,jiraIssueUrl, sensitiveData){
  let auth_type = apiInfo["allAuthTypesFound"].join(", ")
  let access_type = null
  let access_types = apiInfo["apiAccessTypes"]
  if (!access_types || access_types.length == 0) {
      access_type = "none"
  } else if (access_types.indexOf("PUBLIC") !== -1) {
      access_type = "Public"
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
            <Link url={jiraIssueUrl}>
              <Text>
                {key}
              </Text>
            </Link>
          </HorizontalStack>
        </Tag>
    </Box>
  ) : null

  const rowItems = [
    {
      title: 'Severity',
      value: <Text fontWeight="semibold" color={observeFunc.getColor(severity)}>{severity}</Text>,
      tooltipContent: "Severity of the test run result"
    },
    {
      title: "API",
      value: (
        <HorizontalStack gap={"1"}>
          <Text color="subdued" fontWeight="semibold">{apiInfo.id.method}</Text>
          <TextComp value={observeFunc.getTruncatedUrl(apiInfo.id.url)} />
        </HorizontalStack>
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
    },
    {
      title: "Jira",
      value: jiraComponent,
      tooltipContent:"Jira ticket number attached to the testing run issue"
    }
  ]
  return rowItems
},

stopTest(hexId){
  api.stopTest(hexId).then((resp) => {
    func.setToast(true, false, "Test run stopped")
  }).catch((resp) => {
    func.setToast(true, true, "Unable to stop test run")
  });
},

rerunTest(hexId, refreshSummaries, shouldRefresh){
  api.rerunTest(hexId).then((resp) => {
    window.location.reload()
    func.setToast(true, false, "Test re-run initiated")
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
    func.setToast(true, true, "Unable to re-run test" + additionalMessage);
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
  if(item['run_type'] === 'One-time'){
    section1.items.push(actionsList[1])
  }
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
}
}

export default transform