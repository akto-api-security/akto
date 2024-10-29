import React, { useState } from 'react'
import useTable from '../../../components/tables/TableContext'
import TableStore from '../../../components/tables/TableStore'
import { Checkbox } from '@shopify/polaris'

const ActiveTestingCheckbox = ({ testName, testId, setSelectedTests }) => {
    const {selectItems} = useTable()
    const selectedItems = TableStore.getState().selectedItems.flat()
    const initialVal = selectedItems.includes(testId)
    const [checked, setChecked] = useState(initialVal)

    const handleChange = () => {
        const selectedItems = TableStore.getState().selectedItems.flat()
        const newCheckedState = !checked

        let newSelectedItems
        if (newCheckedState) {
            newSelectedItems = [...new Set([...selectedItems, testId])]
        } else {
            newSelectedItems = selectedItems.filter(item => item !== testId)
        }

        setSelectedTests(newSelectedItems)

        TableStore.getState().setSelectedItems(newSelectedItems)
        selectItems(newSelectedItems)
        setChecked(newCheckedState)
    }

    return (
        <Checkbox
            label={testName}
            checked={checked}
            onChange={handleChange}
        />
    )
}

export default ActiveTestingCheckbox