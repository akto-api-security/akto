import PageWithMultipleCards from "@/apps/dashboard/components/layouts/PageWithMultipleCards";
import TitleWithInfo from "@/apps/dashboard/components/shared/TitleWithInfo";
import { Box, Button, Divider, LegacyCard, VerticalStack, Text, HorizontalGrid, Card, Badge, HorizontalStack, Checkbox } from "@shopify/polaris";
import { useEffect, useState } from "react";
import PersistStore from "@/apps/main/PersistStore";
import api from "@/apps/dashboard/pages/testing/api";
import GridRows from "../../components/shared/GridRows";
import FlyLayout from "../../components/layouts/FlyLayout";

function CollectionCard({ cardObj }) {

    console.log(cardObj)
    return (
        <div onClick={() => cardObj.onSelect()}>
            <Card>
            <VerticalStack gap="2">
                    <HorizontalStack align="space-between">
                        <Text variant="headingSm">{cardObj.name}</Text>
                        <Checkbox checked/>
                    </HorizontalStack>
                    <Box width="50%">
                        <Badge size="small">0 of 0 APIs selected</Badge>
                    </Box>
                </VerticalStack>
            </Card>
        </div>
    )
}

function ApiSelector({ apiGroups }) {

    const [ showApis, setShowApis ] = useState(false)
    const [ currentApiCollection, setCurrentApiCollection ] = useState(null)

    apiGroups = apiGroups.map(apiGroup => {
        return { 
            ...apiGroup, 
            onSelect: () => { 
                setShowApis(true)
                setCurrentApiCollection(apiGroup) 
            }
        }
    })

    const apisFlyLayoutComponents = [
        <div>hello</div>
    ]

    const apiCollectionTitle = currentApiCollection ? 
        <HorizontalStack gap="2">
            <Text variant="headingSm">{currentApiCollection.name}</Text>
            <Badge size="small">
                {currentApiCollection.apis?.length} API{currentApiCollection.apis?.length === 1 ? "": "s" }
            </Badge>
        </HorizontalStack> : null

    const handleClose = () => {
        setCurrentApiCollection (null)
    }


    
    return (
        <Box minHeight="300px" padding={'4'}>
            
            <GridRows CardComponent={CollectionCard} columns="3" 
                items={apiGroups} buttonText="Connect" onButtonClick={() => {console.log("hello")}}     
                changedColumns={3}
            />

            <FlyLayout
                show={showApis}
                titleComp={apiCollectionTitle}
                components={apisFlyLayoutComponents}
                isHandleClose={true}
                handleClose={handleClose}
                setShow={setShowApis}
            />
        </Box>
    )
}

function AuthenticationSetup() {

    const allCollections = PersistStore(state => state.allCollections);
    const [ authenticationApiGroups, setAuthenticationApiGroups ] = useState([])
    
    console.log(authenticationApiGroups)

    const fetchAuthenticationSetupData = async () => {
        const authenticationApiGroupsCopy = allCollections.filter(x => [111_111_128, 111_111_129, 111_111_130].includes(x.id))
        for (const authenticationApiGroup of authenticationApiGroupsCopy) {
            const collectionId = authenticationApiGroup.id
            const apiEndpointsResponse = await api.fetchCollectionWiseApiEndpoints(collectionId)
            authenticationApiGroup.apis = apiEndpointsResponse?.listOfEndpointsInCollection
        }

        setAuthenticationApiGroups(authenticationApiGroupsCopy)
    }

    useEffect(() => {
        fetchAuthenticationSetupData()
    }, [])

    return (
        <LegacyCard>

            <Box minHeight="100px">
                <Button>hello</Button>
            </Box>
            <Divider />
            
            <ApiSelector apiGroups={authenticationApiGroups}/>



            <Divider />
            <Box minHeight="76px">
                <Button>hello</Button>
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
            components = {[<AuthenticationSetup/>]}
            />
    )
}

export default Authentication;