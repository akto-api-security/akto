import { DynamicSourceMinor , MagicMinor, PasskeyMinor } from "@shopify/polaris-icons"

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
        return {
            request: JSON.parse(json.requestPayload),
            response: JSON.parse(json.responsePayload)
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
        return parameterPrompts
    },

    getPrompts: function(requestObj) {
        switch(requestObj.key){
            case "DATA_TYPES":
                return dataTypesPrompt

            case "COLLECTION":
                return this.getCollectionsPrompts(requestObj.filteredItems,requestObj.apiCollectionId)

            case "PARAMETER":
                return this.getParameterPrompts(requestObj.jsonStr, requestObj.apiCollectionId)

            default :
                return []
        }
    }
}

export default dashboardFunc