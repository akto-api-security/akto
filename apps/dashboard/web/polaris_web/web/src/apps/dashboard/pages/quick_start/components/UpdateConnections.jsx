import React, { useEffect, useState } from 'react'
import quickStartFunc from '../transform';
import { Badge,HorizontalStack, Page, Tag, Text, VerticalStack, Divider} from '@shopify/polaris';
import RowCard from './RowCard';
import GridRows from '../../../components/shared/GridRows';
import QuickStartStore from '../quickStartStore';
import TitleWithInfo from '@/apps/dashboard/components/shared/TitleWithInfo';
import FlyLayout from '../../../components/layouts/FlyLayout';
import { useSearchParams } from 'react-router-dom';
import func from "@/util/func"
import EndpointShieldCard from './EndpointShieldCard';
import { isEndpointSecurityCategory } from '@/apps/main/labelHelper';

function UpdateConnections(props) {

    const [searchParams, setSearchParams] = useSearchParams();

    const { myConnections } = props; 
    const obj = quickStartFunc.getConnectorsListCategorized()
    const [newCol, setNewCol] = useState(0)

    const currentCardObj = QuickStartStore(state => state.currentConnector)
    const setCurrentCardObj = QuickStartStore(state => state.setCurrentConnector)

    const closeAction = () => {
        func.updateQueryParams(searchParams, setSearchParams, "connector","")
    }

    const onButtonClick = (cardObj) => {
        const connector = cardObj.key?.toLowerCase() ?? "";
        func.updateQueryParams(searchParams, setSearchParams, "connector", encodeURIComponent(connector))
    }

    useEffect(()=>{
        const connectorKey = decodeURIComponent(searchParams.get("connector") || "")
        if (connectorKey.length !== 0) {
            for (const categoryArr of Object.values(obj)) {
                for (const connectorCardObj of categoryArr) {
                    const connectorCardObjKey = connectorCardObj.key?.toLowerCase() ?? "";
                    if (connectorCardObjKey === connectorKey) {
                        setNewCol(2)
                        setCurrentCardObj(connectorCardObj);
                        return; 
                    }
                }
            }
        } 
        setNewCol(0)
        setCurrentCardObj(null)
    },[searchParams])

    const components = [
        currentCardObj ? <HorizontalStack gap="1">
            <Text variant="headingMd" as="h6">{currentCardObj.label} </Text>
            {currentCardObj.badge ? <Badge size='small' status='info'>{currentCardObj.badge}</Badge> : null}
        </HorizontalStack> : null,
        currentCardObj ? currentCardObj.component : null
    ]

    const handleInstallEndpointShield = () => {
        func.updateQueryParams(searchParams, setSearchParams, "connector", encodeURIComponent("mcp_endpoint_shield"))
    };

    const handleSeeDocsEndpointShield = () => {
        window.open('https://ai-security-docs.akto.io/akto-atlas-agentic-ai-security-for-employee-endpoints/endpoints-discovery-agents/mcp-endpoint-shield', '_blank');
    };

    const showRecommendedSetup = !func.checkLocal() && isEndpointSecurityCategory();

    return (
        <Page 
            fullWidth
            title={<TitleWithInfo 
                        tooltipContent={"Learn how to send API traffic data from traffic connectors to Akto Dashboard. "} 
                        titleText={"Quick start"}  
                        docsUrl={"https://docs.akto.io/traffic-connections/traffic-data-sources"}
                    />}
        >
            <div>
                <VerticalStack gap="8">
                    {showRecommendedSetup && (
                        <VerticalStack gap="4">
                            <Text variant="headingMd" as="h6">Recommended setup</Text>
                            <Divider />
                            <EndpointShieldCard
                                onInstall={handleInstallEndpointShield}
                                onSeeDocs={handleSeeDocsEndpointShield}
                            />
                        </VerticalStack>
                    )}
                    {Object.keys(obj).filter(key => key !== "").map((key, index) => {
                        return (
                            <VerticalStack gap="4" key={key}>
                            <HorizontalStack gap={"3"}>
                                <Text variant="headingMd" as="h6" color=""> {key} </Text>
                                <Tag>{obj[key].length.toString()}</Tag>
                            </HorizontalStack>
                            <Divider/>
                            <GridRows CardComponent={RowCard} columns="3"
                            items={obj[key]} buttonText="Connect" onButtonClick={onButtonClick}
                            changedColumns={newCol}
                            />
                            </VerticalStack>
                        )
                    })}
                </VerticalStack>
            </div>
                {currentCardObj ?<FlyLayout
                    width={"27vw"}
                    titleComp={
                        <TitleWithInfo 
                                tooltipContent={"Automate traffic to Akto"} 
                                titleText={"Set up guide"}  
                                docsUrl={currentCardObj.docsUrl}
                            />
                        }
                    show={currentCardObj !== null}
                    components={components}
                    isHandleClose={true}
                    handleClose={closeAction}
                    setShow={() => {}}
                />: null}
        </Page>
    )
}

export default UpdateConnections
                            