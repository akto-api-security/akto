import func from "@/util/func"

const onFunc = {
    getSeverityObj : function(testResults){
        if(testResults && testResults.length > 0){
            const groupedResults = testResults.reduce((result, item) => {
                    const { severity } = item;
                    if (!result[severity]) {
                        result[severity] = { truncate: false, items: [] };
                    }
                    
                    if (result[severity].items.length < 4) {
                        result[severity].items.push(item);
                    } else {
                        result[severity].truncate = true;
                    }
                    return result;
                },{});

            return groupedResults
        }
        return {
            "High": [],
            "Medium":[],
            "Low": [],
        }
    },

    getStatus : function(severity){
        switch(severity){
            case "High":
                return "critical"

            case "Medium":
                return "warning"

            default:
                return "attention"
        }
    },

    getConvertedTestResults: function(testResults,subCategoryMap,subCategoryFromSourceConfigMap){
        let tempArr = []
        testResults.forEach(x => {
            if(x.vulnerable){
                tempArr.push(
                    {
                        method: x['apiInfoKey']['method'],
                        path: func.convertToRelativePath(x["apiInfoKey"]["url"]),
                        severity: "High",
                        vulnerability: func.getRunResultCategory(x, subCategoryMap, subCategoryFromSourceConfigMap, "displayName"),
                        testName: func.getRunResultSubCategory (x, subCategoryFromSourceConfigMap, subCategoryMap, "issueDescription")
                    }
                )
            }
        });
        return tempArr
    }
}

export default onFunc