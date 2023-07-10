import React, { useEffect, useState } from 'react'
import IntegrationsLayout from './IntegrationsLayout'
import { Button, LegacyCard, ResourceItem, ResourceList, Text, TextField } from '@shopify/polaris'
import {SortMinor} from "@shopify/polaris-icons"
import Store from "../../../store"
import "../settings.css"
import settingFunctions from '../module'
import SearchWithList from '../../../components/layouts/SearchWithList'

function AktoGPT() {

    const apiCollections = Store(state => state.allCollections)
    const [selectedItems, setSelectedItems] = useState([]);
    const [clonedItems, setClonedItems] = useState([]);
    const [searchItems, setSearchItems] = useState([])
    const [displayItems , setDisplayItems] = useState(apiCollections)
    const [sortOrder, setSortOrder] = useState(true)

    async function fetchSelectedCollections(){
        let arr = await settingFunctions.fetchGptCollections() 
        setSelectedItems(arr)
        setClonedItems(arr)
    }

    const transformApiCollections = () =>{
        const arr = apiCollections.map((item) => {
            return {
                value: item.id,
                label: item.name
            }
        })
        setSearchItems(arr)
    }

    useEffect(()=>{
        fetchSelectedCollections()
        transformApiCollections()
    },[])

    function renderItem(item) {
        const {id,name} = item;
        return (
            <ResourceItem id={id}>
                <Text fontWeight="bold" as="h3">
                    {name}
                </Text>
            </ResourceItem>
        );
    }

    const discardAction = () =>{
        setSelectedItems(clonedItems)
    }

    const saveAction = async() =>{
        await settingFunctions.updateGptCollections(selectedItems,apiCollections)
    }

    function compareItems (){
        let a = new Set(clonedItems)
        let b = new Set(selectedItems)
        return a.size === b.size && [...a].every(value => b.has(value))
    }

    const sortItems = () =>{
        setSelectedItems([])
        const arr = [...displayItems].sort((a, b) => {
            let aComp = a.displayName.replace(/[^a-zA-Z]/g, '').toLowerCase();
            let bComp = b.displayName.replace(/[^a-zA-Z]/g, '').toLowerCase();
            if (aComp < bComp) return -1;
            if (aComp > bComp) return 1;
            return 0;
        });
        if(!sortOrder){
            arr.reverse()
        }
        setSortOrder(!sortOrder)
        setDisplayItems(arr)
        setTimeout(() => {
            setSelectedItems(clonedItems);
        }, 0)
    }
    const sortFunc = (
        <Button icon={SortMinor} onClick={sortItems}>Sort</Button>
    )

    const searchResult = (item) =>{
        const updatedArray = selectedItems.includes(item) ? selectedItems.filter((el) => el !== item) : [...selectedItems, item]; 
        setSelectedItems(updatedArray)
    }

    const headerComponent = (
        <SearchWithList searchItems = {searchItems} connectedRight = {sortFunc} getSelected={searchResult}/>
    )

    const component = (
        <LegacyCard title="Akto GPT configuration" 
                    secondaryFooterActions={[{content: 'Discard Changes', destructive: true, onAction: discardAction, disabled: compareItems() }]}
                    primaryFooterAction={{content: 'Save', onAction: saveAction, disabled: compareItems()}}
        >
            <LegacyCard.Section title="Manage using AktoGPT for all your collections">
                <ResourceList
                    headerContent="Select All Collections"
                    items={displayItems}
                    renderItem={renderItem}
                    selectedItems={selectedItems}
                    onSelectionChange={setSelectedItems}
                    selectable
                    filterControl={headerComponent}
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