import func from "@/util/func";
import Store from "../../store";

const transform = {
    prepareEndpointData: (apiCollectionMap, res) => {
        let apiCollection = apiCollectionMap[res.data.endpoints[0].apiCollectionId];
        let lastSeen = res.data.endpoints.reduce((max, item) => {
            return (max > item.lastSeen ? max : item.lastSeen );
            }, 0)
        let locations = res.data.endpoints.reduce((location, item) => {
            if(item?.isHeader) location.add("header");
            if(item?.isUrlParam) location.add("URL param");
            if(!item?.isHeader && !item?.isUrlParam) location.add("payload");
            return location
        }, new Set())
        let tmp = {}
        tmp.collection = apiCollection;
        tmp.detected_timestamp = "Detected " + func.prettifyEpoch(lastSeen)
        tmp.location =  "Detected in " + [...locations].join(", ");
        return tmp;
    },
    prepareSampleData: (res, subType) => {
        let paths = []
        for (const c in res.sensitiveSampleData) {
            let paramInfoList = res.sensitiveSampleData[c]
            if (!paramInfoList) {
                paramInfoList = []
            }
            let highlightPaths = paramInfoList.map((x) => {
                let val = {}
                val["value"] = x["subType"]["name"]
                val["asterisk"] = false
                val["highlight"] = true
                val['other'] = x["subType"]["name"]!=subType ? true : false 
                x["highlightValue"] = val
                return x
            })
            paths.push({message:c, highlightPaths:highlightPaths}); 
        }
        return paths;
    },
    findNewParametersCount: (resp, startTimestamp, endTimestamp) => {
        let newParametersCount = 0
        let todayDate = new Date(endTimestamp * 1000)
        let twoMonthsAgo = new Date(startTimestamp * 1000)

        let currDate = twoMonthsAgo
        let ret = []
        let dateToCount = resp.reduce((m, e) => {
            let detectDate = func.toYMD(new Date(e._id * 86400 * 1000))
            m[detectDate] = (m[detectDate] || 0) + e.count
            newParametersCount += e.count
            return m
        }, {})
        while (currDate <= todayDate) {
            ret.push([func.toDate(func.toYMD(currDate)), dateToCount[func.toYMD(currDate)] || 0])
            currDate = func.incrDays(currDate, 1)
        }
        return newParametersCount
    },
    fillSensitiveParams: (sensitiveParams, apiCollection) => {
        sensitiveParams = sensitiveParams.reduce((z,e) => {
            let key = [e.apiCollectionId + "-" + e.url + "-" + e.method]
            z[key] = z[key] || new Set()
            z[key].add(e.subType || {"name": "CUSTOM"})
            return z
        },{})
        Object.entries(sensitiveParams).forEach(p => {
            let apiCollectionIndex = apiCollection.findIndex(e => {
                return (e.apiCollectionId + "-" + e.url + "-" + e.method) === p[0]
            })
            if (apiCollectionIndex > -1) {
                apiCollection[apiCollectionIndex].sensitive = p[1]
            }
        })
        return apiCollection;
    },
    prepareEndpointForTable(x, index) {
        const idToNameMap = func.mapCollectionIdToName(Store.getState().allCollections);
        return {
            id:index,
            name: x.param.replaceAll("#", ".").replaceAll(".$", ""),
            endpoint: x.url,
            url: x.url,
            method: x.method,
            added: func.prettifyEpoch(x.timestamp),
            location: (x.responseCode === -1 ? 'Request' : 'Response') + ' ' + (x.isHeader ? 'headers' : 'payload'),
            type: x.subType.name,
            detectedTs: x.timestamp,
            apiCollectionId: x.apiCollectionId,
            apiCollectionName: idToNameMap[x.apiCollectionId] || '-',
            x: x,
            domain: func.prepareDomain(x),
            valuesString: func.prepareValuesTooltip(x)
        }
    }
}

export default transform