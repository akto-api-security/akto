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
    weekStart (date) {
        let date1 = new Date(Date.UTC(date.getFullYear(), date.getMonth(), date.getDate()))
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
        return Date.UTC(yyyymmdd/10000, (yyyymmdd/100)%100 - 1, yyyymmdd%100)
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
    toDateStrShort(date) {
        var d = "" + date.getDate();
        var m = "" + (date.getMonth()+1);
        var y = "" + date.getFullYear();
        if (m.length < 2) m = '0' + m
        if (d.length < 2) d = '0' + d
        return y + "-" + m + "-" + d
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
        return x.savedAsSensitive || x.sensitive || x.subType === "EMAIL" || x.subType === "CREDIT_CARD" || x.subType.indexOf("PHONE_NUMBER") === 0 || x.subType === "SSN" || x.subType === "ADDRESS" || x.subType === "PAN_CARD"
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

                let authType = apiInfoMap[key] ? apiInfoMap[key]["actualAuthType"].join(" or ") : ""

                ret[key] = {
                    sensitive: x.sensitive,
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
                    sensitiveTags: x.sensitive
                }

            }
        })
        
        return Object.values(ret) 
    },
    recencyPeriod: 600 * 24 * 60 * 60,
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
    }
}