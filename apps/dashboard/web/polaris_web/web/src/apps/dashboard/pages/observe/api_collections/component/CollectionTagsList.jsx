import { Badge, Box, Button, HorizontalStack, Text, VerticalStack } from "@shopify/polaris"
import { ChevronLeftMinor, ChevronRightMinor } from "@shopify/polaris-icons"
import React from "react"
import LocalStore from "../../../../../main/LocalStorageStore"
import ShowListInBadge from "../../../../components/shared/ShowListInBadge"
import TooltipText from "../../../../components/shared/TooltipText"

const TAGS_PER_ROW = 5

// Column heading with a toggle to show every collection tag instead of only the first one
export function CollectionTagsHeading({ title }) {
    const expanded = LocalStore(state => state.expandCollectionTags)
    const setExpanded = LocalStore(state => state.setExpandCollectionTags)

    return (
        <HorizontalStack gap="1" blockAlign="center" wrap={false}>
            <Text>{title}</Text>
            <div onClick={(e) => e.stopPropagation()}>
                <Button
                    plain
                    monochrome
                    size="slim"
                    icon={expanded ? ChevronLeftMinor : ChevronRightMinor}
                    accessibilityLabel={expanded ? "Collapse tags" : "Expand all tags"}
                    onClick={() => setExpanded(!expanded)}
                />
            </div>
        </HorizontalStack>
    )
}

function CollectionTagsList({ itemsArr, maxItems, wrap, getItemStatus }) {
    const expanded = LocalStore(state => state.expandCollectionTags)

    if (!expanded) {
        return (
            <ShowListInBadge
                itemsArr={itemsArr}
                maxItems={maxItems}
                status={"info"}
                useTooltip={true}
                wrap={wrap}
                allowFullWidth={true}
                getItemStatus={getItemStatus}
            />
        )
    }

    const rows = []
    for (let i = 0; i < itemsArr.length; i += TAGS_PER_ROW) {
        rows.push(itemsArr.slice(i, i + TAGS_PER_ROW))
    }

    return (
        <VerticalStack gap="1">
            {rows.map((row, rowIndex) => (
                <HorizontalStack key={rowIndex} gap="1" wrap={false}>
                    {row.map((item, index) => (
                        <Badge key={index + item} size="medium" status={getItemStatus ? getItemStatus(item) : "info"}>
                            <Box maxWidth="200px">
                                <TooltipText tooltip={item} text={item} />
                            </Box>
                        </Badge>
                    ))}
                </HorizontalStack>
            ))}
        </VerticalStack>
    )
}

export default CollectionTagsList
