import func from "@/util/func"

const onFunc = {
    getSeverityObj : function(testResults){
        if(testResults && testResults.length > 0){
            const groupedResults = testResults.reduce((result, item) => {
                    const { severity } = item;
                    if (!result[severity]) {
                        result[severity] = { truncate: false, items: [] };
                    }
                    
                    if (result[severity].items.length < 3) {
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
    },

    getTextColor: function(method){
        switch (method) {
            case "GET": return `var(--color-get)`;
            case "POST": return `var(--color-post)`;
            case "PUT": return `var(--color-put)`;
            case "PATCH": return `var(--color-patch)`;
            case "DELETE": return `var(--color-delete)`;
            case "OPTIONS": return `var(--color-options)`;
            case "HEAD": return `var(--color-head)`;
            case "TOOL": return `var(--color-tool)`;
            case "RESOURCE": return `var(--color-resource)`;
            case "PROMPT": return `var(--color-prompt)`;
            case "SERVER": return `var(--color-server)`;
            default:
                return "";
        }
    }
}

export default onFunc