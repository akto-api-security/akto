import { Badge, Box, HorizontalStack, LegacyCard, Text } from "@shopify/polaris"
import { useState } from "react"
import ItemsGroupCard from "./ItemsGroupCard"
import GithubSimpleTable from "../../../components/tables/GithubSimpleTable"
import GridRows from "../../../components/shared/GridRows"
import FlyLayout from "../../../components/layouts/FlyLayout"

function ItemsSelectorFromCard({ itemGroups, selectedItems, setSelectedItems, itemsResourceName, itemsListFieldName, itemsTableHeaders, processItemId }) {

    const [showGroupItems, setShowGroupItems] = useState(false)
    const [currentItemGroup, setCurrentItemGroup] = useState(null)

    itemGroups.forEach(itemGroup => {

        itemGroup.onSelect = () => {
            setShowGroupItems(true)
            setCurrentItemGroup(itemGroup)
        }
        itemGroup.itemsListFieldName = itemsListFieldName
        itemGroup.itemsResourceName = itemsResourceName

        let selectedCtr = 0

        itemGroup[itemsListFieldName].forEach(item => {
            const id = item.id
            const processedItemId = processItemId !== undefined ? processItemId(id) : id

            if (selectedItems.includes(processedItemId)) {
                item.selected = true
                selectedCtr += 1
            } else {
                item.selected = false
            }
        })
        itemGroup.selectedCtr = selectedCtr
    })

    const handleClose = () => {
        setCurrentItemGroup(null)
    }

    const itemGroupTitle = currentItemGroup ?
        <HorizontalStack gap="2">
            <Text variant="headingSm">{currentItemGroup[currentItemGroup.itemGroupNameField || "name"]}</Text>
            <Badge size="small">
                {currentItemGroup[itemsListFieldName]?.length} {currentItemGroup[itemsListFieldName]?.length === 1 ? itemsResourceName.singular : itemsResourceName.plural}
            </Badge>
            {currentItemGroup.additionalCardBadge || null}
        </HorizontalStack> : null

    const promotedBulkActions = (selectedResources) => {
        let ret = []
        ret.push(
            {
                content: `Confirm ${itemsResourceName.plural} selection`,
                onAction: () => {
                    const updatedSelectedItems = [...selectedItems]

                    selectedResources.forEach(id => {
                        const processedItemId = processItemId !== undefined ? processItemId(id) : id
                        if (!updatedSelectedItems.includes(processedItemId)) {
                            updatedSelectedItems.push(processedItemId)
                        }
                    })

                    setSelectedItems(updatedSelectedItems)
                    setShowGroupItems(false)
                    setCurrentItemGroup(null)
                }
            }
        )

        return ret;
    }

    const initSelectedResources = currentItemGroup ? currentItemGroup[itemsListFieldName].filter(item => item.selected).map(item => item.id) : []
    const initAllResourcesSelected = currentItemGroup ? currentItemGroup[itemsListFieldName].length === initSelectedResources.length : false

    const itemsFlyLayoutComponents = currentItemGroup ? [
        <LegacyCard minHeight="100%">
            <GithubSimpleTable
                pageLimit={10}
                data={currentItemGroup[itemsListFieldName]}
                resourceName={itemsResourceName}
                headers={itemsTableHeaders}
                headings={itemsTableHeaders}
                useNewRow={true}
                selectable={true}
                hideQueryField={true}
                promotedBulkActions={promotedBulkActions}
                initSelectedResources={initSelectedResources}
                initAllResourcesSelected={initAllResourcesSelected}
            />
        </LegacyCard>
    ] : []

    return (
        <Box minHeight="300px" padding={'4'}>

            <GridRows CardComponent={ItemsGroupCard} columns="3" items={itemGroups} changedColumns={3} />

            <FlyLayout
                show={showGroupItems}
                titleComp={itemGroupTitle}
                components={itemsFlyLayoutComponents}
                isHandleClose={true}
                handleClose={handleClose}
                setShow={setShowGroupItems}
            />
        </Box>
    )
}

export default ItemsSelectorFromCard