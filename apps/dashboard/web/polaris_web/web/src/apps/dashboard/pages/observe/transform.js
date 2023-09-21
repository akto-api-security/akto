import func from "@/util/func";
import Store from "../../store";
import { Badge, Box, HorizontalStack, Text } from "@shopify/polaris";

const standardHeaders = [
    'accept', 'accept-ch', 'accept-ch-lifetime', 'accept-charset', 'accept-encoding', 'accept-language', 'accept-patch', 'accept-post', 'accept-ranges', 'access-control-allow-credentials', 'access-control-allow-headers', 'access-control-allow-methods', 'access-control-allow-origin', 'access-control-expose-headers', 'access-control-max-age', 'access-control-request-headers', 'access-control-request-method', 'age', 'allow', 'alt-svc', 'alt-used', 'authorization',
    'cache-control', 'clear-site-data', 'connection', 'content-disposition', 'content-dpr', 'content-encoding', 'content-language', 'content-length', 'content-location', 'content-range', 'content-security-policy', 'content-security-policy-report-only', 'content-type', 'cookie', 'critical-ch', 'cross-origin-embedder-policy', 'cross-origin-opener-policy', 'cross-origin-resource-policy',
    'date', 'device-memory', 'digest', 'dnt', 'downlink', 'dpr',
    'early-data', 'ect', 'etag', 'expect', 'expect-ct', 'expires',
    'forwarded', 'from',
    'host',
    'if-match', 'if-modified-since', 'if-none-match', 'if-range', 'if-unmodified-since',
    'keep-alive',
    'large-allocation', 'last-modified', 'link', 'location',
    'max-forwards',
    'nel',
    'origin',
    'permissions-policy', 'pragma', 'proxy-authenticate', 'proxy-authorization',
    'range', 'referer', 'referrer-policy', 'retry-after', 'rtt',
    'save-data', 'sec-ch-prefers-color-scheme', 'sec-ch-prefers-reduced-motion', 'sec-ch-prefers-reduced-transparency', 'sec-ch-ua', 'sec-ch-ua-arch', 'sec-ch-ua-bitness', 'sec-ch-ua-full-version', 'sec-ch-ua-full-version-list', 'sec-ch-ua-mobile', 'sec-ch-ua-model', 'sec-ch-ua-platform', 'sec-ch-ua-platform-version', 'sec-fetch-dest', 'sec-fetch-mode', 'sec-fetch-site', 'sec-fetch-user', 'sec-gpc', 'sec-purpose', 'sec-websocket-accept', 'server', 'server-timing', 'service-worker-navigation-preload', 'set-cookie', 'sourcemap', 'strict-transport-security',
    'te', 'timing-allow-origin', 'tk', 'trailer', 'transfer-encoding',
    'upgrade', 'upgrade-insecure-requests', 'user-agent',
    'vary', 'via', 'viewport-width',
    'want-digest', 'warning', 'width', 'www-authenticate',
    'x-content-type-options', 'x-dns-prefetch-control', 'x-forwarded-for', 'x-forwarded-host', 'x-forwarded-proto', 'x-frame-options', 'x-xss-protection'
];

const transform = {
    prepareEndpointData: (apiCollectionMap, res) => {
        let apiCollection = apiCollectionMap[res?.data?.endpoints[0]?.apiCollectionId];
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
            added: "Discovered " + func.prettifyEpoch(x.timestamp),
            location: (x.responseCode === -1 ? 'Request' : 'Response') + ' ' + (x.isHeader ? 'headers' : 'payload'),
            subType: x.subType.name,
            detectedTs: x.timestamp,
            apiCollectionId: x.apiCollectionId,
            apiCollectionName: idToNameMap[x.apiCollectionId] || '-',
            x: x,
            domain: func.prepareDomain(x),
            valuesString: func.prepareValuesTooltip(x)
        }
    },
    formatNumberWithCommas(number) {
        const numberString = number.toString();
        return numberString.replace(/\B(?=(\d{3})+(?!\d))/g, ",");
    },
    getStandardHeaderList(){
        return standardHeaders
    },
    getCommonSamples(nonSensitiveData, sensitiveData){
        let sensitiveSamples = this.prepareSampleData(sensitiveData, '')
        const samples = new Set()
        const highlightPathsObj ={}
        sensitiveSamples.forEach((x) => {
            samples.add(JSON.parse(x.message))
            highlightPathsObj[JSON.parse(x.message)] = x.highlightPaths
        })

        let uniqueNonSensitive = []
        nonSensitiveData.forEach((x) => {
            let parsed = JSON.parse(x)
            if(!samples.has(parsed)){
                uniqueNonSensitive.push({message: x, highlightPaths: []})
            }
        })
        const finalArr = [...sensitiveSamples, ...uniqueNonSensitive]
        return finalArr
    },

    getColor(key){
        switch(key){
            case "HIGH" : return "bg-critical-subdued";
            case "MEDIUM": return "bg-caution-subdued";
            case "LOW": return "bg-info-subdued";
            default:
                return "bg";
        }
    },

    getStatus(riskScore){
        if(riskScore > 4.5){
            return "critical"
        }else if(riskScore > 4){
            return "attention"
        }else if(riskScore > 2.5){
            return "warning"
        }else if(riskScore > 0){
            return "info"
        }else{
            return "success"
        }
    },

    getIssuesList(severityInfo){
        return (
            <HorizontalStack gap="1">
                {
                    Object.keys(severityInfo).length > 0 ? Object.keys(severityInfo).map((key,index)=>{
                        return(
                            <Box borderRadius="2" background={this.getColor(key)} width="30px" key={index} paddingBlockEnd={"05"} paddingBlockStart={"05"}>
                                <HorizontalStack align="center">
                                    <Text variant="bodySm" color="subdued" fontWeight="regular">{severityInfo[key]}</Text>
                                </HorizontalStack>
                            </Box>
                        )
                    }):
                    <Text fontWeight="regular" variant="bodyMd" color="subdued">-</Text>
                }
            </HorizontalStack>
        )
    },

    prettifyCollectionsData(newData){
        const prettifyData = newData.map((c)=>{
            return{
                id: c.id,
                nextUrl: '/dashboard/observe/inventory/' + c.id,
                displayName: c.displayName,
                endpoints: c.endpoints,
                riskScore: <Badge status={this.getStatus(0)} size="small">{"0"}</Badge>,
                coverage: c.endpoints > 0 ?Math.ceil((c.testedEndpoints * 100)/c.endpoints) + '%' : '0%',
                issuesArr: this.getIssuesList(c.severityInfo),
                sensitiveSubTypes: c.sensitiveInRespTypes.length,
                lastTraffic: c.detected,
            }
        })


        return prettifyData
    }
      
}

export default transform