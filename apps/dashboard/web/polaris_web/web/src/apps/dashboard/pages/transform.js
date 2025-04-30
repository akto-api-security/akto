import { DynamicSourceMinor , MagicMinor, PasskeyMinor } from "@shopify/polaris-icons"
import func from "@/util/func"

const dataTypesPrompt = [
    {
      icon: DynamicSourceMinor,
      label: "Write regex to find ${input}",
      prepareQuery: (filterApi) => { return {
          type: "generate_regex",
          meta: {
              "input_query": filterApi
          }                        
      }},
      callback: (data) => console.log("callback Tell me all the apis", data)
    }
]

const dashboardFunc = {

    getCollectionsPrompts : function(filteredItems,apiCollectionId){
        const collectionsGptPrompts = [
            {
                icon: MagicMinor,
                label: "Find auth-related tokens",
                prepareQuery: () => { return {
                    type: "find_auth_related_tokens",
                    meta: {
                        "apiCollectionId": apiCollectionId
                    }                        
                }},
                callback: (data) => console.log("callback Find auth-related tokens", data)
            },
            {
                icon: MagicMinor,
                label: "Create API groups",
                prepareQuery: () => { return {
                    type: "group_apis_by_functionality",
                    meta: {
                        "urls": filteredItems.map(x => x.endpoint),
                        "apiCollectionId": apiCollectionId
                    }                        
                }},
                callback: (data) => console.log("callback create api groups", data)
            },
            {
                icon: DynamicSourceMinor,
                label: "Tell me APIs related to ${input}",
                prepareQuery: (filterApi) => { return {
                    type: "list_apis_by_type",
                    meta: {
                        "urls": filteredItems.map(x => x.endpoint),
                        "type_of_apis": filterApi,
                        "apiCollectionId": apiCollectionId
                    }                        
                }},
                callback: (data) => console.log("callback Tell me all the apis", data)
            }
        ]
        return collectionsGptPrompts
    },

    parseMsgForGenerateCurl: function(jsonStr) {
        let json = JSON.parse(jsonStr)
        let responsePayload = {}
        let responseHeaders = {}
        let statusCode = 0

        if (json) {
            responsePayload = json["response"] ?  json["response"]["body"] : json["responsePayload"]
            responseHeaders = json["response"] ?  json["response"]["headers"] : json["responseHeaders"]
            statusCode = json["response"] ?  json["response"]["statusCode"] : json["statusCode"]
        }

        return {
            "responsePayload": responsePayload,
            "responseHeaders": responseHeaders,
            "statusCode": statusCode
        };
    },

    parseMsg: function(jsonStr) {
        let json = JSON.parse(jsonStr)
        let reqStr = json.requestPayload.length > 0 ? json.requestPayload : "{}"
        let resStr = json.responsePayload.length > 0 ? json.responsePayload : "{}"
        return {
            request: JSON.parse(reqStr),
            response: JSON.parse(resStr)
        }
    },

    getParameterPrompts: function(jsonStr,apiCollectionId){
        const parameterPrompts= [
            {
                icon: PasskeyMinor,
                label: "Fetch Sensitive Params",
                prepareQuery: () => { return {
                    type: "list_sensitive_params",
                    meta: {
                        "sampleData": this.parseMsg(jsonStr),
                        "apiCollectionId": apiCollectionId
                    }                        
                }},
                callback: (data) => console.log("callback create api groups", data)
            },
            {
                icon: PasskeyMinor,
                label: "Generate curl for testing SSRF vulnerability",
                prepareQuery: () => { return {
                    type: "generate_curl_for_test",
                    meta: {
                        "sample_data": jsonStr,
                        "response_details": this.parseMsgForGenerateCurl(jsonStr),
                        "test_type": "ssrf",
                        "apiCollectionId": apiCollectionId
                    }                        
                }},
                callback: (data) => console.log("callback create api groups", data)
            },
            {
                icon: PasskeyMinor,
                label: "Generate curl for testing SQLI vulnerability",
                prepareQuery: () => { return {
                    type: "generate_curl_for_test",
                    meta: {
                        "sample_data": jsonStr,
                        "response_details": this.parseMsgForGenerateCurl(jsonStr),
                        "test_type": "sqlinjection",
                        "apiCollectionId": apiCollectionId
                    }                        
                }},
                callback: (data) => console.log("callback create api groups", data)
            },
            {
                icon: PasskeyMinor,
                label: "Suggest API Security tests for this API",
                prepareQuery: () => { return {
                    type: "suggest_tests",
                    meta: {
                        "sample_data": jsonStr,
                        "response_details": this.parseMsgForGenerateCurl(jsonStr),
                        "apiCollectionId": apiCollectionId
                    }                        
                }},
                callback: (data) => console.log("callback create api groups", data)
            }
        ]

        let tempArr = parameterPrompts
        if(jsonStr?.length === 0){
            tempArr.slice(1)
            return tempArr
        }

        let json = JSON.parse(jsonStr)
        let type = ""
        let payload = ""

        if(json.contentType){type = json.contentType.toString()}
        if(json.requestPayload){payload = json.responsePayload.toString()}

        const pattern = /^\{.*\}$/;
        if(!(type.indexOf('application/json') !== -1 ||  pattern.test(payload))){
            tempArr = tempArr.slice(1)
        }
        return tempArr
    },

    getApiPrompts: function(apiCollectionId, url, method){ 
        return [{
            prepareQuery: () => { return {
                type: "analyze_request_response_headers",
                label: "Analyze request/response headers for the protocols and api gateways",
                meta: {
                    "url": url,
                    "method": method,
                    "apiCollectionId": apiCollectionId
                }                        
            }}}
        ]
    },

    getPrompts: function(requestObj) {
        switch(requestObj.key){
            case "DATA_TYPES":
                return dataTypesPrompt

            case "COLLECTION":
                return this.getCollectionsPrompts(requestObj.filteredItems,requestObj.apiCollectionId)

            case "PARAMETER":
                return this.getParameterPrompts(requestObj.jsonStr, requestObj.apiCollectionId)

            case "API":
                return this.getApiPrompts(requestObj.apiCollectionId, requestObj.url, requestObj.method)

            default :
                return []
        }
    },

    getAlertMessageFromType(alertType){
        switch (alertType){
            case "TRAFFIC_STOPPED":
                return "Can't capture traffic";
            case "TRAFFIC_OVERLOADED":
                return "Latency captured, traffic rate high";
            case "CYBORG_STOPPED_RECEIVING_TRAFFIC":
                return "Akto not receiving traffic";
            default:
                return "Unknown error occurred"
        }
    },

    replaceEpochWithFormattedDate(input) {
        const match = input.match(/\$\{(\d+)\}/);

        if (match && match[1]) {
            const epochTime = Number(match[1]);
            const date = new Date(epochTime * 1000);
            const formattedDate = date.toLocaleString('en-US',{timeZone: window.TIME_ZONE === 'Us/Pacific' ? 'America/Los_Angeles' : window.TIME_ZONE});
            const result = input.replace(match[0], formattedDate);
            return result;
        } else {
            return input;
        }
    },

    getBannerStatus(key){
        switch(key.toUpperCase()){
            case "HIGH" : return "critical";
            case "MEDIUM": return "warning";
            case "LOW": return "info";
            default:
                return "bg";
        }
    },
    sortAndFilterAlerts(alerts) {
        const severityOrder = { 'CRITICAL': 4, 'HIGH': 3, 'MEDIUM': 2, 'LOW': 1 };
        const dismissLimit =  60 * 60;
        const currentTime = func.timeNow()
    
        const filteredAlerts = alerts.filter(alert => 
            currentTime - (alert?.lastDismissed || 0) > dismissLimit
        );
    
        filteredAlerts.sort((a, b) => severityOrder[b.severity] - severityOrder[a.severity]);
    
        return filteredAlerts.slice(0, Math.min(3, filteredAlerts.length));
    }
}

export default dashboardFunc