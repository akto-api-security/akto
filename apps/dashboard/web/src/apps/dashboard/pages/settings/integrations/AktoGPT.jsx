import React, { useState } from 'react'
import IntegrationsLayout from './IntegrationsLayout'
import { LegacyCard, ResourceItem, ResourceList, Text } from '@shopify/polaris'
import Store from "../../../store"
import "../settings.css"

function AktoGPT() {

    const apiCollections = Store(state => state.allCollections)

    const [selectedItems, setSelectedItems] = useState([]);

    function renderItem(item) {
        const {id,name} = item;

        return (
            <ResourceItem
                id={id}
            >
                <Text variant="bodyMd" fontWeight="bold" as="h3">
                    {name}
                </Text>
            </ResourceItem>
        );
    }

    const discardAction = () =>{
        setSelectedItems([])
    }

    const saveAction = () =>{
        console.log("save",selectedItems)
    }

    const component = (
        <LegacyCard title="Akto GPT configuration" 
                    secondaryFooterActions={[{content: 'Discard Changes', destructive: true, onAction: discardAction }]}
                    primaryFooterAction={{content: 'Save', onAction: saveAction}}
        >
            <LegacyCard.Section title="Manage using AktoGPT for all your collections">
                <ResourceList
                    headerContent="Select All Collections"
                    items={apiCollections}
                    renderItem={renderItem}
                    selectedItems={selectedItems}
                    onSelectionChange={setSelectedItems}
                    selectable
                    hasMoreItems
                />
            </LegacyCard.Section>
        </LegacyCard>
    )

    let cardContent = "Seamlessly enhance your web application security with AktoGPT integration, empowering you to efficiently detect vulnerabilities, analyze and intercept web traffic, and fortify your digital defenses. "

  return (
    <IntegrationsLayout title="AktoGPT" cardContent={cardContent} component={component} />
  )
}

export default AktoGPT