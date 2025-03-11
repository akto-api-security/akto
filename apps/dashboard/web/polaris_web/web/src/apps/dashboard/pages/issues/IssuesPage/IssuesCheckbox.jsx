import { Checkbox } from '@shopify/polaris'
import React, { useState } from 'react'
import TableStore from '../../../components/tables/TableStore'
import useTable from '../../../components/tables/TableContext'

const IssuesCheckbox = ({id}) => {
    const {selectItems} = useTable()
    const selectedItems = TableStore.getState().selectedItems.flat()
    const initialVal = selectedItems.includes(id)
    const [checked, setChecked] = useState(initialVal)

    const handleChange = () => {
        const selectedItems = TableStore.getState().selectedItems.flat()
        const newCheckedState = !checked

        let newSelectedItems
        if (newCheckedState) {
            newSelectedItems = [...new Set([...selectedItems, id])]
        } else {
            newSelectedItems = selectedItems.filter(item => item !== id)
        }

        TableStore.getState().setSelectedItems(newSelectedItems)
        selectItems(newSelectedItems)
        setChecked(newCheckedState)
    }

    return (
        <Checkbox
            checked={checked}
            onChange={handleChange}
        />
    )
}

export default IssuesCheckbox