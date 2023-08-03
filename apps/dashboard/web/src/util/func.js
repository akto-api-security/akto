import {
  CircleCancelMinor,
  CalendarMinor,
  ClockMinor
} from '@shopify/polaris-icons';
import { saveAs } from 'file-saver'
import inventoryApi from "../apps/dashboard/pages/observe/api"
import { isValidElement } from 'react';
import Store from '../apps/dashboard/store';

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
  validateName(name) {
    const regex = /^[a-z0-9_]+$/i;
    if (name.length == 0) {
       return "Name cannot be blank";
    } else if (!name.match(regex)) {
      return "Only alphanumeric and underscore characters allowed in name" ;
    }
    return true;
  },
  toDateStr(date, needYear) {
    let strArray = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];
    let d = date.getDate();
    let m = strArray[date.getMonth()];
    let y = date.getFullYear();
    return m + ' ' + d + (needYear ? ' ' + y : '');
  },
  prettifyEpoch(epoch) {
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
    return Object.keys(obj).reduce((acc, k) => {
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
  getSeverityStatus(countIssues) {
    if(countIssues==null){
      return [];
    }
    return Object.keys(countIssues).filter((key) => {
      return (countIssues[key] > 0)
    })
  },
  getTestingRunIcon(state) {
    switch (state) {
      case "RUNNING": return ClockMinor;
      case "SCHEDULED": return CalendarMinor;
      case "STOPPED": return CircleCancelMinor;
      default: return ClockMinor;
    }
  },
  getSeverity(countIssues) {
    if (countIssues == null) {
      return []
    }
    return Object.keys(countIssues).filter((key) => {
      return (countIssues[key] > 0)
    }).map((key) => {
      return countIssues[key] + " " + key
    })
  },
  getTestResultStatus(item) {
    let localItem = item.toUpperCase();
    if(localItem.includes("HIGH")) return 'critical';
    if(localItem.includes("MEDIUM")) return 'warning';
    if(localItem.includes("LOW")) return 'neutral';
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
    let testSubType = subCategoryMap[runResult.testSubType]
    if (!testSubType) {
      return "HIGH"
    } else {
      let a = testSubType.superCategory["severity"]["_name"]
      return a
    }
  }
  ,
  copyToClipboard(text) {
    if (!navigator.clipboard) {
      // Fallback for older browsers (e.g., Internet Explorer)
      const textarea = document.createElement('textarea');
      textarea.value = text;
      textarea.style.position = 'fixed';
      textarea.style.opacity = 0;
      document.body.appendChild(textarea);
      textarea.select();
      document.execCommand('copy');
      document.body.removeChild(textarea);
      return;
    }

    // Using the Clipboard API for modern browsers
    navigator.clipboard.writeText(text)
      .then(() => {
        // Add toast here
        console.log('Text copied to clipboard successfully!');
      })
      .catch((err) => {
        console.error('Failed to copy text to clipboard:', err);
      });
  },
  epochToDateTime(timestamp) {
    var date = new Date(timestamp * 1000);
    return date.toLocaleString('en-US', { timeZone: 'Asia/Kolkata' });
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
        choiceList.push({ label: choice, value: choice })
      })
      localFilters[index].choices = choiceList
    })
    return localFilters
  },
  timeNow: () => {
    return parseInt(new Date().getTime() / 1000)
  },
  convertKeysToLowercase: function (obj){
    return Object.keys(obj).reduce((acc, k) => {
      acc[k.toLowerCase()] = obj[k];
      return acc;
    }, {});
  },
  requestJson: function (message, highlightPaths) {

    if(message==undefined){
      return {}
    }
    let result = {}
    let requestHeaders = {}

    let requestHeadersString = "{}"
    let requestPayloadString = "{}"
    let queryParamsString = ""
    if (message["request"]) {
      queryParamsString = message["request"]["queryParams"]
      requestHeadersString = message["request"]["headers"] || "{}"
      requestPayloadString = message["request"]["body"] || "{}"
    } else {
      let url = message["path"]
      let urlSplit = url.split("?")
      queryParamsString = urlSplit.length > 1 ? urlSplit[1] : ""

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

    let requestPayload = {}
    try {
      requestPayload = JSON.parse(requestPayloadString)
    } catch (e) {
      requestPayload = requestPayloadString
    }

    result["json"] = { "queryParams": queryParams, "requestHeaders": requestHeaders, "requestPayload": requestPayload }
    result["highlightPaths"] = {}
    result['firstLine'] = func.requestFirstLine(message)
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
  responseJson: function (message, highlightPaths) {

    if(message==undefined){
      return {}
    }
    let result = {}

    let responseHeadersString = "{}"
    let responsePayloadString = "{}"
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
  requestFirstLine(message) {
    if (message["request"]) {
      let url = message["request"]["url"]
      return message["request"]["method"] + " " + url + " " + message["request"]["type"]
    } else {
      return message.method + " " + message.path.split("?")[0] + " " + message.type
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
sortFunc: (data, sortKey, sortOrder) => {
  return data.sort((a, b) => {
    if(typeof a[sortKey] ==='number')
    return (sortOrder) * (a[sortKey] - b[sortKey]);
    if(typeof a[sortKey] ==='string')
    return (sortOrder) * (b[sortKey].localeCompare(a[sortKey]));
  })
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

toMethodUrlString({method,url}){
  return method + " " + url;
},
toMethodUrlObject(str){
  return {method:str.split(" ")[0], url:str.split(" ")[1]}
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
parameterizeUrl(x) {
  let re = /INTEGER|STRING|UUID/gi;
  let newStr = x.replace(re, (match) => { 
      return "{param_" + match + "}";
  });
  return newStr
},
mergeApiInfoAndApiCollection(listEndpoints, apiInfoList, idToName, iconFunc) {
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
              } else {
                  access_type = "Private"
              }
          }

          let authType = apiInfoMap[key] ? apiInfoMap[key]["actualAuthType"].join(", ") : ""

          ret[key] = {
              id: x.method+ " " + x.url,
              shadow: x.shadow ? x.shadow : false,
              sensitive: x.sensitive,
              tags: x.tags,
              endpoint: x.url,
              parameterisedEndpoint: this.parameterizeUrl(x.url),
              open: apiInfoMap[key] ? apiInfoMap[key]["actualAuthType"].indexOf("UNAUTHENTICATED") !== -1 : false,
              access_type: access_type || "None",
              method: x.method,
              color: x.sensitive && x.sensitive.size > 0 ? "#f44336" : "#00bfa5",
              apiCollectionId: x.apiCollectionId,
              last_seen: apiInfoMap[key] ? ("Seen " + this.prettifyEpoch(apiInfoMap[key]["lastSeen"])) : 0,
              detectedTs: x.startTs,
              changesCount: x.changesCount,
              changes: x.changesCount && x.changesCount > 0 ? (x.changesCount +" new parameter"+(x.changesCount > 1? "s": "")) : '-',
              added: "Discovered " + this.prettifyEpoch(x.startTs),
              violations: apiInfoMap[key] ? apiInfoMap[key]["violations"] : {},
              apiCollectionName: idToName ? (idToName[x.apiCollectionId] || '-') : '-',
              auth_type: (authType || "").toLowerCase(),
              sensitiveTags: [...this.convertSensitiveTags(x.sensitive)],
              method_icon: iconFunc ? iconFunc(x.method) : null
          }

      }
  })
  
  return Object.values(ret) 
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
getDeprecatedEndpoints(apiInfoList, unusedEndpoints) {
  let ret = []
  apiInfoList.forEach(apiInfo => {
      if (apiInfo.lastSeen < (func.timeNow() - func.recencyPeriod)) {
          ret.push({
              endpoint: apiInfo.id.url, 
              method: apiInfo.id.method,
              lastSeen: func.prettifyEpoch(apiInfo.lastSeen),
              color: func.actionItemColors()["This week"]
          })
      }
  })

  try {
      unusedEndpoints.forEach((x) => {
          if (!x) return;
          let arr = x.split(" ");
          if (arr.length < 2) return;
          ret.push({
          endpoint : arr[0],
          method : arr[1],
          color: func.actionItemColors()["This week"],
          lastSeen: 'in API spec file'
          })
      })
  } catch (e) {
  }
  return ret
}

}

export default func