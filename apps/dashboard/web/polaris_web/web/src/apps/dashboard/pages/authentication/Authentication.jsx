import PageWithMultipleCards from "@/apps/dashboard/components/layouts/PageWithMultipleCards";
import TitleWithInfo from "@/apps/dashboard/components/shared/TitleWithInfo";
import { Divider, LegacyCard } from "@shopify/polaris";
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
    
    const [selectedSubCategories, setSelectedSubCategories] = useState([])
    const [authenticationTestCategories, setAuthenticationTestCategories] = useState([])

    const [currentStep, setCurrentStep] = useState(0)

    const fetchAuthenticationSetupData = async () => {
        const authenticationApiGroupsCopy = allCollections.filter(x => (x.urlsCount > 0 && x?.conditions !== null && !x?.deactivated))
        for (const authenticationApiGroup of authenticationApiGroupsCopy) {
            const collectionId = authenticationApiGroup.id
            const apiCollectionData = await api.fetchAPICollection(collectionId)
            const apiEndpointsInCollection = apiCollectionData.data.endpoints.map(x => { return { ...x._id, startTs: x.startTs, changesCount: x.changesCount, shadow: x.shadow ? x.shadow : false } })
            const apiInfoListInCollection = apiCollectionData.data.apiInfoList
            const mergeApiInfoAndApiCollectionResult = func.mergeApiInfoAndApiCollection(apiEndpointsInCollection, apiInfoListInCollection, collectionsMap)
            authenticationApiGroup.apis = transform.prettifyEndpointsData(mergeApiInfoAndApiCollectionResult)
        }


        setAuthenticationApiGroups(authenticationApiGroupsCopy)
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

    const authenticationScreenPages = [
        {
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
        }, 
        {
            pageComponent: 
                (<ItemsSelectorFromCard
                    itemGroups={authenticationTestCategories}
                    selectedItems={selectedSubCategories}
                    setSelectedItems={setSelectedSubCategories}
                    itemsResourceName={subCategoriesSelectorResourceName}
                    itemsListFieldName="subCategories"
                    itemsTableHeaders={subCategoriesSelectorTableHeaders}
                />)
        },
        {
           pageComponent: <div>hey</div>
        }
    ]

    return (
        <LegacyCard
        {...(currentStep < 3) ?  {primaryFooterAction: {content: currentStep < 2 ? 'Next step' : 'Run Test', onAction: () => setCurrentStep(currentStep + 1)}} :null}
        {...(currentStep > 0 && currentStep < 3) ? {secondaryFooterActions: [{content: "Back", onAction: ()=> setCurrentStep(currentStep - 1)}]}: null}>
            <LegacyCard.Section>
                <Steps totalSteps={3} currentStep={currentStep} stepsTextArr={['Select APIs', "Select tests", 'Set Configurations'] }/>
            </LegacyCard.Section>
            <LegacyCard.Section>
                {authenticationScreenPages[currentStep].pageComponent}
            </LegacyCard.Section>
            <Divider/>
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
            components={[<AuthenticationSetup key={"setup-screens"}/>]}
        />
    )
}

export default Authentication;