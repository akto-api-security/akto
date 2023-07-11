import {
  CircleCancelMinor,
  CalendarMinor,
  ClockMinor
} from '@shopify/polaris-icons';
import { saveAs } from 'file-saver'

const func = {
    toDateStr (date, needYear) {
        let strArray=['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];
        let d = date.getDate();
        let m = strArray[date.getMonth()];
        let y = date.getFullYear();
        return m + ' ' + d + (needYear ? ' ' + y: '' );
    },
    prettifyEpoch(epoch) {
        let diffSeconds = (+Date.now())/1000 - epoch
        let sign = 1
        if(diffSeconds < 0){sign = -1}

    if (diffSeconds < 120) {
      return '1 minute ago'
    } else if (diffSeconds < 3600) {
      return Math.round(diffSeconds / 60) + ' minutes ago'
    } else if (diffSeconds < 7200) {
      return '1 hour ago'
    } else if (diffSeconds < 86400) {
      return Math.round(diffSeconds / 3600) + ' hours ago'
    }

        let diffDays = diffSeconds/86400
        let diffWeeks = diffDays/7
        let diffMonths = diffDays/30
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
  
    let csv = headerTextToValueMap.join(",")+"\r\n"
    data.forEach(i => {
        csv += Object.values(headerTextToValueMap).map(h => this.valToString(i[h])).join(",") + "\r\n"
    })
    let blob = new Blob([csv], {
        type: "application/csvcharset=UTF-8"
    });
    saveAs(blob, (selectedTestRun.name || "file") + ".csv");
  },
  flattenObject (obj, prefix = '') {
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
  findInObjectValue (obj, query, keysToIgnore=[]){
    let flattenedObject = this.flattenObject(obj);
    let ret = false;
    Object.keys(flattenedObject).forEach((key) => {
      ret |= !keysToIgnore.some(ignore => key.toLowerCase().includes(ignore.toLowerCase())) && 
      flattenedObject[key].toString().toLowerCase().includes(query);
    })
    return ret;
  },
  getSeverityStatus(countIssues) {
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
      return {
        confidence: key,
        count: countIssues[key]
      }
    })
  },
  getStatus(item) {
    let confidence = item.confidence.toUpperCase();
    switch (confidence) {
      case 'HIGH': return 'critical';
      case 'MEDIUM': return 'warning';
      case 'LOW': return 'neutral';
    }
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
  epochToDateTime (timestamp) {
    var date = new Date(timestamp * 1000);
    return date.toLocaleString('en-US', { timeZone: 'Asia/Kolkata' });
},
}

export default func