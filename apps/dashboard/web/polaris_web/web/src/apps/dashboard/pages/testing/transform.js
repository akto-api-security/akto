import func from "@/util/func";
import api from "./api";
import React, {  } from 'react'
import { Text,HorizontalStack, Badge, Link, List, Box, Icon, VerticalStack, Avatar} from '@shopify/polaris';
import PersistStore from "../../../main/PersistStore";
import observeFunc from "../observe/transform";
import TooltipText from "../../components/shared/TooltipText";

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

function checkTestFailure(summaryState, testRunState){
  if(testRunState=='COMPLETED' && summaryState!='COMPLETED'){
    return true;
  }
  return false;
}

const transform = {
    tagList : (list, cweLink) => {

      let ret = list?.map((tag, index) => {

        let linkUrl = ""
        if(cweLink){ 
          let cwe = tag.split("-")
          if(cwe[1]){
              linkUrl = `https://cwe.mitre.org/data/definitions/${cwe[1]}.html`
          }
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
    prettifyTestName: (testName, icon, iconColor, state)=>{
      let iconComp
      switch(state){
        case "COMPLETED":
          iconComp = (<Avatar shape="round" size="extraSmall" source="/public/circle_tick_minor.svg" />)
          break;
        case "STOPPED":
          iconComp = (<Avatar shape="round" size="extraSmall" source="/public/circle_cancel.svg" />)
          break;
        default:
          iconComp = (<Box><Icon source={icon} color={iconColor}/></Box>)
          break;
      }
      return(
        <HorizontalStack gap={4}>
          {iconComp}
          <Box maxWidth="400px">
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
    prepareTestRun : (data, testingRunResultSummary, cicd, prettified) => {
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
      if(prettified){
        const prettifiedTest={
          ...obj,
          testName: transform.prettifyTestName(data.name || "Test", func.getTestingRunIcon(state),func.getTestingRunIconColor(state), state),
          number_of_tests: getTestsInfo(testingRunResultSummary?.testResultsCount, state),
          severity: observeFunc.getIssuesList(transform.filterObjectByValueGreaterThanZero(testingRunResultSummary.countIssues))
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
      obj['singleTypeInfos'] = data['singleTypeInfos'] || []
      obj['vulnerable'] = data['vulnerable'] || false
      obj['nextUrl'] = "/dashboard/testing/"+ hexId + "/result/" + data.hexId;
      obj['cwe'] = subCategoryMap[data.testSubType]?.cwe ? subCategoryMap[data.testSubType]?.cwe : []
      obj['cweDisplay'] = minimizeTagList(obj['cwe'])
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
  fillMoreInformation(runIssues, runIssuesArr, subCategoryMap, moreInfoSections){
    moreInfoSections[0].content = (
        <Text color='subdued'>
          {subCategoryMap[runIssues.id?.testSubCategory]?.issueImpact || "No impact found"}
        </Text>
      )
    moreInfoSections[1].content = (
        <HorizontalStack gap="2">
          {
            transform.tagList(subCategoryMap[runIssues.id.testSubCategory]?.issueTags)
          }
        </HorizontalStack>
      )
      moreInfoSections[2].content = (
        <HorizontalStack gap="2">
          {
            transform.tagList(subCategoryMap[runIssues.id.testSubCategory]?.cwe, true)
          }
        </HorizontalStack>
      )
      moreInfoSections[4].content = (
        <List type='bullet' spacing="extraTight">
          {
            subCategoryMap[runIssues.id?.testSubCategory]?.references?.map((reference) => {
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
      
        moreInfoSections[3].content = (
          <List type='bullet'>
            {
              runIssuesArr?.map((item, index) => {
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
},
convertSubIntoSubcategory(resp){
  let obj = {}
  const subCategoryMap = PersistStore.getState().subCategoryMap
  Object.keys(resp).forEach((key)=>{
    let temp = {
      text: resp[key],
      color: func.getColorForCharts(subCategoryMap[key].superCategory.name)
    }
    obj[subCategoryMap[key].superCategory.shortName] = temp

  })

  const sortedEntries = Object.entries(obj).sort(([, val1], [, val2]) => {
    const prop1 = val1['text'];
    const prop2 = val2['text'];
    return prop2 - prop1 ;
  });

  return Object.fromEntries(sortedEntries);

},

getUrlComp(url){
  let arr = url.split(' ')
  const method = arr[0]
  const endpoint = arr[1]

  return(
    <HorizontalStack gap={1}>
      <Box width="54px">
        <HorizontalStack align="end">
          <Text variant="bodyMd" color="subdued">{method}</Text>
        </HorizontalStack>
      </Box>
      <Text variant="bodyMd">{endpoint}</Text>
    </HorizontalStack>
  )
},

getCollapisbleRow(urls){
  return(
    <tr style={{background: "#EDEEEF"}}>
      <td colSpan={7}>
        <Box paddingInlineStart={4} paddingBlockEnd={2} paddingBlockStart={2}>
          <VerticalStack gap={2}>
            {urls.map((ele,index)=>{
              return(
                <Link monochrome url={ele.nextUrl} removeUnderline key={index}>
                  {this.getUrlComp(ele.url)}
                </Link>
              )
            })}
          </VerticalStack>
        </Box>
      </td>
    </tr>
  )
},

getPrettifiedTestRunResults(testRunResults){
  let testRunResultsObj = {}
  testRunResults.forEach((test)=>{
    const key = test.name + ': ' + test.vulnerable
    if(testRunResultsObj.hasOwnProperty(key)){
      let endTimestamp = Math.max(test.endTimestamp, testRunResultsObj[key].endTimestamp)
      let urls = testRunResultsObj[key].urls
      urls.push({url: test.url, nextUrl: test.nextUrl})
      let obj = {
        ...test,
        urls: urls,
        endTimestamp: endTimestamp
      }
      delete obj["nextUrl"]
      delete obj["url"]
      testRunResultsObj[key] = obj
    }else{
      let urls = [{url: test.url, nextUrl: test.nextUrl}]
      let obj={
        ...test,
        urls:urls,
      }
      delete obj["nextUrl"]
      delete obj["url"]
      testRunResultsObj[key] = obj
    }
  })
  let prettifiedResults = []
  Object.keys(testRunResultsObj).forEach((key)=>{
    let obj = testRunResultsObj[key]
    let prettifiedObj = {
      ...obj,
      nameComp: <Box maxWidth="250px"><TooltipText tooltip={obj.name} text={obj.name} textProps={{fontWeight: 'medium'}}/></Box>,
      severityComp: obj?.vulnerable === true ? <Badge size="small" status={func.getTestResultStatus(obj?.severity[0])}>{obj?.severity[0]}</Badge> : <Text>-</Text>,
      cweDisplayComp: obj?.cweDisplay?.length > 0 ? <HorizontalStack gap={1}>
        {obj.cweDisplay.map((ele,index)=>{
          return(
            <Badge size="small" status={func.getTestResultStatus(ele)} key={index}>{ele}</Badge>
          )
        })}
      </HorizontalStack> : <Text>-</Text>,
      totalUrls: obj.urls.length,
      scanned_time_comp: <Text variant="bodyMd">{func.prettifyEpoch(obj?.endTimestamp)}</Text>,
      collapsibleRow: this.getCollapisbleRow(obj.urls),
      urlFilters: obj.urls.map((ele) => ele.url)
    }
    prettifiedResults.push(prettifiedObj)
  })
  return prettifiedResults
}

}

export default transform