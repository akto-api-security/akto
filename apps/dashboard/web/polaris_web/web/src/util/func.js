import {
  CalendarMinor,ClockMinor,CircleAlertMajor,DynamicSourceMinor,DynamicSourceMajor, LockMinor, KeyMajor, ProfileMinor, PasskeyMinor,
  EmailMajor, CreditCardMajor, IdentityCardMajor, LocationsMinor,PhoneMajor, FileMinor, ImageMajor, BankMajor, HashtagMinor, 
  ReceiptMajor, MobileMajor, CalendarTimeMinor, LocationMajor,  IdentityCardFilledMajor, CalendarMajor, AffiliateMajor
} from '@shopify/polaris-icons';
import { saveAs } from 'file-saver'
import inventoryApi from "../apps/dashboard/pages/observe/api"
import { isValidElement } from 'react';
import Store from '../apps/dashboard/store';
import { current } from 'immer';
import homeFunctions from '../apps/dashboard/pages/home/module';
import { tokens } from "@shopify/polaris-tokens" 
import PersistStore from '../apps/main/PersistStore';

import { circle_cancel, circle_tick_minor, car_icon } from "@/apps/dashboard/components/icons";
import quickStartFunc from '../apps/dashboard/pages/quick_start/transform';
import { Box } from '@shopify/polaris';
import TooltipText from '../apps/dashboard/components/shared/TooltipText';
import { getMethod } from "../apps/dashboard/pages/observe/GetPrettifyEndpoint";
import observeFunc from '../apps/dashboard/pages/observe/transform';

const iconsUsedMap = {
  CalendarMinor,ClockMinor,CircleAlertMajor,DynamicSourceMinor, LockMinor, KeyMajor, ProfileMinor, PasskeyMinor,
  EmailMajor, CreditCardMajor, IdentityCardMajor, LocationsMinor,PhoneMajor, FileMinor, ImageMajor, BankMajor, HashtagMinor, 
  ReceiptMajor, MobileMajor, CalendarTimeMinor,LocationMajor, IdentityCardFilledMajor, CalendarMajor
}

const searchResultSections = {
  collections: {
    type: "collection",
    title: "Collections",
    icon: DynamicSourceMajor,
    sectionPath: "/dashboard/observe/inventory/"
  },
  tests: {
    type: "test",
    title: "Tests",
    icon: FileMinor,
    sectionPath: "/dashboard/test-editor/"
  },
  connectors: {
    type: "connector",
    title: "Connectors",
    icon: AffiliateMajor,
    sectionPath: "/dashboard/quick-start"
  },
}

const func = {
  setToast (isActive, isError, message) {
    Store.getState().setToastConfig({
          isActive: isActive,
          isError: isError,
          message: message
      })
  },
  formatJsonForEditor(data){
    let res = "";
    try{
      res = JSON.stringify(JSON.parse(data), null, 2)
    } catch {
      res = data;
    }
    return res
  },
  nameValidationFunc(nameVal, initialCond){
    let error = ""
    if(nameVal.length === 0 || initialCond){
      return error
    }
    const regex = /^[A-Z_0-9 ]+$/;
    if (!nameVal.toUpperCase().match(regex)){
      error = "Name can only contain alphabets, spaces, numbers and underscores"
    } else if(nameVal.length > 25){
      error = "Name too long. Maximum limit allowed is 25"
    }
    return error;
  },
  toDateStr(date, needYear) {
    let strArray = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];
    let d = date.getDate();
    let m = strArray[date.getMonth()];
    let y = date.getFullYear();
    return m + ' ' + d + (needYear ? ' ' + y : '');
  },
  prettifyShort(num) {
    return new Intl.NumberFormat( 'en-US', { maximumFractionDigits: 1,notation: "compact" , compactDisplay: "short" }).format(num)
  },



  timeDifference(startTimestamp, endTimestamp) {
    const diffMs = endTimestamp - startTimestamp;
    if(startTimestamp === 0) {
      return 'in total'
    }

    // Convert seconds to days
    const days = diffMs / (60 * 60 * 24);

    if (days <= 1) {
        return "yesterday";
    } else if (days < 7) {
        const dayCount = Math.ceil(days);
        return `in ${dayCount} day${dayCount === 1 ? '' : 's'}`;
    } else if (days <= 29) {
        const weekCount = Math.ceil(days / 7);
        return `in ${weekCount} week${weekCount === 1 ? '' : 's'}`;
    } else {
        const monthCount = Math.ceil(days / 31);
        const years = Math.floor(monthCount / 12);

        if (years > 0) {
            return `in ${years} year${years === 1 ? '' : 's'}`;
        } else {
            return `in ${monthCount} month${monthCount === 1 ? '' : 's'}`;
        }
    }
  },

prettifyEpoch(epoch) {
    if(epoch === 0){
      return "Never" ;
    }
    let diffSeconds = (+Date.now()) / 1000 - epoch
    let sign = 1
    if (diffSeconds < 0) { sign = -1 }

    if (diffSeconds < 120) {
      return '1 minute ago'
    } else if (diffSeconds < 3600) {
      return Math.round(diffSeconds / 60) + ' minutes ago'
    } else if (diffSeconds < 7200) {
      return '1 hour ago'
    } else if (diffSeconds < 86400) {
      return Math.round(diffSeconds / 3600) + ' hours ago'
    }

    let diffDays = diffSeconds / 86400
    let diffWeeks = diffDays / 7
    let diffMonths = diffDays / 30
    let count = Math.round(diffDays)
    let unit = 'day'

    if (diffMonths > 2) {
      return this.toDateStr(new Date(epoch * 1000), true)
    } else if (diffWeeks > 4) {
      count = Math.round(diffMonths + 0.5)
      unit = 'month'
    } else if (diffDays > 11) {
      count = Math.round(diffWeeks + 0.5)
      unit = 'week'
    } else if (diffDays === 1) {
      return sign > 0 ? 'tomorrow' : 'yesterday'
    } else if (diffDays === 0) {
      return 'today'
    }

    let plural = count <= 1 ? '' : 's'
    return count + ' ' + unit + plural + ' ago'
  },
  prettifyEpochDuration(diffSeconds) {
    if(diffSeconds <= 0){
      return "Error occurred while fetching the time"
    }
    const units = [
      { label: "week", duration: 604800 },
      { label: "day", duration: 86400 },
      { label: "hour", duration: 3600 },
      { label: "minute", duration: 60 },
      { label: "second", duration: 1 }
    ];

    let result = [];

    for (let unit of units) {
      if (diffSeconds >= unit.duration) {
        let value = Math.floor(diffSeconds / unit.duration);
        diffSeconds %= unit.seconds;
        result.push(`${value} ${unit.label}${value > 1 ? "s" : ""}`);
      }
    }

    return result.join(", ")
  },

  toSentenceCase(str) {
    if (str == null) return ""
    return str[0].toUpperCase() + (str.length > 1 ? str.substring(1).toLowerCase() : "");
  },
  testingResultType() {
    return {
      BURP: "BURP",
      CICD: "CICD",
      EXTERNAL_API: "EXTERNAL_API"
    }
  },
  initials(str) {
    if (!str)
      return ''

    let ret = str.split(" ").map((n) => n[0]).slice(0, 2).join("").toUpperCase()

    if (ret.length == 1) {
      return str.replaceAll(" ", "").slice(0, 2).toUpperCase()
    } else {
      return ret
    }
  },
  valToString(val) {
    if (val instanceof Set) {
      return [...val].join(" & ")
    } else {
      return val || "-"
    }
  },
  downloadAsCSV(data, selectedTestRun) {
    // use correct function, this does not expand objects.
    if(Object.keys(data).length === 0){
      return;
    }
    let headerTextToValueMap = Object.keys(data[0])

    let csv = headerTextToValueMap.join(",") + "\r\n"
    data.forEach(i => {
      csv += Object.values(headerTextToValueMap).map(h => this.valToString(i[h])).join(",") + "\r\n"
    })
    let blob = new Blob([csv], {
      type: "application/csvcharset=UTF-8"
    });
    saveAs(blob, (selectedTestRun.name || "file") + ".csv");
  },
  flattenObject(obj, prefix = '') {
    return obj && Object.keys(obj).reduce((acc, k) => {

      // skip react objects
      if(isValidElement(obj[k])){
        return acc;
      }

      const pre = prefix.length ? `${prefix}.` : '';
      if (
        typeof obj[k] === 'object' &&
        obj[k] !== null &&
        Object.keys(obj[k]).length > 0
      )
        Object.assign(acc, this.flattenObject(obj[k], pre + k));
      else acc[pre + k] = obj[k];
      return acc;
    }, {})
  },
  findInObjectValue(obj, query, keysToIgnore = []) {
    if(query=="") return true;
    let flattenedObject = this.flattenObject(obj);
    let ret = false;
    Object.keys(flattenedObject).forEach((key) => {
      ret |= !keysToIgnore.some(ignore => key.toLowerCase().includes(ignore.toLowerCase())) &&
        flattenedObject[key]?.toString().toLowerCase().includes(query);
    })
    return ret;
  },
  unflattenObject(flatObj) {
    const result = {};
  
    for (const key in flatObj) {
      const keys = key.split('.');
      let nestedObj = result;
  
      for (let i = 0; i < keys.length - 1; i++) {
        const currentKey = keys[i];
        if (/\d+/.test(keys[i + 1])) {
          nestedObj[currentKey] = nestedObj[currentKey] || [];
        } else {
          nestedObj[currentKey] = nestedObj[currentKey] || {};
        }
        nestedObj = nestedObj[currentKey];
      }
  
      const lastKey = keys[keys.length - 1];
      if (/\d+/.test(lastKey)) {
        const index = parseInt(lastKey, 10);
        nestedObj[index] = flatObj[key];
      } else {
        nestedObj[lastKey] = flatObj[key];
      }
    }
    return result;
  },
  sortObjectBySeverity(obj) {
    const severityOrder = this.getAktoSeverities()

    const sortedEntries = Object.entries(obj).sort(
        ([keyA], [keyB]) => severityOrder.indexOf(keyA) - severityOrder.indexOf(keyB)
    )

    return Object.fromEntries(sortedEntries)
  },
  getSeverityStatus(countIssues) {
    if(countIssues==null || countIssues==undefined){
      return [];
    }

    const sortedCountIssues = this.sortObjectBySeverity(countIssues)

    return Object.keys(sortedCountIssues).filter((key) => {
      return (sortedCountIssues[key] > 0)
    })
  },
  getTestingRunIconObj(state) {
    let testState = state?._name || state
    switch(testState?.toUpperCase()){
      case "RUNNING": 
        return {
          color: "subdued",
          icon: ClockMinor,
          tooltipContent: "Test is currently running"
        }

      case "SCHEDULED": 
        return {
          color: "warning",
          icon: CalendarMinor,
          tooltipContent: "Test is scheduled and will run in future."
        }

      case "FAILED":
      case "FAIL":
        return{
          color: "critical",
          icon: CircleAlertMajor,
          tooltipContent: "Error occurred while running the test."
        }

      case "STOPPED":
        return{
          tooltipContent: "Error occurred while running the test.",
          icon: circle_cancel,
        }
      case "COMPLETED": 
        return {
          tooltipContent: "Test has been completed.",
          icon: circle_tick_minor
        }
      default: 
        return{
          color: "critical",
          icon: CircleAlertMajor,
          tooltipContent: "Unknown error occurred while running the test."
        }
      }
  },
  getSeverity(countIssues) {
    return func.getSeverityStatus(countIssues).map((key) => {
      return countIssues[key] + " " + key
    })
  },
  getTestResultStatus(item) {
    let localItem = item.toUpperCase();
    if(localItem.includes("HIGH")) return 'critical';
    if(localItem.includes("MEDIUM")) return 'warning';
    if(localItem.includes("LOW")) return 'neutral';
    if(localItem.includes("CWE") || localItem.startsWith("+")) return 'info'
    if(localItem.includes("UNREAD") || localItem.startsWith("+")) return 'attention';
    return "";
  },
  getRunResultSubCategory(runResult, subCategoryFromSourceConfigMap, subCategoryMap, fieldName) {
    if (subCategoryMap[runResult.testSubType] === undefined) {
      let a = subCategoryFromSourceConfigMap[runResult.testSubType]
      return a ? a.subcategory : null
    } else {
      return subCategoryMap[runResult.testSubType][fieldName]
    }
  },

  getRunResultCategory(runResult, subCategoryMap, subCategoryFromSourceConfigMap, fieldName) {
    if (subCategoryMap[runResult.testSubType] === undefined) {
      let a = subCategoryFromSourceConfigMap[runResult.testSubType]
      return a ? a.category.shortName : null
    } else {
      return subCategoryMap[runResult.testSubType].superCategory[fieldName]
    }
  },

  getRunResultSeverity(runResult, subCategoryMap) {
    try {
      if (runResult?.testResults?.[0]?.confidence._name) {
        return runResult?.testResults?.[0]?.confidence._name
      } else if (runResult?.testResults?.[0]?.confidence) {
        return runResult?.testResults?.[0]?.confidence
      }
    } catch(e){
    }

    let testSubType = subCategoryMap[runResult.testSubType]
    if (!testSubType) {
      return "HIGH"
    } else {
      let a = testSubType.superCategory["severity"]["_name"]
      return a
    }
  },

  copyToClipboard(text, ref, toastMessage) {
    if (!navigator.clipboard) {
      // Fallback for older browsers (e.g., Internet Explorer)
      const textarea = document.createElement('textarea');
      textarea.value = text;
      textarea.style.position = 'fixed';
      textarea.style.opacity = 0;
      ref.current.appendChild(textarea);
      textarea.select();
      document.execCommand('copy');
      ref.current.removeChild(textarea);
      this.setToast(true,false,toastMessage ? toastMessage : 'Text copied to clipboard successfully!');
      return;
    }

    setTimeout(() => {
      navigator.clipboard.writeText(text).then(() => {
        this.setToast(true,false, toastMessage ? toastMessage : 'Text copied to clipboard successfully!');
      })
      .catch((err) => {
        this.setToast(true,true,`Failed to copy text to clipboard: ${err}`);
      });
    }, 0)
      
  },
  epochToDateTime(timestamp) {
    var date = new Date(timestamp * 1000);

    const timezone = (!window.TIME_ZONE || window.TIME_ZONE.trim() === '' || window.TIME_ZONE === 'Us/Pacific')
    ? 'America/Los_Angeles'
    : window.TIME_ZONE;

    return date.toLocaleString('en-US',{timeZone: timezone});
  },
  getFormattedDate(epoch) {
    const date = new Date(epoch * 1000)
    const day = date.getDate()
    const month = date.toLocaleString('default', { month: 'long' })
    const year = date.getFullYear()

    const suffix = (day) => {
        if (day >= 11 && day <= 13) return 'th'
        switch (day % 10) {
            case 1: return 'st'
            case 2: return 'nd'
            case 3: return 'rd'
            default: return 'th'
        }
    }

    return `${day}${suffix(day)} ${month}, ${year}`
  },

  getListOfHosts(apiCollections) {
    let result = []
    if (!apiCollections || apiCollections.length === 0) return []
    apiCollections.forEach((x) => {
      let hostName = x['hostName']
      if (!hostName) return
      result.push(
        {
          "label": hostName,
          "value": hostName
        }
      )
    })
    return result
  },
  convertTrafficMetricsToTrend(trafficMetricsMap) {
    let result = []
    for (const [key, countMap] of Object.entries(trafficMetricsMap)) {
      let trafficArr = []
      for (const [key, value] of Object.entries(countMap)) {
        const epochHours = parseInt(key);
        const epochMilliseconds = epochHours * 3600000;
        trafficArr.push([epochMilliseconds, value]);
      }

      result.push(
        { "data": trafficArr, "color": null, "name": key },
      )
    }
    return result
  },
  prepareFilters: (data, filters) => {
    if(!data || !filters){
      return []
    }
    let localFilters = filters;
    let filtersHaveChoices = true;
    for(var x in filtersHaveChoices){
      if(x.choices !== undefined || x.choices.length === 0){
        filtersHaveChoices = false;
        break;
      }
    }

    if(filtersHaveChoices){
      return filters;
    }

    localFilters.forEach((filter, index) => {
      localFilters[index].availableChoices = new Set()
      localFilters[index].choices = []
    })
    data.forEach((obj) => {
      localFilters.forEach((filter, index) => {
        let key = filter["key"]
        obj[key]?.
        filter(item => item!=undefined)
        .map((item) => filter.availableChoices.add(item));
        localFilters[index] = filter
      })
    })
    localFilters.forEach((filter, index) => {
      let choiceList = []
      filter.availableChoices.forEach((choice) => {
        choiceList.push({ label: choice, value: choice })
      })
      localFilters[index].choices = choiceList
    })
    return localFilters
  },
  timeNow: () => {
    return parseInt(new Date().getTime() / 1000)
  },
  // Check if API collections data caching is enabled for current account
  isApiCollectionsCachingEnabled: () => {
    const allowedAccounts = [1736798101, 1758595089];
    return allowedAccounts.includes(window.ACTIVE_ACCOUNT);
  },
  convertKeysToLowercase: function (obj){
    return Object.keys(obj).reduce((acc, k) => {
      acc[k.toLowerCase()] = obj[k];
      return acc;
    }, {});
  },
  requestJson: function (message, highlightPaths, metadata = []) {
    if(!message || typeof message !== "object" || Object.keys(message).length === 0){
      return {}
    }
    let result = {}
    let requestHeaders = {}

    const metaDataSet = new Set(metadata.map((x) => x.toLowerCase()))

    let requestHeadersString = "{}"
    let requestPayloadString = "{}"
    let queryParamsString = ""
    if (message["request"]) {
      queryParamsString = message["request"]["queryParams"]
      requestHeadersString = message["request"]["headers"] || "{}"
      requestPayloadString = message["request"]["body"] || "{}"
    } else {
      let url = message["path"]
      let urlSplit = (typeof url === "string") ? url?.split("?") : []
      queryParamsString = urlSplit?.length > 1 ? urlSplit[1] : ""

      requestHeadersString = message["requestHeaders"] || "{}"
      requestPayloadString = message["requestPayload"] || "{}"
    }

    const queryParams = {}
    if (queryParamsString) {
      let urlSearchParams = new URLSearchParams(queryParamsString)
      for (const [key, value] of urlSearchParams) {
        queryParams[key] = value;
      }
    }

    try {
      requestHeaders = func.convertKeysToLowercase(JSON.parse(requestHeadersString))
    } catch (e) {
      // eat it
    }

    Object.keys(requestHeaders).forEach((key) => {
      if(metaDataSet.has(key)){
        highlightPaths.push({
            "highlightValue": {
                "value": key,
                "wholeRow": true,
                "className": "akto-decoded",
                "highlight": true,
            },
            "responseCode": -1,
            "header": key,
            "param": key,
        })
      }
    })

    let requestPayload = {}
    try {
      requestPayload = JSON.parse(requestPayloadString)
    } catch (e) {
      requestPayload = requestPayloadString
    }

    result["json"] = { "queryParams": queryParams, "requestHeaders": requestHeaders, "requestPayload": requestPayload }
    result["highlightPaths"] = {}
    result['firstLine'] = func.requestFirstLine(message, queryParams)
    for (const x of highlightPaths) {
      if (x["responseCode"] === -1) {
        let keys = []
        if (x["header"]) {
          keys.push("root#" + "requestheaders#" + x["param"])
        } else {
          keys.push("root#" + "requestpayload#" + x["param"])
          keys.push("root#" + "queryParams#" + x["param"])
        }

        keys.forEach((key) => {
          key = key.toLowerCase()
          result["highlightPaths"][key] = x["highlightValue"]
        })
      }
    }
    return result
  },
  responseJson: function (message, highlightPaths, metadata = []) {

    if(!message || typeof message !== "object" || Object.keys(message).length === 0){
      return {}
    }
    let result = {}

    let responseHeadersString = "{}"
    let responsePayloadString = "{}"
    const metaDataSet = new Set(metadata.map((x) => x.toLowerCase()))
    if (message["request"]) {
      responseHeadersString = message["response"]["headers"] || "{}"
      responsePayloadString = message["response"]["body"] || "{}"
    } else {
      responseHeadersString = message["responseHeaders"] || "{}"
      responsePayloadString = message["responsePayload"] || "{}"
    }

    let responseHeaders = {};
    try {
      responseHeaders = func.convertKeysToLowercase(JSON.parse(responseHeadersString))
    } catch (e) {
      // eat it
    }
    let responsePayload = {}
    try {
      responsePayload = JSON.parse(responsePayloadString)
    } catch (e) {
      responsePayload = responsePayloadString
    }    

    Object.keys(responseHeaders).forEach((key) => {
      if(metaDataSet.has(key)){
        highlightPaths.push({
            "highlightValue": {
                "value": key,
                "wholeRow": true,
                "className": "akto-decoded",
                "highlight": true,
            },
            "header": key,
            "param": key,
        })
      }
    })

    result["json"] = { "responseHeaders": responseHeaders, "responsePayload": responsePayload }
    result["highlightPaths"] = {}
    result['firstLine'] = func.responseFirstLine(message)
    for (const x of highlightPaths) {
      if (x["responseCode"] !== -1) {
        let key = ""
        if (x["header"]) {
          key = "root#" + "responseheaders#" + x["param"]
        } else {
          key = "root#" + "responsepayload#" + x["param"];
        }
        key = key.toLowerCase();
        result["highlightPaths"][key] = x["highlightValue"]
      }
    }
    return result
  },
  requestFirstLine(message, queryParams) {
    if (message["request"]) {
      let url = message["request"]["url"] || ""
      return message["request"]["method"] + " " + url + func.convertQueryParamsToUrl(queryParams) + " " + message["request"]["type"]
    } else {
      let pathString = ""
      if(message.path !== null && message?.path !== undefined){
        pathString = message.path.split("?")[0];
      }
      return message?.method + " " + pathString + func.convertQueryParamsToUrl(queryParams) + " " + message?.type
    }
  },
  responseFirstLine(message) {
    if (message["response"]) {
      return message["response"]["statusCode"] + ""
    } else {
      return message.statusCode + " " + message.status
    }
  },
  mapCollectionIdToName(collections) {
    let collectionsObj = {}
    collections.forEach((collection)=>{
      if(!collectionsObj[collection.id]){
        collectionsObj[collection.id] = collection.displayName
      }
    })

    return collectionsObj
  },

  mapCollectionIdToRegistryStatus(collections) {
    let registryStatusObj = {}
    collections.forEach((collection)=>{
      if(!registryStatusObj[collection.id] && collection.registryStatus){
        registryStatusObj[collection.id] = collection.registryStatus
      }
    })

    return registryStatusObj
  },

  mapCollectionIdsToTagName(collections) {
        const allTagCollectionsMap = {};
              collections
                .filter(col => !col.deactivated && Array.isArray(col.envType) && col.envType.length > 0)
                .forEach(col => {
                  col.envType.forEach(env => {
                  const keyName = env && env.keyName && (env.keyName.startsWith('userSetEnvType') || env.keyName.startsWith('envType'))
                    ? env.keyName.replace(/^(userSetEnvType|envType)/, 'env')
                    : env.keyName;
                    const key = `${keyName}=${env.value}`;
                    if (!allTagCollectionsMap[key]) {
                      allTagCollectionsMap[key] = [];
                    }
                    allTagCollectionsMap[key].push(col.id);
                  });
                });
       return allTagCollectionsMap
    },
  mapCollectionId(collections) {
    let collectionsObj = {}
    collections.forEach((collection)=>{
      if(!collectionsObj[collection.id]){
        collectionsObj[collection.id] = collection
      }
    })
    return collectionsObj
  },
  reduceToCollectionName(collectionObj){
    return Object.keys(collectionObj).reduce((acc, k) => {
      acc[k] = collectionObj[k].displayName;
      return acc;
    }, {});
  },
  
sortFunc: (data, sortKey, sortOrder, treeView) => {
  if(sortKey === 'displayName'){
    let finalArr = data.sort((a, b) => {
        let nameA = ""
        if(a?.displayName?.length > 0){
          nameA = a?.displayName.toLowerCase() ;
        }else if(a?.name?.length > 0){
          nameA = a?.name.toLowerCase();
        }
        let nameB = ""
        if(b?.displayName?.length > 0){
          nameB = b?.displayName.toLowerCase() ;
        }else if(b?.name?.length > 0){
          nameB = b?.name.toLowerCase();
        }
    
        // Define a regex to check if the name starts with a digit
        const startsWithDigitA = /^\d/.test(nameA);
        const startsWithDigitB = /^\d/.test(nameB);
    
        // Alphabetical names should come first
        if (startsWithDigitA && !startsWithDigitB) return 1;
        if (!startsWithDigitA && startsWithDigitB) return -1;
    
        // If both names either start with a digit or both don't, compare them directly
        return nameA.localeCompare(nameB);
    });
    if(sortOrder > 0){
      finalArr.reverse()
    }
    return finalArr
  }else if(sortKey === 'customGroupsSort'){
    let arr1 = []
    let arr2 = []; 
    data.forEach((x) => {
      if(x.automated){
        arr2.push(x)
      }else{
        arr1.push(x)
      }
    })
    arr1 = arr1.sort((a, b) => {
      return (sortOrder) * (b['displayName'].localeCompare(a['displayName']))
    })
    arr2 = arr2.sort((a, b) => {
      return (sortOrder) * (b['displayName'].localeCompare(a['displayName']))
    })
    return [...arr1, ...arr2]
  }
  data.sort((a, b) => {
    if(typeof a[sortKey] ==='number')
    return (sortOrder) * (a[sortKey] - b[sortKey]);
    if(typeof a[sortKey] ==='string')
    return (sortOrder) * (b[sortKey].localeCompare(a[sortKey]));
  })
  if(treeView){
    func.recursiveSort(data, sortKey, sortOrder)
  }
  return data
},
recursiveSort(data, sortKey, sortOrder = 1) {
  data.sort((a, b) => {
      if (typeof a[sortKey] === 'number') {
          return sortOrder * (a[sortKey] - b[sortKey]);
      } else if (typeof a[sortKey] === 'string') {
          return sortOrder * b[sortKey].localeCompare(a[sortKey]);
      }
      return 0;
  });
  data.forEach(item => {
      if (item.children && !item.isTerminal) {
          func.recursiveSort(item.children, sortKey, sortOrder);
      }
  });
},

async copyRequest(type, completeData) {
  let copyString = "";
  let snackBarMessage = ""
  completeData = JSON.parse(completeData);
  if (type=="RESPONSE") {
    let responsePayload = {}
    let responseHeaders = {}
    let statusCode = 0

    if (completeData) {
      responsePayload = completeData["response"] ?  completeData["response"]["body"] : completeData["responsePayload"]
      responseHeaders = completeData["response"] ?  completeData["response"]["headers"] : completeData["responseHeaders"]
      statusCode = completeData["response"] ?  completeData["response"]["statusCode"] : completeData["statusCode"]
    }
    let b = {
      "responsePayload": responsePayload,
      "responseHeaders": responseHeaders,
      "statusCode": statusCode
    }

    copyString = JSON.stringify(b)
    snackBarMessage = "Response data copied to clipboard"
  } else {
    if (type === "CURL") { 
      snackBarMessage = "Curl request copied to clipboard"
      let resp = await inventoryApi.convertSampleDataToCurl(JSON.stringify(completeData))
      copyString = resp.curlString
    } else {
      snackBarMessage = "Burp request copied to clipboard"
      let resp = await inventoryApi.convertSampleDataToBurpRequest(JSON.stringify(completeData))
      copyString = resp.burpRequest
    }
  }
  return {copyString, snackBarMessage};
},
convertPolicyLines: function(policyLines){
  const jsonString = policyLines.join("\n");
  const formattedJson = JSON.stringify(JSON.parse(jsonString), null, 2);
  return formattedJson
},

deepComparison(item1, item2) {
  
  try {
    const areArrays = Array.isArray(item1) && Array.isArray(item2)
    const areObjects = func.isObject(item1) && func.isObject(item2)
    const isReactObject = isValidElement(item1) || isValidElement(item2)

    if (isReactObject) {
      return false;
    }
    else if (areObjects) {
      return func.deepObjectComparison(item1, item2);
    } else if (areArrays) {
      return func.deepArrayComparison(item1, item2);
    } else {
      return item1 === item2;
    }
  } catch (ex) {
    return false;
  }
},

deepArrayComparison(arr1, arr2) {
  if (arr1.length !== arr2.length) {
    return false;
  }

  for (let i = 0; i < arr1.length; i++) {
    const element1 = arr1[i];
    const element2 = arr2[i];

    const areArrays = Array.isArray(element1) && Array.isArray(element2)
    const areObjects = func.isObject(element1) && func.isObject(element2)
    const isReactObject = isValidElement(element1) || isValidElement(element2)

  if(isReactObject){
    return false;
  }
  else if (areArrays) {
      if (!func.deepArrayComparison(element1, element2)) {
        return false;
      }
    } else if (areObjects) {
      if (!func.deepObjectComparison(element1, element2)) {
        return false;
      }
    } else if (element1 !== element2) {
      return false;
    }
  }

  return true;
},

deepObjectComparison(obj1, obj2) {
  const keys1 = Object.keys(obj1);
  const keys2 = Object.keys(obj2);

  if (keys1.length !== keys2.length) {
    return false;
  }

  for (const key of keys1) {
    const val1 = obj1[key];
    const val2 = obj2[key];

    const areArrays = Array.isArray(val1) && Array.isArray(val2);
    const areObjects = func.isObject(val1) && func.isObject(val2);
    const isReactObject = isValidElement(val1) || isValidElement(val2)

  if(isReactObject){
    return false;
  }
  else if (areArrays) {
      if (!func.deepArrayComparison(val1, val2)) {
        return false;
      }
    } else if (areObjects) {
      if (!func.deepObjectComparison(val1, val2)) {
        return false;
      }
    } else if (val1 !== val2) {
      return false;
    }
  }

  return true;
},

isObject(obj) {
  return obj !== null && typeof obj === 'object';
},

toMethodUrlString({method,url, shouldParse =false}){
  if(shouldParse){
    const finalMethod = getMethod(url, method);
    const finalUrl = observeFunc.getTruncatedUrl(url);
    return finalMethod + " " + finalUrl;
  }
  return method + " " + url;
},

toMethodUrlApiCollectionIdString({ method, url, apiCollectionId, shouldParse = false }) {
  if (shouldParse) {
    const finalMethod = getMethod(url, method);
    const finalUrl = observeFunc.getTruncatedUrl(url);
    return finalMethod + " " + finalUrl + " " + apiCollectionId;
  }
  return method + " " + url + " " + apiCollectionId;
},

toMethodUrlObject(str){

  if(!str){
    return {method:"", url:""}  
  }

  return {method:str.split(" ")[0], url:str.split(" ")[1]}
},

toMethodUrlApiCollectionIdObject(str){
  if(!str){
    return {method:"", url:"", apiCollectionId:0}  
  }

  return {method:str.split(" ")[0], url:str.split(" ")[1], apiCollectionId:str.split(" ")[2]}
},
validateMethod(methodName) {
  let m = methodName.toUpperCase()
  let allowedMethods = ["GET", "POST", "PUT", "DELETE", "HEAD", "OPTIONS", "TRACE", "PATCH"]
  let idx = allowedMethods.indexOf(m);
  if (idx === -1) return null
  return allowedMethods[idx]
},
isSubTypeSensitive(x) {
  return x.savedAsSensitive || x.sensitive
},
prepareValuesTooltip(x) {
  let result = "";
  let values = x["values"]
  if (!values) return ""
  let elements = values["elements"] ? values["elements"] : []
  let count = 0;
  for (let elem of elements) {
      if (count > 50) return result
      if (count !== 0) {
          result +=  ", "
      }
      result += elem
      count += 1
  }

  return (count == 0 ? "" : result)
},

parameterizeUrl(x) {
  let re = /INTEGER|STRING|UUID|VERSIONED/gi;
  let newStr = x.replace(re, (match) => { 
      return "{param_" + match + "}";
  });
  return newStr
},
mergeApiInfoAndApiCollection(listEndpoints, apiInfoList, idToName,apiInfoSeverityMap) {
  const allCollections = PersistStore.getState().allCollections
  const apiGroupsMap = func.mapCollectionIdToName(allCollections.filter(x => x.type === "API_GROUP"))

  let ret = {}
  let apiInfoMap = {}

  if (!listEndpoints) {
      return []
  }

  if (apiInfoList) {
      apiInfoList.forEach(x => {
          apiInfoMap[x["id"]["apiCollectionId"] + "-" + x["id"]["url"] + "-" + x["id"]["method"]] = x
      })
  }
  listEndpoints.forEach(x => {
      let key = x.apiCollectionId + "-" + x.url + "-" + x.method
      if (!ret[key]) {
          let access_type = null
          if (apiInfoMap[key]) {
              let access_types = apiInfoMap[key]["apiAccessTypes"]
              if (!access_types || access_types.length == 0) {
                  access_type = null
              } else if (access_types.indexOf("PUBLIC") !== -1) {
                  access_type = "Public"
              } else if (access_types.indexOf("PARTNER") !== -1){
                  access_type = "Partner"
              } else if (access_types.indexOf("THIRD_PARTY") !== -1){
                  access_type = "Third-party"
              }else{
                  access_type = "Private"
              }
          }

          let authType = apiInfoMap[key] ? apiInfoMap[key]["actualAuthType"].join(", ") : ""
          let authTypeTag = authType.replace(",", "");
          let riskScore = apiInfoMap[key] ? apiInfoMap[key]?.riskScore : 0
          let responseCodesArr = apiInfoMap[key] ? apiInfoMap[key]?.responseCodes : [] 
          let discoveredTimestamp = apiInfoMap[key] ? (apiInfoMap[key].discoveredTimestamp | apiInfoMap[key].startTs) : 0
          if(discoveredTimestamp === 0){
            discoveredTimestamp = x.startTs
          }
          let description = apiInfoMap[key] ? apiInfoMap[key]['description'] : ""
          ret[key] = {
              id: x.method + "###" + x.url + "###" + x.apiCollectionId + "###" + Math.random(),
              shadow: x.shadow ? x.shadow : false,
              sensitive: x.sensitive,
              tags: x.tags,
              endpoint: x.url,
              parameterisedEndpoint: x.method + " " + this.parameterizeUrl(x.url),
              open: apiInfoMap[key] ? apiInfoMap[key]["actualAuthType"].indexOf("UNAUTHENTICATED") !== -1 : false,
              access_type: access_type || "No access type",
              method: x.method,
              color: x.sensitive && x.sensitive.size > 0 ? "#f44336" : "#00bfa5",
              apiCollectionId: x.apiCollectionId,
              last_seen: apiInfoMap[key] ? (this.prettifyEpoch(apiInfoMap[key]["lastSeen"])) : this.prettifyEpoch(x.startTs),
              lastSeenTs: apiInfoMap[key] ? apiInfoMap[key]["lastSeen"] : x.startTs,
              detectedTs: discoveredTimestamp === 0 ? x.startTs : discoveredTimestamp,
              changesCount: x.changesCount,
              changes: x.changesCount && x.changesCount > 0 ? (x.changesCount +" new parameter"+(x.changesCount > 1? "s": "")) : 'No new changes',
              added: this.prettifyEpoch(discoveredTimestamp),
              violations: apiInfoMap[key] ? apiInfoMap[key]["violations"] : {},
              apiCollectionName: idToName ? (idToName[x.apiCollectionId] || '-') : '-',
              auth_type: (authType || "no auth type found").toLowerCase(),
              sensitiveTags: [...this.convertSensitiveTags(x.sensitive)],
              authTypeTag: (authTypeTag || "no auth").toLowerCase(),
              collectionIds: apiInfoMap[key] ? apiInfoMap[key]?.collectionIds.filter(x => {
                return Object.keys(apiGroupsMap).includes(x) || Object.keys(apiGroupsMap).includes(x.toString())
              }).map( x => {
                return apiGroupsMap[x]
              }) : [],
              riskScore: riskScore,
              sensitiveInReq: [...this.convertSensitiveTags(x.sensitiveInReq)],
              sensitiveInResp: [...this.convertSensitiveTags(x.sensitiveInResp)],
              responseCodes: responseCodesArr,
              ...(apiInfoSeverityMap?.hasOwnProperty(key) ? { severityObj: apiInfoSeverityMap[key] } : {}),
              sources: apiInfoMap[key]?apiInfoMap[key]['sources']:{},
              description: description,
              descriptionComp: (<Box maxWidth="300px"><TooltipText tooltip={description} text={description}/></Box>),
              lastTested: apiInfoMap[key] ? apiInfoMap[key]["lastTested"] : 0,
          }

      }
  })
  return Object.values(ret) 
},
getSeverityCountPerEndpointList(apiInfoSeverityMap){
  if(!apiInfoSeverityMap) return {}
  let apiInfoIdSeverityMap = {}
  Object.entries(apiInfoSeverityMap).forEach(([key, value]) => {
    let keyId = key.split(" ").join("-");
    apiInfoIdSeverityMap[keyId] = value;

  });
  return apiInfoIdSeverityMap;
},

convertSensitiveTags(subTypeList) {
  let result = new Set()
  if (!subTypeList || subTypeList.size === 0) return result

  subTypeList.forEach((x) => {
      result.add(x.name)
  })

  return result
},
dayStart(epochMs) {
  let date = new Date(epochMs)
  date.setHours(0)
  date.setMinutes(0)
  date.setSeconds(0)
  return date
},
convertToRelativePath(url) {
  if (!url) return url
  if (!url.startsWith("http")) return url
  try {
      var url = new URL(url)
      return url.pathname
  }catch(e) {
      console.log(e);
  }
  return url
},
actionItemColors() {
  return {
      Total: 'rgba(71, 70, 106)',
      Pending: 'rgba(246, 190, 79)',
      Overdue: 'rgba(243, 107, 107)',
      'This week': 'rgba(33, 150, 243)',
      Completed: 'rgba(0, 191, 165)'
  };
},
recencyPeriod: 60 * 24 * 60 * 60,
getDeprecatedEndpoints(apiInfoList, unusedEndpoints, apiCollectionId) {
  let ret = []
  apiInfoList.forEach(apiInfo => {
      if (apiInfo.lastSeen < (func.timeNow() - func.recencyPeriod)) {
          ret.push({
              id: apiInfo.id.method+ " " + apiInfo.id.url + Math.random(),
              endpoint: apiInfo.id.url, 
              method: apiInfo.id.method,
              apiCollectionId: apiInfo.id.apiCollectionId ? apiInfo.id.apiCollectionId : apiCollectionId,
              last_seen: func.prettifyEpoch(apiInfo.lastSeen),
              parameterisedEndpoint: apiInfo.id.method + " " + this.parameterizeUrl(apiInfo.id.url)
          })
      }
  })

  try {
      unusedEndpoints.forEach((x) => {
          if (!x) return;
          let arr = x.split(" ");
          if (arr.length < 2) return;
          ret.push({
          id: arr[1] + " " + arr[0] + Math.random(),
          endpoint : arr[0],
          method : arr[1],
          apiCollectionId: apiCollectionId,
          last_seen: 'in API spec file',
          parameterisedEndpoint: arr[1] + " " + this.parameterizeUrl(arr[0])
          })
      })
  } catch (e) {
  }
  return ret
}, 
 getCollectionName(collectionId) {
    const collection = Store.getState().allCollections.find(x => x.id === collectionId)
    if (collection) 
      return collection.displayName
    else 
      return ""
 },
  getOption: (selectOptions, type) => {
    const option = selectOptions.filter((item) => {
      return item.value == type
    })[0]
    return option;
  },
  getConditions: (selectOptions, type) => {
    const option = func.getOption(selectOptions, type)
    if (option.operators) {
      return option.operators
    }
    return [{
      label: 'OR',
      value: 'OR',
    },
    {
      label: 'AND',
      value: 'AND'
    }];
  },
  conditionStateReducer(draft, action) {
    try{
        switch (action.type) {
            case "add": {
                if (action.key) {
                    draft[action.key].push(action.condition);
                }
                break;
            }
            case "update": {
                if (action.key) {
                    Object.assign(draft[action.key][action.index], action.obj);
                } else {
                    Object.assign(draft, action.obj);
                }
                break;
            }
            case "delete": {
                if(Array.isArray(current(draft[action.key]))){
                    draft[action.key] = draft[action.key].filter((i, index) => index !== action.index);
                } else {
                    delete draft[action.key];
                }
                break;
            }
            default: break;
        }
    } catch {
        return draft;
    }
},
  conditionsReducer(draft, action) {
    switch (action.type) {
        case "add": draft.push(action.obj); break;
        case "overwrite": draft[action.index][action.key] = { };
        case "update": draft[action.index][action.key] = { ...draft[action.index][action.key], ...action.obj }; break;
        case "updateKey": draft[action.index] = { ...draft[action.index], [action.key]: action.obj }; break;
        case "delete": return draft.filter((item, index) => index !== action.index);
        case "clear": return [];
        default: break;
    }
  },
  toYMD(date) {
    var d = date.getDate();
    var m = date.getMonth() + 1; //Month from 0 to 11
    var y = date.getFullYear();
    return y * 10000 + m * 100 + d
  },
  toDate(yyyymmdd) {
    return +new Date(yyyymmdd / 10000, (yyyymmdd / 100) % 100 - 1, yyyymmdd % 100)
  },
  incrDays(date, days) {
    var ret = new Date(date.getFullYear(), date.getMonth(), date.getDate())
    ret.setDate(ret.getDate() + days)
    return ret
  },
  prepareDomain(x) {
    let NO_VALUES_RECORDED = "-";
    if (x.domain === "RANGE") {
      return x.minValue + " - " + x.maxValue
    } else if (x.domain === "ANY") {
      return "ANY"
    } else {
      let values = x["values"]
      if (!values) return NO_VALUES_RECORDED

      let elements = values["elements"]
      if (!elements) return NO_VALUES_RECORDED

      let size = elements.length
      if (size === 0) {
        return NO_VALUES_RECORDED
      }
      let count = 0
      let result = ""
      const limit = 2
      for (var elem of elements) {
        if (count >= limit) {
          result += " and " + (size - limit) + " more"
          return result
        }

        if (count !== 0) {
          result += ", "
        }

        result += elem
        count += 1

      }
      return result;
    }
  },

 getResponse(resp, queryType){
    const responses = resp?.responses
    if(!responses || responses.length === 0){
      return{
        message: " Sorry couldn't find any response with your prompt.Please try again."
      }
    }

    else{
      if(queryType === "generate_curl_for_test"){
        if(responses[0] && responses[0].curl && responses[0].curl.includes('-H')){
          return{
            message: responses[0].curl.trim()
          }
        }else{
          const message = responses[0].error ? responses[0].error : `It seems that this API is not vulnerable to ${responses[0].test_type.toUpperCase()} vulnerability.`
          return{
            message: message
          }
        }
      }

      else if(queryType === "suggest_tests"){
        if(responses[0].tests){
          let arr = []
          responses.tests.forEach((item) => {
            if(responses.test_details[item]){
              arr.push(responses.test_details[item])
            }
          })

          if(arr.length === 0){
            return {
              message: "No tests available for the api."
            }
          }else{
            let tests = [{functionality: "This api is vulnerable to the following vulnerabilities", apis: arr}]
            return{
              responses: tests
            }
          }
        }else{
          return{
            message: responses[0].error
          }
        }
      }

      else if(queryType === "generate_regex"){
        if(responses[0] && responses[0].regex){
          return{
            message: responses[0].regex
          }
        }else{
          return{
            message: responses[0].error
          }
        }
      }

      else{
        return {
          responses: responses
        }
      }

    }
 },

 dateRangeReducer(draft, action){

  try {
    switch(action.type){
      case "update": {
         if(action.period){
          Object.assign(draft, action.period);
        }
        break;
      }

      default:
        break;
    }

  } catch {
    return draft
  }
 },

 getDateValue(dateRange){
  const dateStr = dateRange.title === "Custom"
          ? dateRange.period.since.toDateString() + " - " + dateRange.period.until.toDateString()
          : dateRange.title;
  return dateStr
 },
 createSearchResult(section, content, url) {
  return {
    content: content,
    url: url, 
    type: section.type,
    icon: section.icon,
    variant: 'menu',
    truncate: true,
  }
 },
 getCollectionsSearchItems(allCollections) {
  const searchItems = []

  const activatedColections = allCollections.filter((collection) => collection.deactivated === false)
  activatedColections.forEach((collection)=> {
    const collectionUrl = searchResultSections.collections.sectionPath + collection.id
    const searchResult = this.createSearchResult(
      searchResultSections.collections,
      collection.displayName, 
      collectionUrl
    )
    searchItems.push(searchResult)
  })
  return searchItems
 },
 getTestSearchItems(allTests) {
  const searchItems = []

  // filter active tests
  const activeTests = Object.values(allTests).filter((test) => test.inactive === false)

  activeTests.forEach(test => {
    const testUrl = searchResultSections.tests.sectionPath + test.name
    const searchResult = this.createSearchResult(
      searchResultSections.tests,
      test.testName, 
      testUrl,
    )
    searchItems.push(searchResult)
  });

  return searchItems
 },
 getConnectorSearchItems() {
  const searchItems = []

  const connectorCategories = quickStartFunc.getConnectorsListCategorized()

  for (const categoryArr of Object.values(connectorCategories)) {
      for (const connector of categoryArr) {
        const connectorKey = connector.key?.toLowerCase() ?? "";
        const connectorUrl = `${searchResultSections.connectors.sectionPath}?connector=${connectorKey}`
        const searchResult = this.createSearchResult(
          searchResultSections.connectors,
          connector.label, 
          connectorUrl,
        )
      searchItems.push(searchResult)
      }
  }

  return searchItems
 },
 getSearchItemsArr(allCollections, subCategoryMap){
  let combinedArr = []

  const collectionsSearchItems = this.getCollectionsSearchItems(allCollections)
  const testSearchItems = this.getTestSearchItems(subCategoryMap)
  const connectorSearchItems = this.getConnectorSearchItems()
  combinedArr.push(...collectionsSearchItems, ...testSearchItems, ...connectorSearchItems)

  return combinedArr
 },
 createSearchResultsSection(section, filteredItemsArr, handleNavigateSearch) {
    const SECTION_ITEMS_MAX_COUNT = 10
    const filteredSectionItems = filteredItemsArr.filter(sectionItem => sectionItem.type === section.type)
    const filteredSectionResults = filteredSectionItems
      .slice(0, SECTION_ITEMS_MAX_COUNT)
      .map(({ url, ...sectionItem }) => ({ ...sectionItem, onAction: () => handleNavigateSearch(url) }));
    
    if (filteredSectionItems.length > SECTION_ITEMS_MAX_COUNT) {
      const { url, ...moreResult } = this.createSearchResult(
        searchResultSections.collections,
        `+${filteredSectionItems.length - SECTION_ITEMS_MAX_COUNT} more`,
        section.sectionPath
      );

      moreResult.onAction = () => handleNavigateSearch(section.sectionPath);
      moreResult.icon = ''
      filteredSectionResults.push(moreResult)
    }

    if (filteredSectionResults.length === 0) return null
    else {
      return { title: section.title, items: filteredSectionResults }
    }
},
 getSearchResults(filteredItemsArr, handleNavigateSearch) {
  const collectionsSection = this.createSearchResultsSection(searchResultSections.collections, filteredItemsArr, handleNavigateSearch)
  const testsSection = this.createSearchResultsSection(searchResultSections.tests, filteredItemsArr, handleNavigateSearch)
  const connectorsSection = this.createSearchResultsSection(searchResultSections.connectors, filteredItemsArr, handleNavigateSearch)

  const allSections = [ collectionsSection, testsSection, connectorsSection ].filter(section => section !== null)

  return allSections
 },

updateQueryParams(searchParams, setSearchParams, key, value) {
  const newSearchParams = new URLSearchParams(searchParams);
  if (value === "") {
      newSearchParams.delete(key)
  } else {
      newSearchParams.set(key, value);
  }
  setSearchParams(newSearchParams);
},
 getComplianceIcon: (complianceName) => {
  return "/public/"+complianceName.toUpperCase()+".svg";
},

 convertToDisambiguateLabel(value, convertFunc, maxAllowed){
  if (value.length > maxAllowed) {
      return `${value.slice(0, maxAllowed).map(val => convertFunc(val)).join(", ")} +${value.length - maxAllowed} more`;
  } else {
      return value.map(val => convertFunc(val)).join(", ");
  }   
},

convertToDisambiguateLabelObj(value, convertObj, maxAllowed){
  if(!value || value.length  === 0 || !Array.isArray(value)){
    return ""
  }
  if (value.length > maxAllowed) {
      return `${value.slice(0, maxAllowed)
                     .map(val => convertObj ? convertObj[val] : val)
                     .join(", ")} +${value.length - maxAllowed} more`;
  } else {
      return value.map(val => convertObj ? convertObj[val] : val).join(", ");
  }   
},
getSizeOfFile(bytes) {
  if (bytes >= 1024 * 1024) {
    return (bytes / (1024 * 1024)).toFixed(2) + ' MB';
  } else if (bytes >= 1024) {
    return (bytes / 1024).toFixed(2) + ' KB';
  } else {
    return bytes + ' B';
  }
},
mapCollectionIdToHostName(apiCollections){
    let collectionsObj = {}
    apiCollections.forEach((collection)=>{
      if(!collectionsObj[collection.id]){
        collectionsObj[collection.id] = collection.hostName
      }
    })

    return collectionsObj
},
joinWordsWithUnderscores(input) {
    if (!input ) return "";
    const words = input.trim().split(/\s+/);
    const result = words.map(word => word).join('_');

    return result.toUpperCase();
  },
  getTimeTakenByTest(startTimestamp, endTimestamp){
    const timeDiff = Math.abs(endTimestamp - startTimestamp);
    const hours = Math.floor(timeDiff / 3600);
    const minutes = Math.floor((timeDiff % 3600) / 60);
    const seconds = timeDiff % 60;

    let duration = '';
    if (hours > 0) {
        duration += hours + ` hour${hours==1 ? '' : 's'} `;
    }
    if (minutes > 0) {
        duration += minutes + ` minute${minutes==1 ? '' : 's'} `;
    }
    if (seconds > 0 || (hours === 0 && minutes === 0)) {
        duration += seconds + ` second${seconds==1 ? '' : 's'}`;
    }
    return duration.trim();
  },
  getHexColorForSeverity(key){
    switch(key){
      case "CRITICAL":
        return "#DF2909"
      case "HIGH":
        return "#FED3D1"
      case "MEDIUM":
        return "#FFD79D"
      case "LOW":
        return "#E4E5E7"
      default:
        return "#E4E5E7"
    }

  },

  getColorForCharts(key){
    switch(key){
      case "HIGH":
        return tokens.color["color-icon-critical"]
      case "MEDIUM":
        return tokens.color["color-icon-warning"]
      case "LOW":
        return tokens.color["color-icon-info"]
      case "BOLA":
        return "#800000"
      case "NO_AUTH":
        return "#808000"
      case "BFLA":
        return "#D9534F"
      case "IAM":
        return "#5BC0DE"
      case "EDE":
        return "#FF69B4"
      case "RL":
        return "#8B4513"
      case "MA":
        return "#E6E6FA"
      case "INJ":
        return "#008080"
      case "ILM":
        return "#26466D"
      case "SM":
        return "#CCCCCC"
      case "SSRF":
        return "#555555"
      case "UC":
        return "#AF7AC5"
      case "UHM":
        return "#337AB7"
      case "VEM":
        return "#5CB85C"
      case "MHH":
        return "#FFC107"
      case "SVD":
        return "#FFA500"
      case "CORS":
        return "#FFD700"
      case "COMMAND_INJECTION":
        return "#556B2F"
      case "CRLF":
        return "#708090"
      case "SSTI":
        return "#008B8B"
      case "LFI":
        return "#483D8B"
      case "XSS":
        return "#8B008B"

      default:
        return  "#" + Math.floor(Math.random()*16777215).toString(16);
    }
  },

  getSensitiveIcons(data) {
    const key = data.toUpperCase().replace(/ /g, '_');
    switch (key) {
        case "DATABASE":
          return DynamicSourceMinor;
        case "SECRET":
          return LockMinor;
        case "TOKEN":
          return KeyMajor;
        case "USERNAME":
          return ProfileMinor;
        case "PASSWORD":
          return PasskeyMinor;
        case "JWT":
          return KeyMajor;
        case "EMAIL":
          return EmailMajor;
        case "CREDIT_CARD":
          return CreditCardMajor;
        case "SSN":
          return IdentityCardMajor;
        case "ADDRESS":
          return LocationsMinor;
        case "IP_ADDRESS":
          return LocationsMinor;
        case "PHONE_NUMBER":
          return PhoneMajor;
        case "UUID":
          return IdentityCardMajor;
        case "DATA_FILE":
          return FileMinor;
        case "IMAGE":
          return ImageMajor;
        case "US_ADDRESS":
          return LocationsMinor;
        case "IBAN_EUROPE":
          return BankMajor;
        case "JAPANESE_SOCIAL_INSURANCE_NUMBER":
          return HashtagMinor;
        case "GERMAN_INSURANCE_IDENTITY_NUMBER":
          return IdentityCardMajor;
        case "CANADIAN_SOCIAL_IDENTITY_NUMBER":
          return IdentityCardMajor;
        case "FINNISH_PERSONAL_IDENTITY_NUMBER":
          return IdentityCardMajor;
        case "UK_NATIONAL_INSURANCE_NUMBER":
          return HashtagMinor;
        case "INDIAN_UNIQUE_HEALTH_IDENTIFICATION":
          return IdentityCardMajor;
        case "US_MEDICARE_HEALTH_INSURANCE_CLAIM_NUMBER":
          return HashtagMinor;
        case "PAN_CARD":
          return IdentityCardMajor;
        case "ENCRYPT":
          return LockMinor;
        case "SESSIONID":
          return KeyMajor;
        case "INVOICE":
          return ReceiptMajor;
        case "EIN":
          return IdentityCardMajor;
        case "PIN":
          return LocationsMinor;
        case "BANK":
          return BankMajor;
        case "PASSPORT":
          return IdentityCardMajor;
        case "LICENSE":
          return IdentityCardMajor;
        case "STREETLINE":
          return LocationsMinor;
        case "ADDRESSKEY":
          return LocationsMinor;
        case "CONTACT":
          return MobileMajor;
        case "AUTH":
          return LocationsMinor;
        case "DOB":
          return CalendarMinor;
        case "BIRTH":
          return CalendarTimeMinor;
        case "VIN":
          return car_icon;
        default:
          return KeyMajor;
    }
  },
  getCollectionFilters(filters) {
    const allCollections = PersistStore.getState().allCollections
    filters.forEach((x, i) => {
      let tmp = []
      switch (x.key) {
        case "collectionIds":
          filters[i].choices = []
          tmp = allCollections
            .filter(x => x.type === 'API_GROUP')
          break;
        case "apiCollectionId":
          filters[i].choices = []
          tmp = allCollections
            .filter(x => x.type !== 'API_GROUP')
          break;
        default:
          break;
      }
      tmp.forEach(x => {
        filters[i].choices.push({
          label: x.displayName,
          value: Number(x.id)
        })
      })
    })
    return filters;
  },
  handleKeyPress (event, funcToCall) {
    const enterKeyPressed = event.keyCode === 13;
    if (enterKeyPressed) {
      event.preventDefault();
      funcToCall();
    }
  },
  async refreshApiCollections() {
    let apiCollections = await homeFunctions.getAllCollections()
    const allCollectionsMap = func.mapCollectionIdToName(apiCollections)

    PersistStore.getState().setAllCollections(apiCollections);
    PersistStore.getState().setCollectionsMap(allCollectionsMap);
  },

  convertParamToDotNotation(str) {
    return str.replace(/[#\$]+/g, '.');;
  },

  findLastParamField(str) {
    let paramDot = func.convertParamToDotNotation(str)
    let parmArr = paramDot.split(".")
    let lastIndex = parmArr.length-1
    let result = parmArr.length > 0 ? parmArr[lastIndex] : paramDot
    if (result.length > 0) return result;
    return parmArr[lastIndex-1]
  },

  addPlurality(count){
    if(count == null || count==undefined){
      return ""
    }
    return count === 1 ? "" : "s" 
  },
  convertQueryParamsToUrl(queryParams) {
    if(!queryParams){
      return "";
    }
    let url = ""
    let first = true;
    let joiner = "?"
    Object.keys(queryParams).forEach((key) => {
      if (!first) {
        joiner = "&"
      }
      url = url + joiner + key + '=' + encodeURI(queryParams[key])
      first = false
    })
    return url;
  },
  transformString(inputString) {
    let transformedString = inputString.replace(/^\//, '').replace(/\/$/, '').replace(/#$/, '');
    const segments = transformedString.split('/');
    for (let i = 0; i < segments.length; i++) {
        // Check if the segment is alphanumeric
        if (/^[0-9a-fA-F]+$/.test(segments[i]) || /^[0-9]+$/.test(segments[i])) {
        segments[i] = 'id';
        }
    }
    transformedString = segments.join('/');
    transformedString = transformedString.replace(/[/|-]/g, '_');
    return transformedString;
},
showConfirmationModal(modalContent, primaryActionContent, primaryAction) {
  Store.getState().setConfirmationModalConfig({
    modalContent: modalContent,
    primaryActionContent: primaryActionContent,
    primaryAction: primaryAction,
    show: true
  })
},
  hashCode(str) {
    var hash = 0;
    for (var i = 0; i < str.length; i++) {
        var character = str.charCodeAt(i);
        hash = ((hash<<5)-hash)+character;
        hash = hash & hash; // Convert to 32bit integer
    }
    return hash;
  },
  getTableTabsContent(tableTabs, countObj, setSelectedTab, selectedTab, currentCount){
    const finalTabs = tableTabs.map((tab,ind) => {
      const tabId = this.getKeyFromName(tab)
      return {
          content: tab,
          badge: selectedTab === (tabId && currentCount !==0) ? currentCount.toString() : countObj[tabId].toString(),
          onAction: () => { setSelectedTab(tabId) },
          id: this.getKeyFromName(tabId),
          index: ind 
      }
    })
    return finalTabs
  },
  getTabsCount(tableTabs, data, initialCountArr = []){
    const currentState = PersistStore(state => state.tableInitialState)
    const baseUrl = window.location.pathname + "/#"
    let finalCountObj = {}
    tableTabs.forEach((tab,ind) => {
      const tabId = this.getKeyFromName(tab)
      const tabKey = baseUrl + tabId
      // Check _counts first (for accurate counts with memory optimization), then fall back to array length
      const count = currentState[tabKey] || data._counts?.[tabId] || data[tabId]?.length || initialCountArr[ind] || 0
      finalCountObj[tabId] = count
    })

    return finalCountObj
  },
  getKeyFromName(key){
    return key.replace(/[\s/]+/g, '_').toLowerCase();
  },
  showTestSampleData(selectedTestRunResult){
    if(selectedTestRunResult?.vulnerable === true){
      return true;
    }
    let skipList = [
      "skipping execution",
      "deactivated"
    ]

    let errors = selectedTestRunResult.errors;

    if (errors && errors.length > 0) {
      let errorInSkipList = errors.filter(x => {
        return skipList.some(y => x.includes(y))
      }).length > 0

      return !errorInSkipList
    }
    return true;
  },
  checkLocal() {
    if (window.DASHBOARD_MODE !== 'ON_PREM' && window.IS_SAAS !== "true") {
      return true;
    }
    return false;
  },
  checkOnPrem() {
    if (window.DASHBOARD_MODE === "ON_PREM") {
      return true;
    }
    return false;
  },
  checkForRbacFeatureBasic(){
    const stiggFeatures = window.STIGG_FEATURE_WISE_ALLOWED
    let rbacAccess = false;
    if (!stiggFeatures || Object.keys(stiggFeatures).length === 0) {
        rbacAccess = true
    } else if(stiggFeatures && stiggFeatures['RBAC_BASIC']){
        rbacAccess = stiggFeatures['RBAC_BASIC'].isGranted
    }
    return rbacAccess;
  },
  checkForRbacFeature(){
    const stiggFeatures = window.STIGG_FEATURE_WISE_ALLOWED
    let rbacAccess = false;
    if (!stiggFeatures || Object.keys(stiggFeatures).length === 0) {
        rbacAccess = true
    } else if(stiggFeatures && stiggFeatures['RBAC_FEATURE']){
        rbacAccess = stiggFeatures['RBAC_FEATURE'].isGranted
    }
    return rbacAccess;
  },

  checkForFeatureSaas(featureLabel) {
    const stiggFeatures = window.STIGG_FEATURE_WISE_ALLOWED
    let access = false;
    if (!stiggFeatures || Object.keys(stiggFeatures).length === 0 ) {
      access = this.checkOnPrem()
    } else if (stiggFeatures && stiggFeatures[featureLabel]) {
      access = stiggFeatures[featureLabel].isGranted
    }
    return access;
  },

  checkUserValidForIntegrations(){
    const rbacAccess = this.checkForRbacFeatureBasic();
    if(!rbacAccess){
      return true;
    }
    const userRole = window.USER_ROLE
    return !(userRole === "GUEST" || userRole === "DEVELOPER")
  },
  capitalizeFirstLetter(str) {
    if (!str) return str;
    return str.charAt(0).toUpperCase() + str.slice(1);
  },
  getDaySuffix(day) {
    if (day > 3 && day < 21) return 'th';
    switch (day % 10) {
        case 1: return 'st';
        case 2: return 'nd';
        case 3: return 'rd';
        default: return 'th';
    }
  },
  formatReportDate(date) {
    const day = date.getDate();
    const month = date.toLocaleString('default', { month: 'long' });
    const year = date.getFullYear();
    const daySuffix = func.getDaySuffix(day);
    return `${day}${daySuffix} ${month}, ${year}`;
  },
  sleep(ms) {
    return new Promise(resolve => setTimeout(resolve, ms));
  },
  getAktoSeverities(){
    return ['CRITICAL', 'HIGH', 'MEDIUM', 'LOW']
  },
  getAktoSeveritiesInitMap(initArr = false) {
    const aktoSeverities = this.getAktoSeverities()
    const severitiesMap = aktoSeverities.reduce((acc, curr) => {
      acc[curr] = initArr ? [] : 0
      return acc
    }, {})
    return severitiesMap
  },
  getSelectedItemsText(selectedItem) {
    if (!Array.isArray(selectedItem) || selectedItem?.length === 0) return "";
  
    if (selectedItem.length === 1) return selectedItem[0];
  
    const allButLast = selectedItem.slice(0, -1).join(", ");
    const last = selectedItem[selectedItem.length - 1];
    return `${allButLast} & ${last}`;
  },  

  getIconFromString(iconString){
    if(iconsUsedMap[iconString] !== undefined){
      return iconsUsedMap[iconString]
    } return null
  },
  formatEndpoint(endpoint) {
    const trimmedEndpoint = endpoint.replace(/^api\//, '')
    const spacedEndpoint = trimmedEndpoint.replace(/([a-z])([A-Z])/g, '$1 $2')
    const finalEndpoint = spacedEndpoint.replace(/[_-]/g, ' ')
    const capitalizedEndpoint = finalEndpoint
        .split(' ')
        .map(word => word.charAt(0).toUpperCase() + word.slice(1))
        .join(' ')

    return capitalizedEndpoint
  },
  validatePassword(password, confirmPassword) {
    if (password.length < 8) {
        func.setToast(true, true, "Minimum of 8 characters required")
        return false
    }

    if (password.length >= 40) {
        func.setToast(true, true, "Maximum of 40 characters allowed")
        return false
    }

    if (password !== confirmPassword) {
        func.setToast(true, true, "Passwords do not match")
        return false
    }

    let numbersFlag = false
    let lettersFlag = false

    const allowedSpecialChars = new Set([
        "+", "@", "*", "#", "$", "%", "&", "/", "(", ")", "=", "?", "^", "!",
        "[", "]", "{", "}", "-", "_", ":", ";", ">", "<", "|", ",", "."
    ])

    for (let i = 0; i < password.length; i++) {
        const ch = password.charAt(i)
        const upperCaseCh = ch.toUpperCase()

        if (ch >= '0' && ch <= '9') {
            numbersFlag = true
        } else if (upperCaseCh >= 'A' && upperCaseCh <= 'Z') {
            lettersFlag = true
        } else if (!allowedSpecialChars.has(ch)) {
            func.setToast(true, true,  "Invalid character")
            return false
        }
    }

    if (!numbersFlag || !lettersFlag) {
        func.setToast(true, true, "Must contain letters and numbers")
        return false
    }

    return true
  },

  trimContentFromSubCategory(subcategory) {
      subcategory["content"] = ""
  },

  getTableTabIndexById(defaultTabIndex, definedTableTabs, selectedTabId) {
    let initialIdx = defaultTabIndex;
    for(let x = 0; x < definedTableTabs.length; x++) {
        const tempId = func.getKeyFromName(definedTableTabs[x]);
        if (tempId === selectedTabId) {
            initialIdx = x;
            break;
        }
    }
    return initialIdx
  },

  getEpochMillis(value, type) {
    const [year, val] = value.split('_').map(Number);
    let date;
    
    switch (type) {
        case 'dayOfYear':
            date = new Date(Date.UTC(year, 0, val)); // January 1st + (val - 1) days
            break;
        case 'monthOfYear':
            date = new Date(Date.UTC(year, val - 1, 1)); // Month is 0-based
            break;
        case 'weekOfYear':
            date = new Date(Date.UTC(year, 0, 1)); // Start of the year
            const firstDay = date.getUTCDay(); // Get the first day of the year
            const offset = firstDay === 0 ? 0 : 7 - firstDay; // Move to first Monday
            date.setUTCDate(date.getUTCDate() + offset + (val - 1) * 7); // Add weeks
            break;
        default:
            throw new Error("Invalid type. Must be 'day', 'month', or 'week'.");
    }
    
    return date.getTime();
},

  prettifyFutureEpoch(epoch, testsPage=false) {
      if (!epoch) return "Never";
      
      const now = Math.floor(Date.now() / 1000);
      const diffSeconds = epoch - now;
      
      if (diffSeconds < 0){
        if(diffSeconds < -86400 || testsPage){
          return this.prettifyEpoch(epoch);
        } else {
          return "now";
        }
      }
      
      const date = new Date(epoch * 1000);
      const tomorrow = new Date();
      tomorrow.setDate(tomorrow.getDate() + 1);
      
      // Format time
      const timeStr = date.toLocaleTimeString('en-US', { 
        hour: 'numeric', 
        minute: '2-digit',
        hour12: true 
      });
      
      // If same day
      if (date.toDateString() === new Date().toDateString()) {
        return `today ${timeStr}`;
      }
      
      // If tomorrow
      if (date.toDateString() === tomorrow.toDateString()) {
        return `tomorrow ${timeStr}`;
      }
      
      // If within 7 days
      const daysDiff = Math.floor(diffSeconds / (24 * 60 * 60));
      if (daysDiff < 7) {
        return `${date.toLocaleDateString('en-US', { weekday: 'long' })} ${timeStr}`;
      }
      
      // Otherwise show full date and time
      return `${date.toLocaleDateString('en-US', { 
        month: 'short',
        day: 'numeric',
        year: 'numeric'
      })} ${timeStr}`;
  },
  isDemoAccount(){
     return window.ACTIVE_ACCOUNT === 1669322524
  },
  isSameDateAsToday (givenDate) {
      const today = new Date();
      return (
          givenDate.getUTCFullYear() === today.getUTCFullYear() &&
          givenDate.getUTCMonth() === today.getUTCMonth() &&
          givenDate.getDate() === today.getDate()
      );
  },
  getStartOfTodayEpoch() {
    const now = new Date();
    return Math.floor(this.getStartOfTodayDate().getTime() / 1000);
  },
  getStartOfTodayDate(){
    const now = new Date();
    return new Date(now.getFullYear(), now.getMonth(), now.getDate());
  },
  getStartOfDay(now) {
    try {
      return new Date(now.getFullYear(), now.getMonth(), now.getDate());
    } catch(e){
      return this.getStartOfTodayDate();
    }
  },
  getStartOfDayEpoch(now) {
    return Math.floor(this.getStartOfDay(now).getTime() / 1000);
  },
  getDayOfWeek(time){
    const temp = new Date(time * 1000);
    switch(temp.getDay()){
      case 1:
        return "Monday"
      case 2:
        return "Tuesday"
      case 3:
        return "Wednesday"
      case 4: 
        return "Thursday"
      case 5:
        return "Friday"
      case 6: 
        return "Saturday"
      default:
        return "Sunday"
    }
  },
  getHourFromEpoch(time) {
    try {
      let date = new Date(time * 1000);
      let hours = date.getHours();
      return hours;
    } catch (e) {
      return 0;
    }
  },
  getFormattedHoursUsingLabel(hour, labels, defaultLabel) {
    let hourLabel = hour === 12 ? "noon" : (
      hour === 0 ? "midnight" : (
        hour < 13 ? "am" : "pm"
      )
    )
    let hourValue = hour == 0 ? 24 : hour

    let filtered = labels.filter((x) => {
      return x.value == hourValue && x.label.includes(hourLabel)
    })

    if (filtered.length == 1) {
      return filtered[0]
    }
    return defaultLabel
  },
  formatCollectionType(type) {
    return (type?.keyName?.replace(/^(userSetEnvType|envType)/, 'env')?.slice(0, 30) ?? '') + '=' + (type?.value?.slice(0, 30) ?? '')
  },
  getRecurringContext(periodInSeconds) {
    if (periodInSeconds === 86400) return "Daily"
    else if (periodInSeconds === (86400 * 30)) return "Monthly"
    else if (periodInSeconds === (86400 * 7)) return "Weekly"
    else if (periodInSeconds === -1) return "Continuously"
    else return "Once"
  },
  getRunTypeLabel(runType, periodInSeconds) {
    if (!runType || runType === "CI_CD" || runType === "ONE_TIME") return "Once";
    else if (runType === "RECURRING") return this.getRecurringContext(periodInSeconds)
    else if (runType === "CONTINUOUS_TESTING") return "Continuously";
  },
  getPriorityStatus (priority) {
    switch (priority) {
      case 'P0':
        return 'critical-strong-experimental';
      case 'P1':
        return 'critical';
      case 'P2':
        return 'attention';
      case 'P3':
        return 'warning';
      case 'P4':
        return 'info';
      case 'P5':
        return 'success';
      default:
        return 'new';
    }
  },
  // Category priority helpers: mcp > agentic > llm > others
  getCategoryPriority(name){
    const lower = (name || '').toLowerCase();
    if (lower.includes('mcp')) return 0;
    if (lower.includes('agentic')) return 1;
    if (lower.includes('llm')) return 2;
    return 3;
  },
  // Generic sorter: accepts array of strings or objects. If objects, pass a key string or a getter fn.
  sortByCategoryPriority(items, keyOrGetter){
    if(!Array.isArray(items)) return items;
    const getName = typeof keyOrGetter === 'function'
      ? keyOrGetter
      : (item) => {
          if (typeof item === 'string') return item;
          const key = keyOrGetter || 'name';
          return item?.[key];
        };
    return [...items].sort((a,b)=>{
      const nameA = getName(a);
      const nameB = getName(b);
      const pA = this.getCategoryPriority(nameA);
      const pB = this.getCategoryPriority(nameB);
      if(pA !== pB) return pA - pB;
      return (nameA || '').localeCompare(nameB || '');
    })
  },
  isLimitedAccount(){
    return window?.ACTIVE_ACCOUNT === 1753372418
  }
}

export default func
