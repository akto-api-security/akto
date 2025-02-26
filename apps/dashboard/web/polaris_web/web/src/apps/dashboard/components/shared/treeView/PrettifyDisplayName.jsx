import React, { useState } from 'react'
import TableStore from '../../tables/TableStore'
import { ChevronRightMinor, ChevronDownMinor } from "@shopify/polaris-icons"
import { Badge, Box, Checkbox, HorizontalStack, Icon, Text, Tooltip } from '@shopify/polaris';

function PrettifyDisplayName({name, level, isTerminal, isOpen, selectItems, collectionIds}) {
    const selectedItems = TableStore.getState().selectedItems.flat()
    const [checked, setChecked] = useState(false)

    const checkedVal = collectionIds.every(id => selectedItems.includes(id))

    const handleNewItems = (collectionIds) => {
        
        if(checkedVal){
            return selectedItems.filter((x) => !collectionIds.includes(x))
        }else{
            return [...new Set([...selectedItems, ...collectionIds])]
        }
    }

    const handleChange = (collectionIds, selectItems) => {
        const newItems = handleNewItems(collectionIds)
        TableStore.getState().setSelectedItems(newItems)
        selectItems(newItems)
        setChecked(!checked)
    }

    const len = level.split("#").length - 1
    const spacingWidth = (len - 1) * 16;

    let displayName = name
    if(level !== undefined || level.length > 0){
        displayName = level.split("#")[len];
    }
    const icon = isOpen ? ChevronDownMinor : ChevronRightMinor
    return(
        <Box width='230px'>
            <div className="styled-name">
                <HorizontalStack gap={"2"} wrap={false}>
                    {spacingWidth > 0 ? <Box width={`${spacingWidth}px`} /> : null}
                    {len !== 0 ? <Checkbox checked={checkedVal} onChange={() => handleChange(collectionIds, selectItems)}/> : null}
                    {!isTerminal ? <Box><Icon source={icon} /></Box> : null}
                    <Box maxWidth="160px">
                        <Tooltip content={name || displayName} dismissOnMouseOut>
                            <HorizontalStack align="space-between" wrap={false} gap={"2"}>
                                <Box maxWidth="130px">
                                    <Text variant="headingSm" truncate>{displayName}</Text>
                                </Box>
                                {collectionIds.length > 1 ? <Badge size="small" status="new">{collectionIds.length}</Badge> : null}
                            </HorizontalStack>
                        </Tooltip>
                    </Box>
                </HorizontalStack>
            </div>
        </Box>
    )
}

export default PrettifyDisplayName