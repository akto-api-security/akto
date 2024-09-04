import React, { useEffect, useState } from 'react'
import quickStartFunc from '../transform';
import { Badge,HorizontalStack, Page, Tag, Text } from '@shopify/polaris';
import RowCard from './RowCard';
import GridRows from '../../../components/shared/GridRows';
import QuickStartStore from '../quickStartStore';
import TitleWithInfo from '@/apps/dashboard/components/shared/TitleWithInfo';
import FlyLayout from '../../../components/layouts/FlyLayout';

function UpdateConnections(props) {

    const { myConnections } = props; 
    const allConnections = quickStartFunc.getConnectorsList()
    const obj = quickStartFunc.getConnectionsObject(myConnections,allConnections)
    const [newCol, setNewCol] = useState(0)

    const currentCardObj = QuickStartStore(state => state.currentConnector)
    const setCurrentCardObj = QuickStartStore(state => state.setCurrentConnector)

    const closeAction = () => {
        setCurrentCardObj(null)
        setNewCol(0)
    }

    const onButtonClick = (cardObj) => {
        setNewCol(2)
        setCurrentCardObj(cardObj)
    }

    useEffect(()=>{
        setCurrentCardObj(null)
        onButtonClick(obj["Source Code"][3])
    },[])

    const components = [
        currentCardObj ? <HorizontalStack gap="1">
            <Text variant="headingMd" as="h6">{currentCardObj.label} </Text>
            {currentCardObj.badge ? <Badge size='small' status='info'>{currentCardObj.badge}</Badge> : null}
        </HorizontalStack> : null,
        currentCardObj ? currentCardObj.component : null
    ]

    return (
        <Page 
            divider fullWidth
            title={<TitleWithInfo 
                        tooltipContent={"Learn how to send API traffic data from traffic connectors to Akto Dashboard. "} 
                        titleText={"Quick start"}  
                        docsUrl={"https://docs.akto.io/traffic-connections/traffic-data-sources"}
                    />}
        >
            <div style={{marginBottom: '16px'}}>
            <HorizontalStack gap={"3"}>
                <Text variant="headingMd" as="h6" color='subdued'> Your connections </Text>
                <Tag>{obj.myConnections.length.toString()}</Tag>
            </HorizontalStack>
            </div>
            <GridRows CardComponent={RowCard} columns="3" 
                items={obj.myConnections} buttonText="Configure" onButtonClick={onButtonClick}
                changedColumns={newCol}
            />

            <div style={{margin: '24px 0 16px 0'}}>
            <HorizontalStack gap={"3"}>
                <Text variant="headingMd" as="h6" color='subdued'> Explore other connections </Text>
                <Tag>{obj.moreConnections.length.toString()}</Tag>
            </HorizontalStack>
            </div>
            <GridRows CardComponent={RowCard} columns="3" 
                items={obj.moreConnections} buttonText="Connect" onButtonClick={onButtonClick}     
                changedColumns={newCol}
            />
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
                            