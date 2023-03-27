export default {
    getCollectionName: (testingEndpoints, mapCollectionIdToName) => {
        let apiCollectionId = 0
        switch (testingEndpoints.type) {
            case "CUSTOM":
                apiCollectionId = testingEndpoints.apisList[0].apiCollectionId
                break;
            case "COLLECTION_WISE":
                apiCollectionId = testingEndpoints.apiCollectionId  
                break;
            case "WORKFLOW": 
                apiCollectionId = testingEndpoints.workflowTest.apiCollectionId
                break;
            case "FILTER_BASED":
                for (let k in testingEndpoints.endpointDataQuery.filterConditions) {
                    let val = testingEndpoints.endpointDataQuery.filterConditions[k]
                    if (val.key == "apiCollectionId"){
                        apiCollectionId = val.values[0]
                    }
                }
        }

        return mapCollectionIdToName[apiCollectionId]
    },
    getEndpoints: (testingEndpoints) => {
        switch (testingEndpoints.type) {
            case "CUSTOM":
                return testingEndpoints.apisList.length
            case "COLLECTION_WISE":
                return "all"
            case "WORKFLOW": 
                return "-"
        }
    }
}