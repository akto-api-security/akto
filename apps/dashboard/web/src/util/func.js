const globalFunctions = {
    prettifyEpoch(epoch) {
        var diffSeconds = (+Date.now())/1000 - epoch

        var sign = 1
        if(diffSeconds < 0){sign = -1}

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
        } else if (diffDays === 1) {
            return sign > 0 ? 'tomorrow' : 'yesterday'
        } else if (diffDays === 0) {
            return 'today'
        }

        var plural = count <= 1 ? '' : 's'

        return count + ' ' + unit + plural + ' ago'
    },
    testingResultType(){
        return {
            BURP:"BURP",
            CICD:"CICD",
            EXTERNAL_API:"EXTERNAL_API"
        }
    },
    initials(str) {
        let ret = str.split(" ").map((n)=>n[0]).slice(0,2).join("").toUpperCase()

        if (ret.length == 1) {
            return str.replaceAll(" ","").slice(0,2).toUpperCase()
        } else {
            return ret
        }
    }
}

export default globalFunctions