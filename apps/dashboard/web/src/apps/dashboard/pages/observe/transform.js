import func from "@/util/func";

function convertHighlightPathToMap(highlightPaths){

    let highlightPathMap = { request: {}, response: {} }
    for (const x of highlightPaths) {
        if (x["responseCode"] === -1) {
            let keys = []
            if (x["header"]) {
                keys.push("requestHeaders#" + x["param"])
            } else {
                keys.push("requestPayload#" + x["param"])
                keys.push("queryParams#" + x["param"])
            }

            keys.forEach((key) => {
                key = key.toLowerCase()
                highlightPathMap.request[key] = x["highlightValue"]
            })
        } else {
            let key = ""
            if (x["header"]) {
                key = "responseHeaders#" + x["param"]
            } else {
                key = "responsePayload#" + x["param"];
            }
            key = key.toLowerCase();
            highlightPathMap.response[key] = x["highlightValue"]
        }
    }
    return highlightPathMap;
}

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

            let highlightPaths = paramInfoList.filter((x) => {
                return x?.subType?.name==subType
            }).map((x) => {
                let localSubType = x["subType"]
                let val = {}
                val["value"] = localSubType["name"]
                val["asterisk"] = false
                val["highlight"] = true
                x["highlightValue"] = val
                return x
            })
            paths.push({message:c, highlightPathMap:convertHighlightPathToMap(highlightPaths)})
        }
        return paths;
    }
}

export default transform