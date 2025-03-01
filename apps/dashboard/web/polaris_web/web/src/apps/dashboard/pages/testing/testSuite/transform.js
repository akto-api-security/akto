
import LocalStore from "../../../../main/LocalStorageStore";
const transform  = {
    getSubCategoryMap: async ()=>{
        let metaDataObj = {
            categories: [],
            subCategories: [],
            testSourceConfigs: []
        }
        if ((LocalStore.getState().subCategoryMap && Object.keys(LocalStore.getState().subCategoryMap).length > 0)) {
            metaDataObj = {
                subCategories: Object.values(LocalStore.getState().subCategoryMap),
            }

        } else {
           return await transform.getSubCategoryMap();
        }
        const subCategoryMap = {};

        metaDataObj.subCategories.forEach(subCategory => {
            if (!subCategoryMap[subCategory?.superCategory?.name]) {
                subCategoryMap[subCategory.superCategory?.name] = [];
            }
            let obj = {
                label: subCategory.testName,
                value: subCategory.name,
                author: subCategory.author,
                categoryName: subCategory.superCategory.displayName,
                selected: true
            }
            subCategoryMap[subCategory.superCategory?.name].push(obj);
        });

        return subCategoryMap;
    }
}

export default transform;