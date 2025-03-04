import LocalStore from "../../../main/LocalStorageStore";

const threatDetectionFunc = {
    getGraphsData : (resp) => {
        const subCategoryMap = LocalStore.getState().subCategoryMap;
        const categoryMap = LocalStore.getState().categoryMap;
        if (resp?.categoryCounts) {
            const categoryRes = {};
            const subCategoryRes = {};
            for (const cc of resp.categoryCounts) {
              if (categoryRes[cc.category]) {
                categoryRes[cc.category] += cc.count;
              } else {
                categoryRes[cc.category] = cc.count;
              }
    
              if (subCategoryRes[cc.subCategory]) {
                subCategoryRes[cc.subCategory] += cc.count;
              } else {
                subCategoryRes[cc.subCategory] = cc.count;
              }
            }
    
            const subcategoryCountRes = Object.keys(subCategoryRes).map((x) => {
                let temp_name = x === "BUA" ? "NO_AUTH": x;
                var name = subCategoryMap[x] ? subCategoryMap[x].testName : categoryMap[temp_name]?.displayName || ""
                var usedName = name.length > 0 ? name : x.replaceAll("_", " ")
                return {
                  text: usedName,
                  value: subCategoryRes[x],
                  color: "#A5B4FC",
                };
            })
    
            const categoryCountRes = Object.keys(categoryRes).map((x) => {
                let temp_name = x === "BUA" ? "NO_AUTH": x;
                var name = categoryMap[temp_name]?.displayName || ""
                var usedName = name.length > 0 ? name : x.replaceAll("_", " ")
                return {
                  text: usedName,
                  value: categoryRes[x],
                  color: "#A5B4FC",
                };
            })
            return {
                subCategoryCount: subcategoryCountRes,
                categoryCountRes: categoryCountRes,
            }
        }else{
            return {
                subCategoryCount: [],
                categoryCountRes: [],
            }
        }
    }
}

export default threatDetectionFunc;