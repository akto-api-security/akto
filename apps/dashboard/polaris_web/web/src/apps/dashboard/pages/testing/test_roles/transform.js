
const transform = {
    filterContainsConditions(conditions, operator) { //operator is string as 'OR' or 'AND'
        let filteredCondition = {}
        let found = false
        filteredCondition['operator'] = operator
        filteredCondition['predicates'] = []
        conditions.forEach(element => {
            if (element.value && element.operator === operator) {
                if (element.type === 'CONTAINS') {
                    filteredCondition['predicates'].push({ type: element.type, value: element.value })
                    found = true
                } else if (element.type === 'BELONGS_TO' || element.type === 'NOT_BELONGS_TO') {
                    let collectionMap = element.value
                    let collectionId = Object.keys(collectionMap)[0]
    
                    if (collectionMap[collectionId]) {
                        let apiKeyInfoList = []
                        collectionMap[collectionId].forEach(apiKeyInfo => {
                            apiKeyInfoList.push({ 'url': apiKeyInfo['url'], 'method': apiKeyInfo['method'], 'apiCollectionId': Number(collectionId) })
                            found = true
                        })
                        if (apiKeyInfoList.length > 0) {
                            filteredCondition['predicates'].push({ type: element.type, value: apiKeyInfoList })
                        }
                    }
                }
            }
        });
        if (found) {
            return filteredCondition;
        }
    },
    
    fillConditions(conditions, predicates, operator) {
        predicates.forEach(async (e, i) => {
            let valueFromPredicate = e.value
            if (Array.isArray(valueFromPredicate) && valueFromPredicate.length > 0) {
                let valueForCondition = {}
                let collectionId = valueFromPredicate[0]['apiCollectionId']
                let apiInfoKeyList = []
                for (var index = 0; index < valueFromPredicate.length; index++) {
                    let apiEndpoint = {
                        method: valueFromPredicate[index]['method'],
                        url: valueFromPredicate[index]['url']
                    }
                    apiInfoKeyList.push({
                            method: apiEndpoint.method,
                            url: apiEndpoint.url
                    })
                }
                valueForCondition[collectionId] = apiInfoKeyList
                conditions.push({ operator: operator, type: e.type, value: valueForCondition })
            } else {
                conditions.push({ operator: operator, type: e.type, value: valueFromPredicate })
            }
        })
    },
    
    createConditions(data){
        let testingEndpoint = data
            let conditions = []
            if (testingEndpoint?.andConditions) {
                transform.fillConditions(conditions, testingEndpoint.andConditions.predicates, 'AND')
            }
            if (testingEndpoint?.orConditions) {
                transform.fillConditions(conditions, testingEndpoint.orConditions.predicates, 'OR')
            }
            return conditions;
    },
}

export default transform