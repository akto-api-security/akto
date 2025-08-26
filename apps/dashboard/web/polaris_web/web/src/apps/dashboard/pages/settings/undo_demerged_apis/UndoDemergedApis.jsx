import { Button, HorizontalStack, LegacyCard, ResourceItem, ResourceList, Text } from '@shopify/polaris'
import React, { useEffect, useState } from 'react'
import settingRequests from '../api'
import func from '@/util/func'
import PersistStore from '../../../../main/PersistStore'
import GetPrettifyEndpoint from '../../observe/GetPrettifyEndpoint'
import PageWithMultipleCards from '../../../components/layouts/PageWithMultipleCards'
import TitleWithInfo from '../../../components/shared/TitleWithInfo'

const UndoDemergedApis = () => {
    const [mergedApis, setMergedApis] = useState([])
    const [loading, setLoading] = useState(false)
    
    const collectionsMap = PersistStore(state => state.collectionsMap)

    const fetchMergedApis = async () => {
        setLoading(true)
        await settingRequests.getDeMergedApis().then((res) => {
            setMergedApis(res || [])
            setLoading(false)
        }).catch((err) => {
            func.setToast(true, true, 'Something went wrong while fetching de-merged APIs')
            setLoading(false)
        })
    }

    const deleteDuplicateEntries = async () => {
        await settingRequests.deleteDuplicateEntries();
    }

    useEffect(() => {
        fetchMergedApis()
    }, [])

    const undoDemergedApis = async (mergedApis) => {
        setLoading(true)
        await settingRequests.undoDemergedApis(mergedApis).then((res) => {
            setLoading(false)
            func.setToast(true, false, 'Successfully undone de-merged APIs')
            fetchMergedApis()
        }).catch((err) => {
            func.setToast(true, true, 'Something went wrong while undoing de-merged APIs')
            setLoading(false)
        })
    }

    const resourceListRenderItems = (item) => {
        const { id, url, method, apiCollectionId } = item
        const collectionName = collectionsMap[apiCollectionId] || ''

        const shortcutActions = [
            {
                content: <Button plain>Undo</Button>,
                onAction: () => undoDemergedApis([item])
            }
        ]

        return (
            <ResourceItem
                id={id}
                shortcutActions={shortcutActions}
                persistActions
            >
                <GetPrettifyEndpoint method={method} methodBoxWidth={" "} url={url} isNew={false} />
                {/* <Text variant="bodyMd">
                    {collectionName}
                </Text> */}
            </ResourceItem>
        )
    }

    const table = (
        <LegacyCard>
            {
                mergedApis && mergedApis?.length > 0 ?
                <ResourceList
                    resourceName={{ singular: 'merged api', plural: 'merged apis' }}
                    items={mergedApis}
                    renderItem={resourceListRenderItems}
                    headerContent={`Showing ${mergedApis.length} merged api${mergedApis.length > 1 ? 's': ''}`}
                    showHeader
                    loading={loading}
                /> : 
                <div style={{height: "400px", display: 'flex', justifyContent: 'center', alignItems: 'center'}}>
                    <Text variant='headingLg'>Nothing to show here</Text>
                </div>
            }
        </LegacyCard>
    )

    const secondaryActionsComp = (
        <HorizontalStack gap={"2"}>
            <Button disabled={mergedApis?.length === 0} onClick={() => undoDemergedApis(mergedApis)}>Undo All De-merged APIs</Button>
                {window.USER_NAME?.toLowerCase()?.includes("@akto.io") ? <Button onClick={() => deleteDuplicateEntries()}>Delete duplicates</Button> : null}
        </HorizontalStack>
        
    )

    return (
        <PageWithMultipleCards
            title={
                <TitleWithInfo
                    tooltipContent={"Undo a de-merged API"}
                    titleText={"Undo De-merged APIs"} 
                    docsUrl={"https://docs.akto.io/api-inventory/how-to/de-merge-api"}
                />
            }
            secondaryActions={secondaryActionsComp}
            components={[table]}
            fullWidth={false}
            isFirstPage={true}
        />
    )
}

export default UndoDemergedApis