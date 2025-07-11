import func from "@/util/func"
import {Tooltip, Box, Icon, Text, HorizontalStack} from "@shopify/polaris"
import {FolderMajor} from "@shopify/polaris-icons"

const convertFunc = {
    mapCategoryToSubcategory: function (categoriesArr) {

        let aktoTests = {}
        let customTests = {}

        let totalCustomTests = 0
        let totalAktoTests = 0

        let mapTestToData = {}
        let mapIdtoTest = {}

        categoriesArr.forEach(test => {
            let obj = {
                label: test.testName,
                value: test.name,
                category: test.superCategory.displayName,
                inactive: test.inactive
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
            let dataObj = {
                content: test?.content||"",
                lastUpdated: func.prettifyEpoch(test.updatedTs),
                superCategory: test.superCategory.name,
                type: test.templateSource._name,
                category: test.superCategory.displayName,
                inactive: test.inactive,
                severity: test?.superCategory?.severity?._name || "",
                nature : test?.attributes?.nature?._name || "",
                duration: test?.attributes?.duration?._name || "",
                value: test.name,
                isCustom: test?.templateSource?._name === "CUSTOM" || false,
                author: test?.author || "",
                compliance: Object.keys(test?.compliance?.mapComplianceToListClauses || {}),
            }

            mapTestToData[test.testName] = dataObj
            mapIdtoTest[test.name] = test.testName
        });

        let resultObj = {
            aktoTests,customTests,totalAktoTests,totalCustomTests,mapTestToData,mapIdtoTest
        }

        return resultObj
    },

    getNavigationItems(testObj,param,selectedFunc){
        let arr = []
        let count = 0;
        if(param === 'CUSTOM'){
            for(const key in testObj?.customTests){
                if(testObj.customTests.hasOwnProperty(key)){
                    let item = {
                        label: (
                            <Tooltip content={testObj?.customTests[key][0]?.category} dismissOnMouseOut width="wide">
                                <HorizontalStack gap="2">
                                    <Box>
                                        <Icon source={FolderMajor} color="base"/>
                                    </Box>
                                    <Text truncate variant="bodyMd" as="h5">
                                        {testObj?.customTests[key][0]?.category}
                                    </Text>
                                </HorizontalStack>
                            </Tooltip>
                        ),
                        badge: testObj?.customTests[key]?.length.toString(),
                        url: '#',
                        onClick: (()=> selectedFunc(key+'_custom')),
                        subNavigationItems: testObj?.customTests[key],
                        key: key,
                        param: '_custom',
                        truncateText: true,
                    }
                    if(item.subNavigationItems.length > 0){
                        arr.push(item)
                    }
                }
            }
            count = testObj?.totalCustomTests;
        }else{
            for(const key in testObj?.aktoTests){
                if(testObj.aktoTests.hasOwnProperty(key)){
                    let item = {
                        label: (
                            <Tooltip content={testObj?.aktoTests[key][0]?.category} dismissOnMouseOut width="wide">
                                <HorizontalStack gap="2">
                                    <Box>
                                        <Icon source={FolderMajor} color="base"/>
                                    </Box>
                                    <Text truncate variant="bodyMd" as="h5">
                                      {testObj?.aktoTests[key][0]?.category}
                                   </Text>
                                </HorizontalStack>
                            </Tooltip>
                        ),
                        badge: testObj.aktoTests[key]?.length.toString(),
                        url: '#',
                        onClick: (()=> selectedFunc(key+'_akto')),
                        subNavigationItems: testObj?.aktoTests[key],
                        key: key,
                        param: '_akto',
                        truncateText: true,
                    }
                    if(item.subNavigationItems.length > 0){
                        arr.push(item)
                    }
                }
            }
            count = testObj?.totalAktoTests;
        }
        return {items: arr, count: count}
    },

    mapVulnerableRequests(vulnerableRequests){
        let mapRequestsToId = {}
        vulnerableRequests?.length > 0 && vulnerableRequests.forEach((x)=>{
            let methodArr = x.templateIds
            methodArr.forEach((method) => {
                if (!mapRequestsToId[method]) {
                    mapRequestsToId[method] = {}
                }

                mapRequestsToId[method] = x.id
            })
        })

        return mapRequestsToId
    }

}

export default convertFunc