import React from 'react'
import quickStartFunc from '../tranform';
import { Badge, Page, Text } from '@shopify/polaris';
import RowCard from './RowCard';
import GridRows from '../../../components/shared/GridRows';
import { useState } from 'react';

function UpdateConnections(props) {

    const { myConnections } = props; 
    const allConnections = quickStartFunc.getConnectorsList()
    const obj = quickStartFunc.getConnectionsObject(myConnections,allConnections)
    const [newCol, setNewCol] = useState(0)

    const onButtonClick = () => {
        setNewCol(2)
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

        </Page>
    )
}

export default UpdateConnections