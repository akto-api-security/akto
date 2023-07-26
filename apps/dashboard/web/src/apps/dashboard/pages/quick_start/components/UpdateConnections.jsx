import React from 'react'
import quickStartFunc from '../tranform';
import { Badge, Button, Card, Page, Text } from '@shopify/polaris';
import {CancelMinor} from "@shopify/polaris-icons"
import RowCard from './RowCard';
import GridRows from '../../../components/shared/GridRows';
import { useState } from 'react';
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

    return (
        <Page divider title='Quick start' fullWidth>
            <div style={{marginBottom: '16px'}}>
                <Text variant="headingMd" as="h6"> Your Connections </Text>
            </div>
            <GridRows CardComponent={RowCard} columns="3" 
                items={obj.myConnections} buttonText="Configure" onButtonClick={onButtonClick}
                changedColumns={newCol}
            />

            <div style={{margin: '24px 0 16px 0', display: 'flex', gap: '4px'}}>
                <Text variant="headingMd" as="h6"> Explore Other Connections </Text>
                <Badge size='small' status='info'>{obj.moreConnections.length.toString()}</Badge>
            </div>
            <GridRows CardComponent={RowCard} columns="3" 
                items={obj.moreConnections} buttonText="Connect" onButtonClick={onButtonClick}     
                changedColumns={newCol}
            />

            {
                currentCardObj ? 
                <div className="right-card">
                    <Card>
                        <div className='settings'>
                            <Text variant="headingMd" as="h6">Setup guide </Text>
                            <Button plain icon={CancelMinor} onClick={closeAction} />
                        </div>
                        <Text variant="headingMd" as="h6">{currentCardObj.label} </Text>
                        {currentCardObj.component}
                    </Card>
                </div>
                : null
            }

        </Page>
    )
}

export default UpdateConnections