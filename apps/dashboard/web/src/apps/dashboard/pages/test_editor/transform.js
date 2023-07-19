import func from "@/util/func"
import {Tooltip, Box, Icon} from "@shopify/polaris"
import {FolderMajor} from "@shopify/polaris-icons"

const convertFunc = {
    mapCategoryToSubcategory: function (categoriesArr) {

        let aktoTests = {}
        let customTests = {}

        let totalCustomTests = 0
        let totalAktoTests = 0

        let mapTestToContent = {}
        let mapTestToLastUpdated = {}

        categoriesArr.forEach(test => {
            let obj = {
                label: test.testName,
                value: test.name,
                category: test.superCategory.displayName
            }
            if(test.templateSource._name === "CUSTOM"){
                if(!customTests[test.superCategory.name]){
                    customTests[test.superCategory.name] = []
                }
                customTests[test.superCategory.name].push(obj)
                totalCustomTests++
            }else{
                if(!aktoTests[test.superCategory.name]){
                    aktoTests[test.superCategory.name] = []
                }
                aktoTests[test.superCategory.name].push(obj)
                totalAktoTests++
            }

            mapTestToContent[test.testName] = test.content
            mapTestToLastUpdated[test.testName] = func.prettifyEpoch(test.updatedTs)
        });

        let resultObj = {
            aktoTests,customTests,totalAktoTests,totalCustomTests,mapTestToContent,mapTestToLastUpdated
        }

        return resultObj
    },

    getNavigationItems(testObj,param,selectedFunc){
        let arr = []
        if(param === 'CUSTOM'){
            for(const key in testObj?.customTests){
                if(testObj.customTests.hasOwnProperty(key)){
                    let item = {
                        label: (
                            <Tooltip content={testObj?.customTests[key][0]?.category} dismissOnMouseOut width="wide">
                                <span className="text-overflow" style={{'fontSize': '14px', 'gap': '6px'}}>
                                    <Box>
                                        <Icon source={FolderMajor} color="base"/>
                                    </Box>
                                    {testObj?.customTests[key][0]?.category}
                                </span>
                            </Tooltip>
                        ),
                        badge: testObj?.customTests[key]?.length.toString(),
                        url: '#',
                        onClick: (()=> selectedFunc(key+'_custom')),
                        subNavigationItems: testObj?.customTests[key],
                        key: key,
                        param: '_custom'
                    }
                    if(item.subNavigationItems.length > 0){
                        arr.push(item)
                    }
                }
            }
        }else{
            for(const key in testObj?.aktoTests){
                if(testObj.aktoTests.hasOwnProperty(key)){
                    let item = {
                        label: (
                            <Tooltip content={testObj?.aktoTests[key][0]?.category} dismissOnMouseOut width="wide">
                                <span className="text-overflow" style={{'fontSize': '14px', gap:'6px'}}>
                                    <Box>
                                        <Icon source={FolderMajor} color="base"/>
                                    </Box>
                                    {testObj?.aktoTests[key][0]?.category}
                                </span>
                            </Tooltip>
                        ),
                        badge: testObj.aktoTests[key]?.length.toString(),
                        url: '#',
                        onClick: (()=> selectedFunc(key+'_akto')),
                        subNavigationItems: testObj?.aktoTests[key],
                        key: key,
                        param: '_akto',
                    }
                    if(item.subNavigationItems.length > 0){
                        arr.push(item)
                    }
                }
            }
        }
        return arr
    }
}

export default convertFunc