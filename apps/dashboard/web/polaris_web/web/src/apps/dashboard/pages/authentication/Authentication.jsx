import PageWithMultipleCards from "@/apps/dashboard/components/layouts/PageWithMultipleCards";
import TitleWithInfo from "@/apps/dashboard/components/shared/TitleWithInfo";
import { Box, Button, Divider, LegacyCard,Badge, HorizontalStack } from "@shopify/polaris";
import { useEffect, useState } from "react";
import PersistStore from "@/apps/main/PersistStore";
import api from "@/apps/dashboard/pages/observe/api";
import { CellType } from "../../components/tables/rows/GithubRow";
import func from "@/util/func"
import transform from "../observe/transform";
import ItemsSelectorFromCard from "./components/ItemsSelectorFromCard";
import Steps from "../../components/progress/Steps";

function AuthenticationSetup() {

    const allCollections = PersistStore(state => state.allCollections)
    const collectionsMap = PersistStore(state => state.collectionsMap)
    const [selectedApis, setSelectedApis] = useState([])
    const [authenticationApiGroups, setAuthenticationApiGroups] = useState([])
    
    const categoryMap = PersistStore(state => state.categoryMap)
    const subCategoryMap = PersistStore(state => state.subCategoryMap)
    const [selectedSubCategories, setSelectedSubCategories] = useState([])
    const [authenticationTestCategories, setAuthenticationTestCategories] = useState([])

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
                (<ItemsSelectorFromCard
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
                (<ItemsSelectorFromCard
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
        // <LegacyCard primaryFooterAction={{content: stepObj.actionText, onAction: next}}
        // {...(currentStep > 1 && currentStep < 4) ? {secondaryFooterActions: [{content: "Back", onAction: ()=> requestStepChange(currentStep - 1)}]}: null}>
        // </LegacyCard>
        <LegacyCard>
            <LegacyCard.Section>
                <Steps totalSteps={3} currentStep={1}/>
            </LegacyCard.Section>
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