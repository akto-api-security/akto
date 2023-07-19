import func from "@/util/func"

let subCategoryMap = {}
let subCategoryFromSourceConfigMap = {}
let apiCollectionMap = {}

function getCategoryName(id) {
    if (subCategoryMap[id.testSubCategory] === undefined) {//Config case
        let a = subCategoryFromSourceConfigMap[id.testCategoryFromSourceConfig]
        return a ?  a.subcategory : null;
    }
    return subCategoryMap[id.testSubCategory].testName;
}

function getCategoryDescription(id) {
    if (subCategoryMap[id.testSubCategory] === undefined) {//Config case
        let a = subCategoryFromSourceConfigMap[id.testCategoryFromSourceConfig]
        return a ? a.description : null;
    }
    return subCategoryMap[id.testSubCategory].issueDescription
}

function getTestType(name) {
    switch (name) {
        case 'AUTOMATED_TESTING':
            return 'testing';
        case 'RUNTIME':
            return 'runtime'
        default:
            return ''
    }
}

function getCreationTime(creationTime) {
    let epoch = Date.now();
    let difference = epoch / 1000 - creationTime;
    let numberOfDays = difference / (60 * 60 * 24)
    if (numberOfDays < 31) {
        return parseInt(numberOfDays) + ' d';
    } else if (numberOfDays < 366) {
        return parseInt(numberOfDays / 31) + ' m' + parseInt(numberOfDays % 31) + ' d';
    } else {
        return parseInt(numberOfDays / 365) + ' y' + parseInt((numberOfDays % 365) / 31) + ' m'
            + parseInt((numberOfDays % 365) % 31) + ' d';
    }
}

const transform = {
    prepareIssues: (res, localSubCategoryMap, localSubCategoryFromSourceConfigMap, localApiCollectionMap) => {
        let ret=[]
        subCategoryMap = localSubCategoryMap
        subCategoryFromSourceConfigMap = localSubCategoryFromSourceConfigMap
        apiCollectionMap = localApiCollectionMap
        res.issues.forEach((issue, index) => {
            let temp = {}
            temp.hexId = index
            temp.method = issue.id.apiInfoKey.method;
            temp.endpoint = issue.id.apiInfoKey.url;
            temp.url = temp.method + " " + temp.endpoint;
            temp.severity=[func.toSentenceCase(issue.severity)]
            temp.timestamp=issue.creationTime
            temp.detected_timestamp= "Discovered " + func.prettifyEpoch(temp.timestamp)
            temp.collection=apiCollectionMap[issue.id.apiInfoKey.apiCollectionId]
            temp.apiCollectionId=issue.id.apiInfoKey.apiCollectionId
            temp.categoryName=getCategoryName(issue.id)
            temp.categoryDescription=getCategoryDescription(issue.id)
            temp.testType=getTestType(issue.id.testErrorSource)
            temp.issueId=issue.id
            temp.issueStatus=issue.testRunIssueStatus
            temp.ignoreReason=issue.ignoreReason
            ret.push(temp);
        })
        return ret;
    }
}

export default transform