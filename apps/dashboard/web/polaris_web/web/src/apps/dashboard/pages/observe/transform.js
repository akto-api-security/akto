import func from "@/util/func";
import { Badge, Box, Button, HorizontalStack, Icon, Text, Tooltip } from "@shopify/polaris";
import PersistStore from "../../../main/PersistStore";
import TooltipText from "../../components/shared/TooltipText";
import StyledEndpoint from "./api_collections/component/StyledEndpoint"
import CopyEndpoint from "./api_collections/component/CopyEndpoint"
import { SearchMinor, InfoMinor, LockMinor, ClockMinor, PasskeyMinor, LinkMinor, DynamicSourceMinor, GlobeMinor, LocationsMinor, PriceLookupMinor, ArrowUpMinor, ArrowDownMinor } from "@shopify/polaris-icons"
import api from "./api";
import GetPrettifyEndpoint from "./GetPrettifyEndpoint";
import ShowListInBadge from "../../components/shared/ShowListInBadge";
import { getDashboardCategory } from "../../../main/labelHelper";

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
        component: (data) => StyledEndpoint(data, "14px", "headingSm", true, true)
    },
    {
        text: 'Collection name',
        value: 'apiCollectionName',
        itemOrder: 3,
        icon: DynamicSourceMinor,
        iconTooltip: "Api collection name"
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
        itemOrder: 4,
        showFilter: true,
    },
    {
        text: 'hostname',
        itemOrder: 3,
        value: 'hostName',
        icon: GlobeMinor,
        iconTooltip: "Hostname of the api collection"
    },
    {
        text: 'Last Seen',
        value: 'last_seen',
        icon: SearchMinor,
        itemOrder: 3,
        iconTooltip: "Last seen traffic"
    },
    {
        text: 'Access Type',
        value: 'access_type',
        icon: InfoMinor,
        itemOrder: 3,
        showFilter: true, 
        iconTooltip: "Access type of the API"
    },
    {
        text: 'Auth Type',
        value: 'auth_type',
        icon: LockMinor,
        itemOrder: 3,
        showFilter: true,
        iconTooltip: "Auth type of the API"
    },
    {
        text: "Discovered",
        value: 'added',
        icon: ClockMinor,
        itemOrder: 3,
        iconTooltip: "Discovered time of API"
    },
    {
        text: 'Changes',
        value: 'changes',
        icon: InfoMinor,
        itemOrder: 3,
        iconTooltip: "Changes in API"
    },
    {
        text: "",
        value: "parameterisedEndpoint",
        itemOrder: 1,
        component: (data) => CopyEndpoint(data)
    },
    {
        text: 'Non-Sensitive Params',
        value: 'nonSensitiveTags',
        itemOrder: 4,
    },
    {
        text: 'Description',
        itemOrder: 2,
        value: 'description',
        alignVertical: "bottom",
        component: (data) => (<Button plain onClick={data?.action} textAlign="left">Add description</Button>),
        action: () => {}
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
            if(c === "") continue
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
            if(c.includes("x-akto-decode")){
                highlightPaths.push({
                    "highlightValue": {
                        "value": "x-akto-decode",
                        "wholeRow": true,
                        "className": "akto-decoded",
                        "highlight": true,
                    },
                    "responseCode": -1,
                    "header": 'x-akto-decode',
                    "param": "x-akto-decode",
                })
            }
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

            let detectDate = func.toYMD(new Date((e._id+1) * 86400 * 1000))
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
        sensitiveParams = sensitiveParams && sensitiveParams.reduce((z,e) => {
            let key = [e.apiCollectionId + "-" + e.url + "-" + e.method]
            z[key] = z[key] || new Set()
            z[key].add(e.subType || {"name": "CUSTOM"})
            return z
        },{})
        sensitiveParams && Object.entries(sensitiveParams).forEach(p => {
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
        const endpointComp = <GetPrettifyEndpoint method={x.method} url={x.url} isNew={false} />
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
        if(number === undefined){
            return 0;
        }
        const numberString = number.toString();
        return numberString.replace(/\B(?=(\d{3})+(?!\d))/g, ",");
    },
    getStandardHeaderList(){
        return standardHeaders
    },
    isValidString(string){
        try {
            const val = JSON.parse(string)
            return {type:true, val: val}
        } catch (error) {
            return {type:false, val: ""}
        }
    },
    getCommonSamples(nonSensitiveData, sensitiveData){
        let sensitiveSamples = this.prepareSampleData(sensitiveData, '')
        const samples = new Set()
        const highlightPathsObj ={}
        sensitiveSamples.forEach((x) => {
            const validObj = this.isValidString(x.message)
            if(validObj.type){
                samples.add(validObj.val)
                highlightPathsObj[validObj.val] = x.highlightPaths 
            }
            
        })

        let uniqueNonSensitive = []
        nonSensitiveData.forEach((x) => {
            try {
                let parsed = JSON.parse(x)
                if(samples.size === 0 || !samples.has(parsed)){
                    uniqueNonSensitive.push({message: x, highlightPaths: []})
                }
            } catch (e) {
            }
            
        })
        uniqueNonSensitive = uniqueNonSensitive.reverse();
        let finalArr = [...uniqueNonSensitive]
        if(samples.size > 0){
            finalArr = [...sensitiveSamples, ...finalArr]
        }
        return finalArr
    },

    convertSampleDataToSensitiveSampleData(samples, sensitiveInfo) {
        let sensitiveSampleData = {}
        samples.forEach(x => {
            sensitiveSampleData[x] = sensitiveInfo
        })
        return { sensitiveSampleData: sensitiveSampleData };
    },

    getColor(key, isSensitiveBadge=false){
        switch(key.toUpperCase()){
            case "CRITICAL": return "critical-strong-experimental"
            case "HIGH" : {
                if(isSensitiveBadge){
                    return "warning"
                }
                return "critical"
            }
            case "MEDIUM": return "attention"
            case "LOW": {
                if(isSensitiveBadge){
                    return "success"
                }
                return "info"
            }
            default:
                return "bg";
        }
    },

    getColorForSensitiveData(key){
        switch(key.toUpperCase()){
            case "CRITICAL": return "#E45357"
            case "HIGH" : return "#EF864C"
            case "MEDIUM": return "#F6C564"
            case "LOW": return "#6FD1A6"
            default:
                return "#6FD1A6";
        }
    },

    getColorForStatus(key){
        switch(key.toUpperCase()){
            case "ACTIVE": return "#EF864C"
            case "UNDER_REVIEW": return "#F6C564"
            case "IGNORED": return "#6FD1A6"
            case "TOTAL": return "#7F56D9"
            default:
                return "#6FD1A6";
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
        const sortedSeverityInfo = func.sortObjectBySeverity(severityInfo)
        return (
            <HorizontalStack gap="1" wrap={false}>
                {
                    Object.keys(sortedSeverityInfo).length > 0 ? Object.keys(sortedSeverityInfo).map((key,index)=>{
                        return(
                            <div className={`badge-wrapper-${key}`}>
                                <Badge size="small" key={index}>{Math.max(sortedSeverityInfo[key], 0).toString()}</Badge>
                            </div>
                        )
                    }):
                    <Text fontWeight="regular" variant="bodyMd" color="subdued">-</Text>
                }
            </HorizontalStack>
        )
    },

    getCollectionTypeList(envType, maxItems, wrap){
        if(envType == null || envType.length === 0){
            return <></>
        }

        // Sort tags to prioritize 'privatecloud.agoda.com/service' first
        const sortedEnvType = [...envType].sort((a, b) => {
            const aKey = a.split('=')[0];
            const bKey = b.split('=')[0];

            if (aKey === 'privatecloud.agoda.com/service') return -1;
            if (bKey === 'privatecloud.agoda.com/service') return 1;
            return 0;
        });

        return (
            <ShowListInBadge
                itemsArr={sortedEnvType}
                maxItems={maxItems}
                status={"info"}
                useTooltip={true}
                wrap={wrap}
                allowFullWidth={true}
            />
        )
    },

    getIssuesListText(severityInfo){
        const sortedSeverityInfo = func.sortObjectBySeverity(severityInfo)
        let val = "-"
        if(Object.keys(sortedSeverityInfo).length > 0){
            val = ""
            Object.keys(sortedSeverityInfo).map((key) => {
                val += (key + ": " + sortedSeverityInfo[key] + " ")
            })
        } 
        return val
    },

    prettifySubtypes(sensitiveTags, deactivated){
        return(
            <Box maxWidth="200px">
                <HorizontalStack gap={1} wrap={false}>
                    {sensitiveTags.map((item,index)=>{
                        return (index < 4 ? <Tooltip dismissOnMouseOut content={item} key={index + item}><Box>
                            <div className={deactivated ? "icon-deactivated" : ""}>
                                <Icon color={deactivated ? "" : "subdued"} source={func.getSensitiveIcons(item)} />
                            </div>
                        </Box></Tooltip> : null)
                    })}
                    {sensitiveTags.length > 4 ? <Badge size="small" status="warning" key={"more"}>{'+' + (sensitiveTags.length - 4).toString() + 'more'}</Badge> : null}
                </HorizontalStack>
            </Box>
        )
    },

    prettifyCollectionsData(newData, isLoading, selectedTab){
        const prettifyData = newData.map((c)=>{
            // Check if we're in the untracked tab
            const isUntrackedTab = selectedTab === 'untracked';

            // Calculate coverage - for untracked tab, leave it blank
            let calcCoverage = '0%';
            if(isUntrackedTab){
                calcCoverage = '';
            } else if(!c.isOutOfTestingScope){
                if(c.urlsCount > 0){
                    if(c.urlsCount < c.testedEndpoints){
                        calcCoverage= '100%'
                    }else{
                        calcCoverage =  Math.ceil((c.testedEndpoints * 100)/c.urlsCount) + '%'
                    }
                }
            }else{
                calcCoverage = 'N/A'
            }

            const loadingComp = <Text color="subdued" variant="bodyMd">...</Text>

            // Create displayNameComp if it doesn't exist (for lazy-loaded items)
            const displayNameComp = c.displayNameComp || (
                <HorizontalStack gap="2" align="start">
                    <Box maxWidth="30vw"><Text truncate fontWeight="medium">{c.displayName}</Text></Box>
                    {c.registryStatus === "available" && <Badge>Registry</Badge>}
                </HorizontalStack>
            );

            // Create descriptionComp if it doesn't exist
            const descriptionComp = c.descriptionComp || (<Box maxWidth="350px"><Text>{c.description}</Text></Box>);

            // Create outOfTestingScopeComp if it doesn't exist
            const outOfTestingScopeComp = c.outOfTestingScopeComp || (c.isOutOfTestingScope ? (<Text>Yes</Text>) : (<Text>No</Text>));

            // Risk score component - for untracked tab, show blank
            const riskScoreComp = isUntrackedTab
                ? <Text></Text>
                : (isLoading ? loadingComp : <Badge key={c?.id} status={this.getStatus(c.riskScore)} size="small">{c.riskScore}</Badge>);

            return{
                ...c,
                id: c.id,
                nextUrl: '/dashboard/observe/inventory/' + c.id,
                displayName: c.displayName,
                displayNameComp: displayNameComp,
                descriptionComp: descriptionComp,
                outOfTestingScopeComp: outOfTestingScopeComp,
                riskScoreComp: riskScoreComp,
                coverage: calcCoverage,
                issuesArr: isLoading ? loadingComp : this.getIssuesList(c.severityInfo),
                issuesArrVal: this.getIssuesListText(c.severityInfo),
                sensitiveSubTypes: isLoading ? loadingComp : this.prettifySubtypes(c.sensitiveInRespTypes, c.deactivated),
                lastTraffic: isLoading ? '...' : c.detected,
                riskScore: c.riskScore,
                deactivatedRiskScore: c.deactivated ? (c.riskScore - 10 ) : c.riskScore,
                activatedRiskScore: -1 * (c.deactivated ? c.riskScore : (c.riskScore - 10 )),
                envTypeComp: isLoading ? loadingComp : this.getCollectionTypeList(c.envType, 1, false),
                sensitiveSubTypesVal: c?.sensitiveInRespTypes.join(" ") ||  "-"
            }
        })


        return prettifyData
    },

    prettifyUntrackedCollectionsData(newData){
        const prettifyData = newData.map((c)=>{
            // Create displayNameComp if it doesn't exist (for lazy-loaded items)
            const displayNameComp = c.displayNameComp || (
                <HorizontalStack gap="2" align="start">
                    <Box maxWidth="30vw"><Text truncate fontWeight="medium">{c.displayName}</Text></Box>
                    {c.registryStatus === "available" && <Badge>Registry</Badge>}
                </HorizontalStack>
            );

            // For untracked tab, show empty/blank values for fields that don't apply
            return{
                ...c,
                id: c.id,
                name: c.name,
                nextUrl: null,
                displayName: c.displayName,
                displayNameComp: displayNameComp,
                descriptionComp: <Text></Text>, // Empty for untracked
                outOfTestingScopeComp: <Text></Text>, // Empty for untracked
                riskScoreComp: <Text></Text>, // Empty for untracked
                coverage: '', // Empty for untracked
                issuesArr: <Text></Text>, // Empty for untracked - no issues data
                issuesArrVal: '', // Empty for untracked
                sensitiveSubTypes: <Text></Text>, // Empty for untracked - no sensitive data
                sensitiveSubTypesVal: '', // Empty for untracked
                lastTraffic: '', // Empty for untracked
                discovered: '', // Empty for untracked
                riskScore: 0,
                deactivatedRiskScore: 0,
                activatedRiskScore: 0,
                envTypeComp: <Text></Text>, // Empty for untracked
                testedEndpoints: 0,
                collapsibleRow: c.collapsibleRow,
                collapsibleRowText: c.collapsibleRowText,
            }
        })

        return prettifyData
    },

    getSummaryData(collectionsData){
        let totalUrl = 0;
        let sensitiveInRes = 0;
        let totalTested = 0 ;
        let totalAllowedForTesting = 0;

        collectionsData?.forEach((c) =>{
            if (c.hasOwnProperty('type') && c.type === 'API_GROUP') {
                return
            }
            if (c.deactivated) {
                return
            }
            totalUrl += c.urlsCount ;
            if(!c.isOutOfTestingScope){
                totalTested += c.testedEndpoints;
                totalAllowedForTesting += c.urlsCount;
                return
            }
        })

        return {
            totalEndpoints:totalUrl , totalTestedEndpoints: totalTested, totalSensitiveEndpoints: sensitiveInRes, totalAllowedForTesting: totalAllowedForTesting,
        }
    },

    getTruncatedUrl(url){
        const category = getDashboardCategory();
        let parsedURL = url;
        let pathUrl = url;
        try {
            parsedURL = new URL(url)
            pathUrl = parsedURL.pathname.replace(/%7B/g, '{').replace(/%7D/g, '}');
        } catch (error) {
            
        }
        if(category.includes("MCP") || category.includes("Agentic")){
            try {
                const [path, tail = ""] = pathUrl.split(/(?=[?#])/); // keep ? or # in tail
                const newPath = path
                  .replace(/^.*?\/calls?(?:\/|$)/i, "/")       // keep only what's after /call or /calls
                  .replace(/\/{2,}/g, "/");                  // collapse slashes
                return (newPath.endsWith("/") && newPath !== "/")
                  ? newPath.slice(0, -1) + tail
                  : newPath + tail;
            } catch (error) {
                return url;
            }
            
        }
        return pathUrl;
    },

    getHostName(url){
        try {
            const parsedURL = new URL(url)
            return parsedURL.hostname
        } catch (error) {
            return "No host"
        }
    },

    isNewEndpoint(lastSeen){
        if(lastSeen === undefined || lastSeen <= 0){
            return false
        }
        let lastMonthEpoch = func.timeNow() - (30 * 24 * 60 * 60);
        return lastSeen > lastMonthEpoch
    },

    prettifyEndpointsData(inventoryData){
        const hostNameMap = PersistStore.getState().hostNameMap
        const prettifyData = inventoryData.map((url) => {
            let lastTestedText = "";
            if(url?.lastTested === undefined || url?.lastTested <= 0){
                lastTestedText = "Never"
            }else{
                lastTestedText = func.prettifyEpoch(url?.lastTested)
            }
            return{
                ...url,
                last_seen: url.last_seen,
                hostName: (hostNameMap[url.apiCollectionId] !== null ? hostNameMap[url.apiCollectionId] : this.getHostName(url.endpoint)),
                access_type: url.access_type,
                auth_type: url.auth_type,
                endpointComp: <GetPrettifyEndpoint method={url.method} url={url.endpoint} isNew={this.isNewEndpoint(url.lastSeenTs)} />,
                sensitiveTagsComp: this.prettifySubtypes(url.sensitiveTags),
                riskScoreComp: <Badge status={this.getStatus(url.riskScore)} size="small">{url?.riskScore.toString()}</Badge>,
                isNew: this.isNewEndpoint(url.lastSeenTs),
                sensitiveDataTags: url?.sensitiveTags.join(" "),
                codeAnalysisEndpoint: false,
                issuesComp: url.severityObj? this.getIssuesList(url.severityObj):'-',
                severity: url.severityObj? Object.keys(url.severityObj):[],
                description: url.description,
                lastTestedComp: <Text variant="bodyMd" fontWeight={this.isNewEndpoint(url?.lastTested) ? "regular" : "semibold"} color={this.isNewEndpoint(url?.lastTested) ? "" : "subdued"}>{lastTestedText}</Text>,
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
                try {
                    await api.getRiskScoreInfo().then((res) =>{
                        const newObj = {
                            criticalUrls: res.criticalEndpointsCount,
                            riskScoreMap: res.riskScoreOfCollectionsMap, 
                        }
                        tempRiskScoreObj = JSON.parse(JSON.stringify(newObj));
                        setLastFetchedResp(newObj);
                    })
                } catch (error) {
                    func.setToast(true, false, error.message)
                }
                
            }
            if(resp.lastUpdatedSeverity >= lastFetchedInfo.lastRiskScoreInfo){
                try {
                    await api.getSeverityInfoForCollections().then((resp) => {
                        tempSeverityObj = JSON.parse(JSON.stringify(resp))
                        setLastFetchedSeverityResp(resp)
                    })
                } catch (error) {
                    func.setToast(true, false, error.message)
                }
                
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
            try {
                await api.getSensitiveInfoForCollections().then((resp) => {
                    const sensitiveObj = {
                        sensitiveUrls: resp.sensitiveUrlsInResponse,
                        sensitiveInfoMap: resp.sensitiveSubtypesInCollection
                    }
                    setLastCalledSensitiveInfo(func.timeNow())
                    setLastFetchedSensitiveResp(sensitiveObj)
                    tempSensitiveInfo = JSON.parse(JSON.stringify(sensitiveObj))
                })
            } catch (error) {
                return tempSensitiveInfo
            }
            
        }
        return tempSensitiveInfo; 
    },

    convertToPrettifyData(c){
        return{
            riskScoreComp:<Badge key={c.level} status={this.getStatus(c.riskScore)} size="small">{c.riskScore}</Badge>,
            coverage: c.urlsCount !== 0 ? Math.min( Math.floor((c.testedEndpoints * 100)/c.urlsCount), 100) + "%": '0%',
            issuesArr: this.getIssuesList(c.severityInfo),
            sensitiveSubTypes: this.prettifySubtypes(c?.sensitiveInRespTypes || []),
            lastTraffic: func.prettifyEpoch(c.detectedTimestamp),
            discovered: func.prettifyEpoch(c.startTs),
        }
    },
    generateByLineComponent: (val, time) => {
        if (!val || isNaN(val)) return null
        if (val === 0 ) {
            return <Text>No change in {time}</Text>
        }
        const source = val > 0 ? ArrowUpMinor : ArrowDownMinor
        return (
            <HorizontalStack gap={1}>
                <Box>
                    <Icon source={source} color='subdued' />
                </Box>
                <Text color='subdued' fontWeight='medium'>{Math.abs(val)}</Text>
                <Text color='subdued' fontWeight='semibold'>{time}</Text>
            </HorizontalStack>
        )
    },
    getCumulativeData: (data) => {
        let cumulative = []
        data.reduce((acc, val, i) => cumulative[i] = acc + val, 0)
        return cumulative
    },
    setIssuesState: (data, setState, setDelta, useCumulative) => {
        if(useCumulative) {
            data = transform.getCumulativeData(data)
        }

        if (data == null || data.length === 0) {
            setState([0, 0])
            setDelta(0)
        } else if (data.length === 1) {
            setState([0, data[0]])
            setDelta(data[0])
        } else {
            setState(data)
            
            setDelta(data[data.length-1] - data[0])
        }
    },

    getUntrackedApisCollapsibleRow: (untrackedApis) => {
        return (
            <tr style={{padding: '0px !important', borderTop: '1px solid #dde0e4'}}>
                <td colSpan={8} style={{padding: '0px !important', width: '100%'}}>
                    {untrackedApis.map((api, index) => {
                        const borderStyle = index < (untrackedApis.length - 1) ? {borderBlockEndWidth: 1} : {}
                        return (
                            <Box
                                padding={"2"}
                                paddingInlineStart={"4"}
                                key={index}
                                borderColor="border-subdued"
                                {...borderStyle}
                                width="100%"
                            >
                                <div style={{ display: "flex", alignItems: "center", width: "100%" }}>
                                    <HorizontalStack gap="2" align="start" blockAlign="center">
                                        <GetPrettifyEndpoint 
                                            method={api.method} 
                                            url={api.url} 
                                            isNew={false}
                                        />
                                    </HorizontalStack>
                                    <div style={{ marginLeft: "auto" }}>
                                        <Text color="subdued" fontWeight="semibold">
                                            {api.urlType || "STATIC"}
                                        </Text>
                                    </div>
                                </div>
                            </Box>
                        )
                    })}
                </td>
            </tr>
        )
    }
}

export default transform
