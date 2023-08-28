import React, { useEffect, useState } from 'react'
import quickStartFunc from '../transform';
import { Badge, Button, Card, HorizontalStack, Page, Scrollable, Tag, Text } from '@shopify/polaris';
import {CancelMinor} from "@shopify/polaris-icons"
import RowCard from './RowCard';
import GridRows from '../../../components/shared/GridRows';
import QuickStartStore from '../quickStartStore';

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
    },[])

    return (
        <Page divider title='Quick start' fullWidth>
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

            {
                currentCardObj ? 
                <div className="right-card">
                    <Card>
                        <Scrollable shadow style={{maxHeight: '85vh'}} focusable>
                            <div className='settings'>
                                <Text variant="headingMd" as="h6">Setup guide </Text>
                                <Button plain icon={CancelMinor} onClick={closeAction} />
                            </div>
                            <HorizontalStack gap="1">
                                <Text variant="headingMd" as="h6">{currentCardObj.label} </Text>
                                {currentCardObj.badge ? <Badge size='small' status='info'>{currentCardObj.badge}</Badge> : null}
                            </HorizontalStack>
                            {currentCardObj.component}
                        </Scrollable>
                    </Card>
                </div>
                : null
            }

        </Page>
    )
}

export default UpdateConnections