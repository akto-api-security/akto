import func from "@/util/func";
import { Badge, Box, HorizontalStack, Icon, Text, Tooltip } from "@shopify/polaris";
import PersistStore from "../../../main/PersistStore";
import tranform from "../onboarding/transform"
import TooltipText from "../../components/shared/TooltipText";
import StyledEndpoint from "./api_collections/component/StyledEndpoint"
import { SearchMinor, InfoMinor, LockMinor, ClockMinor, PasskeyMinor, LinkMinor, DynamicSourceMinor, GlobeMinor, LocationsMinor, PriceLookupMinor } from "@shopify/polaris-icons"
import api from "./api";

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

const apiDetailsHeaders = [
    {
        text: "Method",
        value: "method",
        showFilter: true
    },
    {
        text: "Endpoint",
        value: "parameterisedEndpoint",
        itemOrder: 1,
        showFilter: true,
        component: StyledEndpoint
    },
    {
        text: 'Collection name',
        value: 'apiCollectionName',
        itemOrder: 3,
        icon: DynamicSourceMinor,
    },
    {
        text: 'Tags',
        value: 'tags',
        itemCell: 3,
        showFilter: true
    },
    {
        text: 'Sensitive Params',
        value: 'sensitiveTags',
        itemOrder: 2,
        showFilter: true
    },
    {
        text: 'Last Seen',
        value: 'last_seen',
        icon: SearchMinor,
        itemOrder: 3
    },
    {
        text: 'Access Type',
        value: 'access_type',
        icon: InfoMinor,
        itemOrder: 3,
        showFilter: true
    },
    {
        text: 'Auth Type',
        value: 'auth_type',
        icon: LockMinor,
        itemOrder: 3,
        showFilter: true
    },
    {
        text: "Discovered",
        value: 'added',
        icon: ClockMinor,
        itemOrder: 3
    },
    {
        text: 'Changes',
        value: 'changes',
        icon: InfoMinor,
        itemOrder: 3

    }
]

const paramHeaders = [
    {
        text: 'Name',
        value: 'name',
        itemOrder: 1,
    },
    {
        text: 'Type',
        value: 'subType',
        icon: PasskeyMinor,
        itemOrder: 3
    },
    {
        text: 'Endpoint',
        value: 'endpoint',
        icon: LinkMinor,
        itemOrder: 3,
        sortKey: 'url',
        showFilterMenu: true
    },
    {
        text: 'Collection',
        value: 'apiCollectionName',
        icon: DynamicSourceMinor,
        itemOrder: 3,
        sortKey: 'apiCollectionId',
        showFilterMenu: true
    },
    {
        text: 'Method',
        value: 'method',
        icon: GlobeMinor,
        itemOrder: 3,
        sortKey: 'method',
        showFilterMenu: true
    },
    {
        text: 'Location',
        value: 'location',
        icon: LocationsMinor,
        itemOrder: 3,
        sortKey: 'isHeader',
        showFilterMenu: true
    },
    {
        text: "Discovered",
        value: 'added',
        icon: ClockMinor,
        itemOrder: 3,
        sortKey: 'timestamp',
        showFilterMenu: true
    },
    {
        text: 'Values',
        value: 'domain',
        icon: PriceLookupMinor,
        itemOrder: 3,
        showFilterMenu: true
    }
]

const lastFetchedInfo = PersistStore.getState().lastFetchedInfo
const lastFetchedResp = PersistStore.getState().lastFetchedResp
const lastFetchedSeverityResp = PersistStore.getState().lastFetchedSeverityResp
const lastCalledSensitiveInfo = PersistStore.getState().lastCalledSensitiveInfo
const lastFetchedSensitiveResp = PersistStore.getState().lastFetchedSensitiveResp
const setLastFetchedInfo = PersistStore.getState().setLastFetchedInfo
const setLastFetchedResp = PersistStore.getState().setLastFetchedResp
const setLastFetchedSeverityResp = PersistStore.getState().setLastFetchedSeverityResp
const setLastCalledSensitiveInfo = PersistStore.getState().setLastCalledSensitiveInfo
const setLastFetchedSensitiveResp = PersistStore.getState().setLastFetchedSensitiveResp

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
    findNewParametersCountTrend: (resp, startTimestamp, endTimestamp) => {
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
        return {
            count: newParametersCount,
            trend: ret
        }
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
        const idToNameMap = PersistStore.getState().collectionsMap;
        const endpointComp = this.getPrettifyEndpoint(x.method, x.url, false);
        const name = x.param.replaceAll("#", ".").replaceAll(".$", "")

        return {
            id:index,
            name: <Box maxWidth="130px"><TooltipText tooltip={name} text={name} textProps={{fontWeight: 'medium'}} /></Box> ,
            endpoint: x.url,
            url: x.url,
            method: x.method,
            added: func.prettifyEpoch(x.timestamp),
            location: (x.responseCode === -1 ? 'Request' : 'Response') + ' ' + (x.isHeader ? 'headers' : 'payload'),
            subType: x.subType.name,
            detectedTs: x.timestamp,
            apiCollectionId: x.apiCollectionId,
            apiCollectionName: idToNameMap[x.apiCollectionId] || '-',
            x: x,
            domain: func.prepareDomain(x),
            valuesString: func.prepareValuesTooltip(x),
            endpointComp: endpointComp,
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
        switch(key.toUpperCase()){
            case "HIGH" : return "critical";
            case "MEDIUM": return "attention";
            case "LOW": return "info";
            default:
                return "bg";
        }
    },

    getStatus(riskScore){
        if(riskScore >= 4.5){
            return "critical"
        }else if(riskScore >= 4){
            return "attention"
        }else if(riskScore >= 2.5){
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
                            <Badge size="small" status={this.getColor(key)} key={index}>{severityInfo[key].toString()}</Badge>
                        )
                    }):
                    <Text fontWeight="regular" variant="bodyMd" color="subdued">-</Text>
                }
            </HorizontalStack>
        )
    },

    prettifySubtypes(sensitiveTags){
        return(
            <Box maxWidth="200px">
                <HorizontalStack gap={1}>
                    {sensitiveTags.map((item,index)=>{
                        return(index < 4 ? <Tooltip dismissOnMouseOut content={item} key={index}><Box><Icon color="subdued" source={func.getSensitiveIcons(item)} /></Box></Tooltip> : null)
                    })}
                    {sensitiveTags.length > 4 ? <Badge size="small" status="warning" key={"more"}>{'+' + (sensitiveTags.length - 4).toString() + 'more'}</Badge> : null}
                </HorizontalStack>
            </Box>
        )
    },

    prettifyCollectionsData(newData){
        const prettifyData = newData.map((c)=>{
            return{
                id: c.id,
                nextUrl: '/dashboard/observe/inventory/' + c.id,
                displayName: c.displayName,
                displayNameComp: c.displayNameComp,
                endpoints: c.endpoints,
                riskScoreComp: <Badge status={this.getStatus(c.riskScore)} size="small">{c.riskScore}</Badge>,
                coverage: c.endpoints > 0 ?Math.ceil((c.testedEndpoints * 100)/c.endpoints) + '%' : '0%',
                issuesArr: this.getIssuesList(c.severityInfo),
                sensitiveSubTypes: this.prettifySubtypes(c.sensitiveInRespTypes),
                lastTraffic: c.detected,
                riskScore: c.riskScore,
            }
        })


        return prettifyData
    },

    getSummaryData(collectionsData){
        let totalUrl = 0;
        let sensitiveInRes = 0;
        let totalTested = 0 ;

        collectionsData?.forEach((c) =>{
            totalUrl += c.endpoints ;
            totalTested += c.testedEndpoints;
        })

        return {
            totalEndpoints:totalUrl , totalTestedEndpoints: totalTested, totalSensitiveEndpoints: sensitiveInRes
        }
    },

    getTruncatedUrl(url){
        try {
            const parsedURL = new URL(url)
            return parsedURL.pathname
        } catch (error) {
            return url
        }
    },

    getHostName(url){
        try {
            const parsedURL = new URL(url)
            return parsedURL.hostname
        } catch (error) {
            return "No host"
        }
    },
    
    getPrettifyEndpoint(method,url, isNew){
        return(   
            <div style={{display: 'flex', gap: '4px'}}>
                <Box width="54px">
                    <HorizontalStack align="end">
                        <span style={{color: tranform.getTextColor(method), fontSize: "14px", fontWeight: 500}}>{method}</span>
                    </HorizontalStack>
                </Box>
                <Box width="200px" maxWidth="200px">
                    <HorizontalStack align="space-between">
                        <Box maxWidth={isNew ? "140px" : '180px'}>
                            <TooltipText text={this.getTruncatedUrl(url)} tooltip={this.getTruncatedUrl(url)} textProps={{fontWeight: "medium"}} />
                        </Box>
                        {isNew ? <Badge size="small">New</Badge> : null}
                    </HorizontalStack>
                </Box>
            </div> 
        )
    },

    getRiskScoreValue(severity){
        if(severity >= 100){
            return 2
        }else if(severity >= 10){
            return 1
        }else if(severity > 0){
            return 0.5
        }else{
            return 0
        }
    },

    isNewEndpoint(lastSeen){
        let lastMonthEpoch = func.timeNow() - (30 * 24 * 60 * 60);
        return lastSeen > lastMonthEpoch
    },

    getRiskScoreForEndpoint(url){
        let riskScore = 0 
        riskScore += this.getRiskScoreValue(url.severityScore);

        if(url.access_type === "Public"){
            riskScore += 1
        }

        if(url.isSensitive){
            riskScore += 1
        }

        if(this.isNewEndpoint(url.lastSeenTs)){
            riskScore += 1
        }

        return riskScore
    },

    prettifyEndpointsData(inventoryData){
        const hostNameMap = PersistStore.getState().hostNameMap
        const prettifyData = inventoryData.map((url) => {
            const score = this.getRiskScoreForEndpoint(url)
            return{
                ...url,
                last_seen: url.last_seen,
                hostName: (hostNameMap[url.apiCollectionId] !== null ? hostNameMap[url.apiCollectionId] : this.getHostName(url.endpoint)),
                access_type: url.access_type,
                auth_type: url.auth_type,
                endpointComp: this.getPrettifyEndpoint(url.method, url.endpoint, this.isNewEndpoint(url.lastSeenTs)),
                sensitiveTagsComp: this.prettifySubtypes(url.sensitiveTags),
                riskScoreComp: <Badge status={this.getStatus(score)} size="small">{score.toString()}</Badge>,
                riskScore: score,
                isNew: this.isNewEndpoint(url.lastSeenTs)
            }
        })

        return prettifyData
    },

    getDetailsHeaders(){
        return apiDetailsHeaders
    },

    changesTrend (data, startTimestamp, endTimestamp) {
        let end = new Date(endTimestamp * 1000)
        let start = new Date(startTimestamp * 1000)
        
        let date = start
        let ret = []
        let dateToCount = data.reduce((m, e) => { 
            let detectDate = func.toYMD(new Date(e.detectedTs*1000))
            m[detectDate] = (m[detectDate] || 0 ) + 1
            return m
        }, {})
        while (date <= end) {
            ret.push([func.toDate(func.toYMD(date)), dateToCount[func.toYMD(date)] || 0])
            date = func.incrDays(date, 1)
        }
        return ret
    },

    getParamHeaders(){
        return paramHeaders;
    },

    async fetchRiskScoreInfo(){
        let tempRiskScoreObj = lastFetchedResp
        let tempSeverityObj = lastFetchedSeverityResp
        await api.lastUpdatedInfo().then(async(resp) => {
            if(resp.lastUpdatedSeverity >= lastFetchedInfo.lastRiskScoreInfo || resp.lastUpdatedSensitiveMap >= lastFetchedInfo.lastSensitiveInfo){
                await api.getRiskScoreInfo().then((res) =>{
                    const newObj = {
                        criticalUrls: res.criticalEndpointsCount,
                        riskScoreMap: res.riskScoreOfCollectionsMap, 
                    }
                    tempRiskScoreObj = JSON.parse(JSON.stringify(newObj));
                    setLastFetchedResp(newObj);
                })
            }
            if(resp.lastUpdatedSeverity >= lastFetchedInfo.lastRiskScoreInfo){
                await api.getSeverityInfoForCollections().then((resp) => {
                    tempSeverityObj = JSON.parse(JSON.stringify(resp))
                    setLastFetchedSeverityResp(resp)
                })
            }
            setLastFetchedInfo({
                lastRiskScoreInfo: func.timeNow() >= resp.lastUpdatedSeverity ? func.timeNow() : resp.lastUpdatedSeverity,
                lastSensitiveInfo: func.timeNow() >= resp.lastUpdatedSensitiveMap ? func.timeNow() : resp.lastUpdatedSensitiveMap,
            })
        })
        let finalObj = {
            riskScoreObj: tempRiskScoreObj,
            severityObj: tempSeverityObj
        }
        return finalObj
    },

    async fetchSensitiveInfo(){
        let tempSensitiveInfo = lastFetchedSensitiveResp
        if((func.timeNow() - (5 * 60)) >= lastCalledSensitiveInfo){
            await api.getSensitiveInfoForCollections().then((resp) => {
                const sensitiveObj = {
                    sensitiveUrls: resp.sensitiveUrlsInResponse,
                    sensitiveInfoMap: resp.sensitiveSubtypesInCollection
                }
                setLastCalledSensitiveInfo(func.timeNow())
                setLastFetchedSensitiveResp(sensitiveObj)
                tempSensitiveInfo = JSON.parse(JSON.stringify(sensitiveObj))
            })
        }
        return tempSensitiveInfo; 
    }

      
}

export default transform