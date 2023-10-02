import vuetify from "../plugins/vuetify"
export default {
    trackingPeriodStr: (num) => {
        switch (num) {
            case 0: return 'daily'
            case 1: return 'weekly'
            case 2: return 'monthly'
            case 3: return 'quarterly'
            case 4: return 'half-yearly'
            case 5: return 'yearly'
        }
    },
    trackingPeriodNum: (str) => {
        switch (str) {
            case 'daily': return 0
            case 'weekly': return 1
            case 'monthly': return 2
            case 'quarterly': return 3
            case 'half-yearly': return 4
            case 'yearly': return 5
        }
    },
    sourceNum: (str) => {
        switch (str.toLowerCase().replaceAll(" ", "")) {
            case 'spreadsheet': return 0
            case 'googlesheets': return 0
            case 'salesforce': return 1
            case 'hubspot': return 2
            case 'mysql': return 3
            case 'postgresql': return 4
            case 'redshift': return 5
            case 'bigtable': return 6
            case 'snowflake': return 7

        }
    },
    returnResultTypes: () => {
      return ['NUMERIC', 'AVERAGE']
    },
    sourceStr: (num) => {
        switch (num) {
            case 0: return 'Spreadsheet'
            case 1: return 'Salesforce'
            case 2: return 'Hubspot'
        }
    },
    icon: (num) => {
        switch (num) {
            case 0: return {name: '$fas_file-csv', color: '#33a852'}
            case 1: return {name: '$fab_salesforce', color: '#17A0DB'}
            case 2: return {name: '$fab_hubspot', color: '#fe7b5b'}
            case 3: return {name: '$fas_database', color: '#47466A'}
            case 4: return {name: '$fas_database', color: '#47466A'}
            case 5: return {name: '$fas_database', color: '#47466A'}
            case 6: return {name: '$fas_database', color: '#47466A'}
            case 7: return {name: '$fas_database', color: '#47466A'}
        }
    },

    pretty: (num) => {
        return (Math.round(num * 100)/100).toLocaleString()
    },
    timeNow: () => {
        return parseInt(new Date().getTime()/1000)
    },
    todayDate: () => {
        return new Date(Date.now())
    },
    epochToDateTime: (timestamp) => {
        var date = new Date(timestamp * 1000);

        var year = date.getFullYear();
        var month = date.getMonth() + 1;
        var day = date.getDate();
        var hours = date.getHours();
        var minutes = date.getMinutes();
        var seconds = date.getSeconds();

        return year + "-" + month + "-" + day + " " + hours + ":" + minutes + ":" + seconds
    },
    dayStart(epochMs) {
        let date = new Date(epochMs)
        date.setHours(0)
        date.setMinutes(0)
        date.setSeconds(0)
        return date
    },
    dayEnd(epochMs) {
        let date = new Date(epochMs)
        date.setHours(23)
        date.setMinutes(59)
        date.setSeconds(59)
        return date
    },
    weekStart (date) {
        let date1 = new Date(date.getTime())
        return new Date(date1.setDate(date1.getDate() - date1.getDay() + (date1.getDay() === 0 ? -6 : 1)))
    },
    weekEnd (date) {
        let date1 = this.weekStart(date)
        return new Date(date1.setDate(date1.getDate() + 6))
    },
    monthStart (date) {
        return new Date(date.getFullYear(),date.getMonth(),1)
    },
    monthEnd (date) {
        return new Date(date.getFullYear(),date.getMonth()+1,0) // 0 gives last date of prev month
    },
    quarterStart(date) {
        return new Date(date.getFullYear(),Math.floor(date.getMonth() / 3) * 3,1)
    },
    quarterEnd(date) {
        let customDate = this.quarterStart(new Date(date))
        customDate.setMonth(customDate.getMonth()+3)
        customDate.setDate(0)
        return customDate

    },
    days_between(date1,date2) {
        const oneDay = 24 * 60 * 60 * 1000;
        let firstDate = new Date(date1)
        firstDate.setHours(0,0,0)
        let secondDate = new Date(date2)
        secondDate.setHours(0,0,0)
        return Math.round(Math.abs((firstDate - secondDate) / oneDay)) + 1
    },
    toDate (yyyymmdd) {
        return +new Date(yyyymmdd/10000, (yyyymmdd/100)%100 - 1, yyyymmdd%100)
    },
    toHyphenated (yyyymmdd) {
        let month = parseInt(yyyymmdd/100)%100
        month = (month < 10 ? "0" : "")+month

        let day = parseInt(yyyymmdd)%100
        day = (day < 10 ? "0" : "")+day
        return [parseInt(yyyymmdd/10000), month, day].join("-")
    },
    toDateStr (date, needYear) {
        var strArray=['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];
        var d = date.getDate();
        var m = strArray[date.getMonth()];
        var y = date.getFullYear();
        return m + ' ' + d + (needYear ? ' ' + y: '' );
    },
    toTimeStr (date, needYear) {
        var strArray=['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];
        var d = date.getDate();
        var m = strArray[date.getMonth()];
        var y = date.getFullYear();
        return m + ' ' + d + ', ' + (needYear ? y: '' ) + ' ' + date.toTimeString().substr(0, 5)
    },
    toDateStrShort(date) {
        var d = "" + date.getDate();
        var m = "" + (date.getMonth()+1);
        var y = "" + date.getFullYear();
        if (m.length < 2) m = '0' + m
        if (d.length < 2) d = '0' + d
        return y + "-" + m + "-" + d
    },
    toHyphenatedDate(epochInMs) {
        return this.toDateStrShort(new Date(epochInMs))
    },
    toYMD (date) {
        var d = date.getDate();
        var m = date.getMonth() + 1; //Month from 0 to 11
        var y = date.getFullYear();
        return y * 10000 + m * 100 + d
    },
    prettifyShort(num) {
        return new Intl.NumberFormat( 'en-US', { maximumFractionDigits: 1,notation: "compact" , compactDisplay: "short" }).format(num)
    },
    prettifyEpochWithNull(epoch, textToShowIfNull){
        if(epoch === -1){
            return textToShowIfNull;
        }
        return this.prettifyEpoch(epoch)
    },
    async copyToClipboard(text, onCopyBtnClickText, domElement) {

            // main reason to use domElement like this instead of document.body is that execCommand works only if current
            // component is not above normal document. For example in testing page, we show SampleSingleSide.vue in a v-dialog
            // NOTE: Do not use navigator.clipboard because it only works for HTTPS sites
            if (window.isSecureContext && navigator.clipboard) {
                navigator.clipboard.writeText(text).then(() => {
                    window._AKTO.$emit('SHOW_SNACKBAR', {
                        show: true,
                        text: onCopyBtnClickText,
                        color: 'green'
                    })
                }).catch(err => {
                    console.warn("error in copying to clipboard")
                });
            } else if (window.clipboardData && window.clipboardData.setData) {
                // Internet Explorer-specific code path to prevent textarea being shown while dialog is visible.
                window.clipboardData.setData("Text", text);
                window._AKTO.$emit('SHOW_SNACKBAR', {
                    show: true,
                    text: onCopyBtnClickText,
                    color: 'green'
                })
            } else if (document.queryCommandSupported && document.queryCommandSupported("copy")) {
                // let domElement = _this.$el;
                var textarea = document.createElement("textarea");
                textarea.textContent = text;
                textarea.style.position = "fixed";  // Prevent scrolling to bottom of page in Microsoft Edge.
                domElement.appendChild(textarea);
                textarea.focus();
                textarea.select();
                textarea.setSelectionRange(0, 99999);        
                try {
                    document.execCommand("copy");  // Security exception may be thrown by some browsers.
                    window._AKTO.$emit('SHOW_SNACKBAR', {
                        show: true,
                        text: onCopyBtnClickText,
                        color: 'green'
                    })
                }
                catch (ex) {
                    // console.warn("Copy to clipboard failed.", ex);
                    // return prompt("Copy to clipboard: Ctrl+C, Enter", text);
                }
                finally {
                    domElement.removeChild(textarea);
                }
            }

    },
    prettifyEpoch(epoch) {
        var diffSeconds = (+Date.now())/1000 - epoch
        if (diffSeconds < 120) {
            return '1 minute ago'
        } else if (diffSeconds < 3600) {
            return Math.round(diffSeconds/60) + ' minutes ago'
        } else if (diffSeconds < 7200) {
            return '1 hour ago'
        } else if (diffSeconds < 86400) {
            return Math.round(diffSeconds/3600) + ' hours ago'
        }

        var diffDays = diffSeconds/86400

        var diffWeeks = diffDays/7

        var diffMonths = diffDays/30

        var count = Math.round(diffDays)
        var unit = 'day'

        if (diffMonths > 2) {
            return this.toDateStr(new Date(epoch*1000), true)
        } else if (diffWeeks > 4) {
            count = Math.round(diffMonths + 0.5)
            unit = 'month'
        } else if (diffDays > 11) {
            count = Math.round(diffWeeks + 0.5)
            unit = 'week'
        } else if (diffDays == 1) {
            return sign > 0 ? 'tomorrow' : 'yesterday'
        } else if (diffDays == 0) {
            return 'today'
        }

        var plural = count <= 1 ? '' : 's'

        return count + ' ' + unit + plural + ' ago'
    },
    getCreationTime(creationTime) {
        let epoch = Date.now();
        let difference = epoch / 1000 - creationTime;
        let numberOfDays = difference / (60 * 60 * 24)
        if (numberOfDays < 31) {
            return parseInt(numberOfDays) + ' d';
        } else if (numberOfDays < 366) {
            return parseInt(numberOfDays / 31) + ' m' + parseInt(numberOfDays % 31) + ' d';
        } else {
            return parseInt(numberOfDays / 365) + ' y' + parseInt((numberOfDays % 365) / 31) + ' m'
                + parseInt((numberOfDays % 365) % 31) + ' d';
        }
    },
    prettifyDate(date) {
        var diffSeconds = Math.abs(this.toDate(date) - this.toDate(this.toYMD(new Date(Date.now()))))/1000


        var diffDays = diffSeconds/86400
        var sign = this.toDate(date) > Date.now()

        var diffWeeks = diffDays/7

        var diffMonths = diffDays/30

        var count = diffDays
        var unit = 'day'

        if (diffMonths > 2) {
            return this.toDateStr(new Date(this.toDate(date)), true)
        } else if (diffWeeks > 4) {
            count = Math.round(diffMonths + 0.5)
            unit = 'month'
        } else if (diffDays > 11) {
            count = Math.round(diffWeeks + 0.5)
            unit = 'week'
        } else if (diffDays == 1) {
            return sign > 0 ? 'tomorrow' : 'yesterday'
        } else if (diffDays == 0) {
            return 'today'
        }

        var plural = count <= 1 ? '' : 's'

        if (sign > 0) {
            return 'in ' + count + ' ' + unit + plural
        } else {
            return count + ' ' + unit + plural + ' ago'
        }

    },
    getActionItems(list, colName) {
        if (!list || list.length == 0) {
            return []
        }

        switch (colName.replaceAll(' ','').toLowerCase()) {
            case "thisweek":
                return list.filter(x => x.current_status == 0 && x.dueDate <= +(this.weekEnd(this.todayDate())/1000.0) && x.dueDate > this.timeNow())
            case "pending":
                return list.filter(x => x.current_status == 0 && x.dueDate > +(this.weekEnd(this.todayDate())/1000.0))
            case "overdue":
            case "pastduedate":
                return list.filter(x => x.current_status == 0 && x.dueDate <= this.timeNow())
            case "completed":
                return list.filter(x => x.current_status != 0)
            case "total":
                return list
        }
    },
    initials(str) {
        var ret = str.split(" ").map((n)=>n[0]).slice(0,2).join("").toUpperCase()

        if (ret.length == 1) {
            return str.replaceAll(" ","").slice(0,2).toUpperCase()
        } else {
            return ret
        }
    },
    toSentenceCase(str) {
        if (str == null) return ""
        return str[0].toUpperCase() + (str.length > 1 ? str.substring(1).toLowerCase() : "");
      },
    hashCode(str) {
        var hash = 0;
        for (var i = 0; i < str.length; i++) {
            var character = str.charCodeAt(i);
            hash = ((hash<<5)-hash)+character;
            hash = hash & hash; // Convert to 32bit integer
        }
        return Math.abs(hash);
    },
    colors() {
        return ['red', 'blue', 'green', 'purple', 'brown']
    },
    chartColors() {
        return ['#1EE0B7', '#FF4DCA','#167414','#F68B4F', '#41B3E9']
    },
    convertToPairs(trend) {
        return Object.entries(trend).map(x => {
            return [this.toDate(x[0]), Math.round(100*(+x[1]))/100]
        })
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
    incrDays(date, days) {
        var ret = new Date(date.getFullYear(), date.getMonth(), date.getDate())
        ret.setDate(ret.getDate() + days)
        return ret
    },
    incrMonths(date, months) {
        var ret = new Date(date.getFullYear(), date.getMonth(), date.getDate())
        ret.setMonth(ret.getMonth() + months)
        return ret
    },
    calculateMetricTrend(mapDateToDatapoint, getKey, cumulative) {
        mapDateToDatapoint = Object.entries(mapDateToDatapoint).reduce( (z, e) => {
            let key = getKey(e[0])
            z[key] = z[key] || 0
            z[key] += e[1].value.number
            return z
        }, {})

        let newSeries = [...Object.entries(mapDateToDatapoint)].sort((a,b) => {
            return +a[0] - +b[0]
        })

        let ret = []
        newSeries.forEach((x, i) => {
            ret.push([this.toDate(+x[0]), Math.round((x[1] + (i > 0 && cumulative ? ret[i-1][1] : 0)) * 100)/100])
        })

        return ret
    },
    isSubTypeSensitive(x) {
        return x.savedAsSensitive || x.sensitive
    },
    parameterizeUrl(x) {
        let re = /INTEGER|STRING/gi;
        let newStr = x.replace(re, (match) => { 
            return "{param_" + match + "}";
        });
        return newStr
    },
    mergeApiInfoAndApiCollection(listEndpoints, apiInfoList, idToName) {
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
                    shadow: x.shadow ? x.shadow : false,
                    sensitive: x.sensitive,
                    tags: x.tags,
                    endpoint: x.url,
                    parameterisedEndpoint: this.parameterizeUrl(x.url),
                    open: apiInfoMap[key] ? apiInfoMap[key]["actualAuthType"].indexOf("UNAUTHENTICATED") !== -1 : false,
                    access_type: access_type,
                    method: x.method,
                    color: x.sensitive && x.sensitive.size > 0 ? "#f44336" : "#00bfa5",
                    apiCollectionId: x.apiCollectionId,
                    last_seen: apiInfoMap[key] ? this.prettifyEpoch(apiInfoMap[key]["lastSeen"]) : 0,
                    detectedTs: x.startTs,
                    changesCount: x.changesCount,
                    changes: x.changesCount && x.changesCount > 0 ? (x.changesCount +" new parameter"+(x.changesCount > 1? "s": "")) : '-',
                    added: this.prettifyEpoch(x.startTs),
                    violations: apiInfoMap[key] ? apiInfoMap[key]["violations"] : {},
                    apiCollectionName: idToName ? (idToName[x.apiCollectionId] || '-') : '-',
                    auth_type: (authType || "").toLowerCase(),
                    sensitiveTags: this.convertSensitiveTags(x.sensitive)
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

    recencyPeriod: 60 * 24 * 60 * 60,
    toCommaSeparatedNumber(number) {
        let nf = new Intl.NumberFormat('en-US');
        return nf.format(number);      
    },  
    sensitiveTagDetails(tag) {
        let icon = "$fas_info-circle"
        switch(tag) {
            case "EMAIL":
                icon = "$fas_envelope"
                break;
            case "PHONE_NUMBER_INDIA":
            case "PHONE_NUMBER_US":
            case "PHONE_NUMBER":      
                icon = "$fas_phone-alt"          
                break;
            case "CREDIT_CARD":
                icon = "$fas_credit-card"
                break;
            case "SSN":
                icon = "$fas_chalkboard"
                break;
            case "PAN_CARD":
                icon = "$fas_address-card"
                break;
            case "JWT":
                icon = "$fas_key"
                break;
            case "IP_ADDRESS":
                icon = "$fas_globe"
                break;
            default:
                break;        
        }
        return icon
    },

    preparePredicatesForApi(predicates) {
        let result = []
        if (!predicates) return result
        predicates.forEach((predicate) => {
            let type = predicate["type"]
            let obj = {"type": type}

            let valueMap = {}
            switch (type) {
                case "STARTS_WITH":
                case "ENDS_WITH":
                case "REGEX":
                case "EQUALS_TO":
                    valueMap["value"] = predicate["value"]
                    break;

                case "IS_NUMBER":
                    break;

            }

            obj["valueMap"] = valueMap

            result.push(obj)
        })

        return result;
    },

    generateKeysForApi(predicates){
        let result =[]
        if(!predicates) return result
        predicates.forEach((predicate)=>{
            result.push(predicate["value"])
        })
        return result;
    },

    prepareAuthTypes(auth_types){
        if(auth_types) {
            auth_types.forEach((x)=>{
                x["operator"]="OR"
                x["prefix"] = x["id"] ? "[custom]" : ""
                let headerPredicates =[]
                x["headerKeys"].forEach((key)=>{
                    let obj = {"type":"EQUALS_TO","value":key}
                    headerPredicates.push(obj)
                })
                let payloadPredicates =[]
                x["payloadKeys"].forEach((key)=>{
                    let obj = {"type":"EQUALS_TO","value":key}
                    payloadPredicates.push(obj)
                })
                if(x["id"]){
                    x["headerKeyConditions"] = {
                        "operator":"AND",
                        "predicates": headerPredicates
                    }
                    x["payloadKeyConditions"] = {
                        "operator":"AND",
                        "predicates": payloadPredicates
                    }
                }
            })
        }
        return auth_types
    },

    prepareDataTypes(data_types) {
        if (data_types) {
            data_types.forEach((x) => {
                let isSensitive = x["sensitiveAlways"] || (x["sensitivePosition"] && x["sensitivePosition"].length > 0)
                x["color"] = isSensitive ? vuetify.userPreset.theme.themes.dark.redMetric : "transparent"
                x["prefix"] = x["id"] ? "[custom]" : ""
                if (x["id"]) {
                    if (!x["keyConditions"]) {
                        x["keyConditions"] = {"operator": "AND", "predicates": []}
                    }
                    if (!x["valueConditions"]) {
                        x["valueConditions"] = {"operator": "AND", "predicates": []}
                    }
                } else {
                    x["active"] = true
                }
            })

            data_types.sort(function(a,b){
                if (a["id"] && b["id"]) return b["id"]["timestamp"] - a["id"]["timestamp"]
                return a["id"] ? -1 : 1
            })

            data_types.sort(function(a,b){
                if (a["active"] && b["active"]) return 0
                return a["active"] ? -1 : 1
            })
        }
    },

    prettifySensitivePosition(sensitivePosition) {
        let andIndex = sensitivePosition.length - 2
        let result = ""
        for (let idx=0; idx < sensitivePosition.length; idx++) {
            result += sensitivePosition[idx]
            if (andIndex) {
                result += "and"
            } else {
                result += ","
            }
        }

        return result
    },

    icon(name) {
        let ele = document.querySelector(':root');
        let cs = getComputedStyle(ele);
        switch (name) {
            case "BURPSUITE":
                return {name: '$burpsuite', color: cs.getPropertyValue('--hexColor42')}

            case "POSTMAN":
                return {name: '$postman', color: cs.getPropertyValue('--white')}

            case "AKTOAPI":
                return {name: '$restapi', color: cs.getPropertyValue('--hexColor42')}

            case "SLACK":
                return {name: '$slack', color: cs.getPropertyValue('--hexColor42')}
            
            case "WEBHOOKS":
                return {name: '$customwebhooks', color: cs.getPropertyValue('--hexColor42')}

            case "CI/CDINTEGERATION":
                return {name: '$cicdicon', color: cs.getPropertyValue('--hexColor42') }

            case "AKTOGPT":
                return {name: '$chatGPT', color: 'rgb(16, 163, 127)'}
            
            case "GITHUB":
                return {name: '$githubIcon', color: cs.getPropertyValue('--hexColor42')}
        }
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
                    result +=  ", "
                }

                result += elem
                count += 1

            }

            return result;
            
        }
    },

    prepareValuesTooltip(x) {
        let result = "";
        let values = x["values"]
        if (!values) return "No values recorded"
        let elements = values["elements"] ? values["elements"] : []
        let count = 0;
        for (var elem of elements) {
            if (count > 50) return result
            if (count !== 0) {
                result +=  ", "
            }
            result += elem
            count += 1
        }

        return (count == 0 ? "No values recorded" : result)
    },

    showErrorSnackBar(val){
        window._AKTO.$emit('SHOW_SNACKBAR', {show: true, text: val, color: 'red'})
    },
    showSuccessSnackBar(val){
        window._AKTO.$emit('SHOW_SNACKBAR', {show: true, text: val, color: 'green'})
    },
    toEpochInMs(hyphenatedDate) {
        return +this.toDate(hyphenatedDate.replace(/\-/g, ''))
    },

    testingResultType(){
        return {
            BURP:"BURP",
            CICD:"CICD",
            EXTERNAL_API:"EXTERNAL_API"
        }
    },

    testingType () {
        return {
            active:"active",
            inactive:"inactive",
            cicd:"cicd"
        }
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
                {"data": trafficArr, "color": null, "name": key},
            )
        }

        return result
    },

    getListOfHosts(apiCollections) {
        let result = []
        if (!apiCollections || apiCollections.length == 0) return []
        apiCollections.forEach((x) => {
            let hostName = x['hostName']
            if (!hostName) return
            result.push(
                {
                    "title": hostName,
                    "value": hostName
                }
            )
        })

        return result
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

    getRunResultSubCategory (runResult, subCategoryFromSourceConfigMap, subCatogoryMap, fieldName) {
        if (subCatogoryMap[runResult.testSubType] === undefined) {
            let a = subCategoryFromSourceConfigMap[runResult.testSubType]
            return a ? a.subcategory : null
        } else {
            return subCatogoryMap[runResult.testSubType][fieldName]
        }
    },

    getRunResultCategory (runResult, subCatogoryMap, subCategoryFromSourceConfigMap, fieldName) {
        if (subCatogoryMap[runResult.testSubType] === undefined) {
            let a = subCategoryFromSourceConfigMap[runResult.testSubType]
            return a ? a.category.shortName : null
        } else {
            return subCatogoryMap[runResult.testSubType].superCategory[fieldName]
        }
    },

    prettifyArray(arr) {
        if (!arr || arr.length == 0) return ""
        if (arr.length >= 2) {
            return arr.slice(0, -1).join(', ') + ' and ' + arr[arr.length - 1];
          } else {
            return arr[0] || ""
          }
    },
    
    getRunResultCwe(runResult, subCatogoryMap) {
        if (subCatogoryMap[runResult.testSubType]?.cwe)
            return subCatogoryMap[runResult.testSubType].cwe
        return [];
    },

    getRunResultSeverity(runResult, subCategoryMap) {
        let testSubType = subCategoryMap[runResult.testSubType]
        if (!testSubType) {
            return 3
        } else {
            let a = testSubType.superCategory["severity"]["_name"]
            switch(a){
                case "HIGH": 
                    return {title: a, value: 3}

                case "MEDIUM": 
                    return {title: a, value: 2}

                case "LOW": 
                    return {title: a, value: 1}

                default:
                    return {title: a, value: 3}
            }
        }
    },

    convertSecondsToReadableTime(seconds) {
        if (seconds < 60) {
          return `${seconds} ${seconds === 1 ? 'second' : 'seconds'}`;
        } else if (seconds < 3600) {
          const minutes = Math.floor(seconds / 60);
          return `${minutes} ${minutes === 1 ? 'minute' : 'minutes'}`;
        } else if (seconds < 86400) {
          const hours = Math.floor(seconds / 3600);
          const remainingMinutes = Math.floor((seconds % 3600) / 60);
          let result = `${hours} ${hours === 1 ? 'hour' : 'hours'}`;
          if (remainingMinutes > 0) {
            result += ` ${remainingMinutes} ${remainingMinutes === 1 ? 'minute' : 'minutes'}`;
          }
          return result;
        } else {
          const days = Math.floor(seconds / 86400);
          const remainingHours = Math.floor((seconds % 86400) / 3600);
          let result = `${days} ${days === 1 ? 'day' : 'days'}`;
          if (remainingHours > 0) {
            result += ` ${remainingHours} ${remainingHours === 1 ? 'hour' : 'hours'}`;
          }
          return result;
        }
      }

}