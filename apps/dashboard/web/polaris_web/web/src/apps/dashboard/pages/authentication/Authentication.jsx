import PageWithMultipleCards from "@/apps/dashboard/components/layouts/PageWithMultipleCards";
import TitleWithInfo from "@/apps/dashboard/components/shared/TitleWithInfo";
import { Box, Button, Divider, LegacyCard, VerticalStack, Text, HorizontalGrid, Card, Badge, HorizontalStack, Checkbox } from "@shopify/polaris";
import { useEffect, useState } from "react";
import PersistStore from "@/apps/main/PersistStore";
import api from "@/apps/dashboard/pages/observe/api";
import GridRows from "../../components/shared/GridRows";
import FlyLayout from "../../components/layouts/FlyLayout";
import GithubSimpleTable from "../../components/tables/GithubSimpleTable";
import { CellType } from "../../components/tables/rows/GithubRow";
import func from "@/util/func"
import transform from "../observe/transform";

// function CollectionCard({ cardObj }) {

//     const selectedApisCount = cardObj.selectedCtr || 0
//     const apisCount = cardObj.apis?.length || 0

//     return (
//         <div onClick={() => cardObj.onSelect()}>
//             <Card>
//                 <VerticalStack gap="2">
//                     <HorizontalStack align="space-between">
//                         <Text variant="headingSm">{cardObj.name}</Text>
//                         <Checkbox checked={selectedApisCount >= 1} />
//                     </HorizontalStack>
//                     <Box width="50%">
//                         <Badge size="small">{selectedApisCount} of {apisCount} API{apisCount == 1 ? "" : "s"} selected</Badge>
//                     </Box>
//                 </VerticalStack>
//             </Card>
//         </div>
//     )
// }



// function ApiSelector({ apiGroups, selectedApis, setSelectedApis }) {

//     const [showApis, setShowApis] = useState(false)
//     const [currentApiCollection, setCurrentApiCollection] = useState(null)

//     apiGroups = apiGroups.map(apiGroup => {
//         return {
//             ...apiGroup,
//             onSelect: () => {
//                 setShowApis(true)
//                 setCurrentApiCollection(apiGroup)
//             }
//         }
//     })

//     const headers = [
//         {
//             text: "Endpoint",
//             value: "endpointComp",
//             title: "API endpoints",
//             textValue: "endpoint",
//         },
//         {
//             title: "Collection",
//             value: "apiCollectionName",
//             type: CellType.TEXT,
//         }
//     ]

//     const resourceName = {
//         singular: 'API',
//         plural: 'APIs',
//     };

//     const promotedBulkActions = (selectedResources) => {
//         let ret = []
//         ret.push(
//             {
//                 content: 'Confirm APIs selection',
//                 onAction: () => {
//                     const updatedSelectedApis = [...selectedApis]

//                     selectedResources.forEach(id => {
//                         const idParts = id.split("###")
//                         const idWithoutRandomStr = idParts.slice(0, 3).join("###");
//                         if (!updatedSelectedApis.includes(idWithoutRandomStr)) {
//                             updatedSelectedApis.push(idWithoutRandomStr)
//                         }
//                     })

//                     setSelectedApis(updatedSelectedApis)
//                     setShowApis(false)
//                     setCurrentApiCollection(null)
//                 }
//             }
//         )

//         return ret;
//     }

//     const initSelectedResources = currentApiCollection ? currentApiCollection.apis.filter(api => api.selected).map(api => api.id) : []
//     const initAllResourcesSelected = currentApiCollection ? currentApiCollection.apis.length === initSelectedResources.length : false

//     const apisFlyLayoutComponents = currentApiCollection ? [
//         <LegacyCard minHeight="100%">
//             <GithubSimpleTable
//                 pageLimit={10}
//                 data={currentApiCollection.apis}
//                 resourceName={resourceName}
//                 headers={headers}
//                 headings={headers}
//                 useNewRow={true}
//                 selectable={true}
//                 hideQueryField={true}
//                 promotedBulkActions={promotedBulkActions}
//                 initSelectedResources={initSelectedResources}
//                 initAllResourcesSelected={initAllResourcesSelected}
//             />
//         </LegacyCard>
//     ] : []

//     const apiCollectionTitle = currentApiCollection ?
//         <HorizontalStack gap="2">
//             <Text variant="headingSm">{currentApiCollection.name}</Text>
//             <Badge size="small">
//                 {currentApiCollection.apis?.length} API{currentApiCollection.apis?.length === 1 ? "" : "s"}
//             </Badge>
//         </HorizontalStack> : null

//     const handleClose = () => {
//         setCurrentApiCollection(null)
//     }

//     return (
//         <Box minHeight="300px" padding={'4'}>

//             <GridRows CardComponent={CollectionCard} columns="3" items={apiGroups} changedColumns={3} />

//             <FlyLayout
//                 show={showApis}
//                 titleComp={apiCollectionTitle}
//                 components={apisFlyLayoutComponents}
//                 isHandleClose={true}
//                 handleClose={handleClose}
//                 setShow={setShowApis}
//             />
//         </Box>
//     )
// }

function ItemGroupCard({ cardObj }) {
    const selectedItemsCount = cardObj.selectedCtr || 0
    const itemsCount = cardObj[cardObj?.itemsListFieldName]?.length || 0
    const itemsResourceName = cardObj?.itemsResourceName
    
    const itemGroupNameField = cardObj?.itemGroupNameField || "name"
    const itemGroupName = cardObj[itemGroupNameField] || ""

    return (
        <div onClick={() => cardObj.onSelect()}>
            <Card>
                <VerticalStack gap="2">
                    <HorizontalStack align="space-between">
                        <Text variant="headingSm">{itemGroupName}</Text>
                        <Checkbox checked={selectedItemsCount >= 1} />
                    </HorizontalStack>
                    <Box width="80%">
                        <HorizontalStack align="space-between">
                            <Badge size="small">{selectedItemsCount} of {itemsCount} {itemsCount == 1 ? itemsResourceName.singular : itemsResourceName.plural} selected</Badge>
                            {cardObj.additionalCardBadge || null}
                        </HorizontalStack>
                    </Box>
                </VerticalStack>
            </Card>
        </div>
    )
}

function MultipleItemsSelector({ itemGroups, selectedItems, setSelectedItems, itemsResourceName, itemsListFieldName, itemsTableHeaders, processItemId }) {

    const [showGroupItems, setShowGroupItems] = useState(false)
    const [currentItemGroup, setCurrentItemGroup] = useState(null)

    itemGroups.forEach(itemGroup => {

        itemGroup.onSelect = () => {
            setShowGroupItems(true)
            setCurrentItemGroup(itemGroup)
        }
        itemGroup.itemsListFieldName = itemsListFieldName
        itemGroup.itemsResourceName = itemsResourceName

        let selectedCtr = 0

        itemGroup[itemsListFieldName].forEach(item => {
            const id = item.id
            const processedItemId = processItemId !== undefined ? processItemId(id) : id

            if (selectedItems.includes(processedItemId)) {
                item.selected = true
                selectedCtr += 1
            } else {
                item.selected = false
            }
        })
        itemGroup.selectedCtr = selectedCtr
    })

    const handleClose = () => {
        setCurrentItemGroup(null)
    }

    const itemGroupTitle = currentItemGroup ?
        <HorizontalStack gap="2">
            <Text variant="headingSm">{currentItemGroup[currentItemGroup.itemGroupNameField || "name"]}</Text>
            <Badge size="small">
                {currentItemGroup[itemsListFieldName]?.length} {currentItemGroup[itemsListFieldName]?.length === 1 ? itemsResourceName.singular : itemsResourceName.plural}
            </Badge>
            {currentItemGroup.additionalCardBadge || null}
        </HorizontalStack> : null

    const promotedBulkActions = (selectedResources) => {
        let ret = []
        ret.push(
            {
                content: `Confirm ${itemsResourceName.plural} selection`,
                onAction: () => {
                    const updatedSelectedItems = [...selectedItems]

                    selectedResources.forEach(id => {
                        const processedItemId = processItemId !== undefined ? processItemId(id) : id
                        if (!updatedSelectedItems.includes(processedItemId)) {
                            updatedSelectedItems.push(processedItemId)
                        }
                    })

                    setSelectedItems(updatedSelectedItems)
                    setShowGroupItems(false)
                    setCurrentItemGroup(null)
                }
            }
        )

        return ret;
    }

    const initSelectedResources = currentItemGroup ? currentItemGroup[itemsListFieldName].filter(item => item.selected).map(item => item.id) : []
    const initAllResourcesSelected = currentItemGroup ? currentItemGroup[itemsListFieldName].length === initSelectedResources.length : false

    const itemsFlyLayoutComponents = currentItemGroup ? [
        <LegacyCard minHeight="100%">
            <GithubSimpleTable
                pageLimit={10}
                data={currentItemGroup[itemsListFieldName]}
                resourceName={itemsResourceName}
                headers={itemsTableHeaders}
                headings={itemsTableHeaders}
                useNewRow={true}
                selectable={true}
                hideQueryField={true}
                promotedBulkActions={promotedBulkActions}
                initSelectedResources={initSelectedResources}
                initAllResourcesSelected={initAllResourcesSelected}
            />
        </LegacyCard>
    ] : []

    return (
        <Box minHeight="300px" padding={'4'}>

            <GridRows CardComponent={ItemGroupCard} columns="3" items={itemGroups} changedColumns={3} />

            <FlyLayout
                show={showGroupItems}
                titleComp={itemGroupTitle}
                components={itemsFlyLayoutComponents}
                isHandleClose={true}
                handleClose={handleClose}
                setShow={setShowGroupItems}
            />
        </Box>
    )
}

function AuthenticationSetup() {

    const tmpSelected = [
        "POST###http://sampl-aktol-1exannwybqov-67928726.ap-south-1.elb.amazonaws.com/api/college/sac/password-reset###1111111111",
        "POST###http://sampl-aktol-1exannwybqov-67928726.ap-south-1.elb.amazonaws.com/api/college/account/recover###1111111111"
    ]

    const allCollections = PersistStore(state => state.allCollections)
    const collectionsMap = PersistStore(state => state.collectionsMap)
    const [selectedApis, setSelectedApis] = useState(tmpSelected)
    const [authenticationApiGroups, setAuthenticationApiGroups] = useState([])
    
    const categoryMap = PersistStore(state => state.categoryMap)
    const subCategoryMap = PersistStore(state => state.subCategoryMap)
    const [selectedSubCategories, setSelectedSubCategories] = useState([])
    const [authenticationTestCategories, setAuthenticationTestCategories] = useState([])

    // authenticationApiGroups.forEach(apiGroup => {
    //     let selectedCtr = 0

    //     apiGroup.apis.forEach(api => {
    //         const id = api.id
    //         const idParts = id.split("###")
    //         const idWithoutRandomStr = idParts.slice(0, 3).join("###");

    //         if (selectedApis.includes(idWithoutRandomStr)) {
    //             api.selected = true
    //             selectedCtr += 1
    //         } else {
    //             api.selected = false
    //         }
    //     })

    //     apiGroup.selectedCtr = selectedCtr
    // })





    console.log(authenticationApiGroups)

    const fetchAuthenticationSetupData = async () => {
        const authenticationApiGroupsCopy = allCollections.filter(x => [111_111_128, 111_111_129, 111_111_130].includes(x.id))
        for (const authenticationApiGroup of authenticationApiGroupsCopy) {
            const collectionId = authenticationApiGroup.id
            const apiCollectionData = await api.fetchAPICollection(collectionId)
            const apiEndpointsInCollection = apiCollectionData.data.endpoints.map(x => { return { ...x._id, startTs: x.startTs, changesCount: x.changesCount, shadow: x.shadow ? x.shadow : false } })
            const apiInfoListInCollection = apiCollectionData.data.apiInfoList
            const mergeApiInfoAndApiCollectionResult = func.mergeApiInfoAndApiCollection(apiEndpointsInCollection, apiInfoListInCollection, collectionsMap)
            authenticationApiGroup.apis = transform.prettifyEndpointsData(mergeApiInfoAndApiCollectionResult)
        }


        setAuthenticationApiGroups(authenticationApiGroupsCopy)

        const subCategoryMapCopy = { ...subCategoryMap }
        let authenticationTestCategoriesCopy = { ...categoryMap }
        // Initialize sub categories
        Object.values(authenticationTestCategoriesCopy).forEach(category => {
            category.itemGroupNameField = "shortName"
            category.subCategories = []
        })

        // Add sub categories to categories
        Object.values(subCategoryMapCopy).forEach(subCategory => {
            const category = authenticationTestCategoriesCopy[subCategory.superCategory.name]
            
            subCategory.id = subCategory.name
            category.subCategories.push(subCategory)
        })

        // Add missing configs to sub categories
        const subCategories = []
        Object.values(authenticationTestCategoriesCopy).forEach(category => {
            category.subCategories.forEach(subCategory => {
                subCategories.push(subCategory.name)
            })
        })

        const fetchTestConfigsRequired = await api.fetchTestConfigsRequired(subCategories)
        const subCategoryVsMissingConfigsMap = fetchTestConfigsRequired?.subCategoryVsMissingConfigsMap || {}
        Object.values(authenticationTestCategoriesCopy).forEach(category => {
            let categorySubCategoriesRequireConfigs = false
            category.subCategories.forEach(subCategory => {
                subCategory.missingConfigs = subCategoryVsMissingConfigsMap[subCategory.name] || []

                if (subCategory.missingConfigs.length > 0) {
                    categorySubCategoriesRequireConfigs = true
                }
            })

            if (categorySubCategoriesRequireConfigs) {
                category.additionalCardBadge = <Badge size="small" status="warning">Config needed</Badge>
            } 
        })


        authenticationTestCategoriesCopy = Object.values(authenticationTestCategoriesCopy)
        setAuthenticationTestCategories(authenticationTestCategoriesCopy)

        console.log(authenticationTestCategoriesCopy)
    }

    useEffect(() => {
        fetchAuthenticationSetupData()
    }, [])


    // API Selector
    const apisSelectorResourceName = {
        singular: 'API',
        plural: 'APIs',
    };

    const apisSelectorTableHeaders = [
        {
            text: "Endpoint",
            value: "endpointComp",
            title: "API endpoints",
            textValue: "endpoint",
        },
        {
            title: "Collection",
            value: "apiCollectionName",
            type: CellType.TEXT,
        }
    ]

    // Sub Categories Selector
    const subCategoriesSelectorResourceName = {
        singular: 'test',
        plural: 'tests',
    };

    const subCategoriesSelectorTableHeaders = [
        {
            title: "Tests",
            value: "testName",
            type: CellType.TEXT,
        }
    ]

    const [authenticationScreenState, setAuthenticationScreenState] = useState({
        currentPageIndex: 0,
        selectedApis: [],
        selectedTests: [],
    })

    const authenticationScreenPages = [
        {
            title: "Select APIs",
            pageComponent: 
                (<MultipleItemsSelector
                    itemGroups={authenticationApiGroups}
                    selectedItems={selectedApis}
                    setSelectedItems={setSelectedApis}
                    itemsResourceName={apisSelectorResourceName}
                    itemsListFieldName="apis"
                    itemsTableHeaders={apisSelectorTableHeaders}
                    processItemId={(id) => {
                        const idParts = id.split("###")
                        return idParts.slice(0, 3).join("###");
                    }}
                />),
            pageNavigation: [
                <Button>Next</Button>
            ]
        }, 
        {
            title: "Select Tests",
            pageComponent: 
                (<MultipleItemsSelector
                    itemGroups={authenticationTestCategories}
                    selectedItems={selectedSubCategories}
                    setSelectedItems={setSelectedSubCategories}
                    itemsResourceName={subCategoriesSelectorResourceName}
                    itemsListFieldName="subCategories"
                    itemsTableHeaders={subCategoriesSelectorTableHeaders}
                />),
        },
        {
            title: "Set configurations"
        }
    ]


    return (
        <LegacyCard>

            <div style={{display: "flex", justifyContent: "center", alignItems: "center", height: "100px"}}>
                <HorizontalStack>
                <Button>Setup APIs</Button>
                <Button>Setup Tests</Button>

                </HorizontalStack>
            </div>
            
            <Divider />

            {/* <ApiSelector
                apiGroups={authenticationApiGroups}
                selectedApis={selectedApis}
                setSelectedApis={setSelectedApis}
            /> */}

            {/* <MultipleItemsSelector
                itemGroups={authenticationApiGroups}
                selectedItems={selectedApis}
                setSelectedItems={setSelectedApis}
                itemsResourceName={apisSelectorResourceName}
                itemsListFieldName="apis"
                itemsTableHeaders={apisSelectorTableHeaders}
                processItemId={(id) => {
                    const idParts = id.split("###")
                    return idParts.slice(0, 3).join("###");
                }}
            /> */}

            {/* {authenticationScreenPages[authenticationScreenState.currentPageIndex].pageComponent} */}
            {authenticationScreenPages[1].pageComponent}

            <Divider />
            <Box minHeight="76px">
                <HorizontalStack>
                    <Button>Previous</Button>
                    <Button>Next</Button>
                </HorizontalStack>
            </Box>
        </LegacyCard>
    )
}

function Authentication() {

    return (
        <PageWithMultipleCards
            title={<TitleWithInfo
                titleText={"Authentication"}
                tooltipContent={"Configure authentication based tests."}
            />}
            isFirstPage={true}
            components={[<AuthenticationSetup />]}
        />
    )
}

export default Authentication;