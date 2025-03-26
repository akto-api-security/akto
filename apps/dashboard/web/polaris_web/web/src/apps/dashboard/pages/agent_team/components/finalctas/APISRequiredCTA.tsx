import { Modal, Text } from '@shopify/polaris'
import React, { useEffect, useState } from 'react'
import { useAgentsStore } from '../../agents.store'
import { intermediateStore } from '../../intermediate.store'
import SearchableResourceList from '../../../../components/shared/SearchableResourceList'
import { usersCollectionRenderItem } from '../../../settings/rbac/utils'
import { preRequisitesMap } from '../../constants'

function APISRequiredCTA() {

    const {setFinalCTAShow, setPRState, PRstate, finalCTAShow} = useAgentsStore()
    const { sourceCodeCollections, setUserSelectedCollections, filteredUserInput } = intermediateStore();
    const [ show, setShow] = useState<boolean>(true);
    const [selectedApisCount, setSelectedApisCount] = useState<number>(0);

    const showCollections = sourceCodeCollections.length > 0
    const actionContent = showCollections ? `${selectedApisCount} APIs selected` : "Get APIs"
    const handleAction = () => {
        setShow(false); 
        setFinalCTAShow(false);
        setTimeout(() => {
            if(!showCollections){
                setPRState("-1")
            }else{
                if(selectedApisCount !== 0){
                    setPRState('-1')
                }
            }
        },10)
        
    }

    const handleSelection = (selectedIds: string[]) => {
        let apisCount = 0;
        const tempSet = new Set(selectedIds);
        sourceCodeCollections.forEach((collection: any) => {
            if(tempSet.has(collection.id)){
                apisCount += collection?.count || 0;
            }
        })
        setSelectedApisCount(apisCount);
        setUserSelectedCollections(selectedIds)
    }

    useEffect(() => {
        if(finalCTAShow && PRstate !== "-1"){
            setShow(true)
            preRequisitesMap["FIND_VULNERABILITIES_FROM_SOURCE_CODE"][1].action()   
        } 
        
    },[filteredUserInput, finalCTAShow, PRstate])

    const component = showCollections ? (
        <SearchableResourceList
            resourceName={'collection'}
            items={sourceCodeCollections.map((collection: any) => ({ id: collection.id, collectionName: collection.name }))}
            renderItem={usersCollectionRenderItem}
            isFilterControlEnabale={true}
            selectable={true}
            loading={false}
            onSelectedItemsChange={(selectedItems: any) => handleSelection(selectedItems)}
            alreadySelectedItems={[]}
        />

    ) : (<Text as="span" variant="bodyMd"> You need to have APIs from Source code analyzer agent on this directory first. Please get the APIs from the agent.</Text>)

    return (
       <Modal
            title={"APIs required"}
            open={show}
            onClose={() => setShow(false)}
            primaryAction={{
                content: actionContent,
                onAction: () => handleAction(), /* setCurrentAgent as source code agent here */
                disabled: (showCollections && selectedApisCount === 0)
            }}
            secondaryActions={[{
                content: 'Cancel',
                onAction: () => { setShow(false); setFinalCTAShow(false)}
            }]}
        >
            <Modal.Section>
                {component}
            </Modal.Section>
        </Modal>
    )
}

export default APISRequiredCTA