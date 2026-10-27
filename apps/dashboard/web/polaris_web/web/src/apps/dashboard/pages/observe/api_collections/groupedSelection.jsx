import React, { useEffect } from "react"
import { Checkbox, HorizontalStack } from "@shopify/polaris"
import useTable from "@/apps/dashboard/components/tables/TableContext"
import TableStore from "@/apps/dashboard/components/tables/TableStore"

/**
 * Selection for tables that group collections under a device row. A device row's id is
 * deviceRowId(endpointId); selecting it selects every collection under it, and each collection
 * row has its own checkbox, so bulk actions get the device ids and single collection ids mixed —
 * expandGroupedSelection turns that into collection ids.
 */

const DEVICE_ROW_PREFIX = "device:"

export const deviceRowId = (endpointId) => `${DEVICE_ROW_PREFIX}${endpointId}`

export const expandGroupedSelection = (selectedIds, collectionIdsOfDevice) => [...new Set(
    selectedIds.flatMap(id => (typeof id === "string" && id.startsWith(DEVICE_ROW_PREFIX))
        ? (collectionIdsOfDevice(id) || [])
        : [id])
)]

// Clears the table selection (shared across tables) when a grouped table mounts and unmounts,
// so a selection left on one page doesn't open the bulk-action bar on the next.
export const useClearSelectionOnMount = () => {
    const { selectItems } = useTable()
    useEffect(() => {
        selectItems([])
        TableStore.getState().setSelectedItems([])
        return () => {
            selectItems([])
            TableStore.getState().setSelectedItems([])
        }
    // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [])
}

// One cell of a row under a device, sized like the device row's own cell (GithubRow's LinkCell
// sizes them by the heading's boxWidth)
export const childCell = (header, content, clickable) => (
    <div style={{ width: header.boxWidth, cursor: clickable ? "pointer" : undefined }}>{content}</div>
)

// A row under a device, as a row of the device table itself so its cells line up with the device
// row's columns. cells holds one entry per table heading; the table's own selection column stays
// empty (a collection's checkbox sits in the expand-arrow column, see CollectionCheckbox).
export function ChildRow({ cells, onClick, selected }) {
    return (
        <tr
            className={`Polaris-IndexTable__TableRow${selected ? " Polaris-IndexTable__TableRow--selected" : ""}`}
            onClick={onClick}
            style={{ cursor: onClick ? "pointer" : undefined }}
        >
            <td className="Polaris-IndexTable__TableCell Polaris-IndexTable__TableCell--first" />
            {cells.map((cell, index) => <td key={index} className="Polaris-IndexTable__TableCell">{cell}</td>)}
        </tr>
    )
}

// A collection's checkbox, right-aligned in the expand-arrow column like the device row's arrow.
// Disabled (and checked) while the whole device is selected.
export function CollectionCheckbox({ collectionId, parentRowId }) {
    const { selectedItems, selectItems } = useTable()
    const parentSelected = selectedItems.includes(parentRowId)
    const checked = parentSelected || selectedItems.includes(collectionId)

    const toggle = () => {
        const next = checked
            ? selectedItems.filter(x => x !== collectionId)
            : [...selectedItems, collectionId]
        selectItems(next)
        TableStore.getState().setSelectedItems(next)
    }

    return (
        <div onClick={(e) => e.stopPropagation()}>
            <HorizontalStack align="end" wrap={false}>
                <Checkbox label="Select collection" labelHidden checked={checked} disabled={parentSelected} onChange={toggle} />
            </HorizontalStack>
        </div>
    )
}
