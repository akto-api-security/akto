import { Checkbox } from '@shopify/polaris'
import React, { useState } from 'react'
import TableStore from '../../../components/tables/TableStore'
import useTable from '../../../components/tables/TableContext'

const IssuesCheckbox = ({id, selectedTestRunForRerun = null, handleChangeFromProp}) => {
    const {selectItems} = useTable()
    const selectedItems = TableStore.getState().selectedItems.flat()
    const initialVal = selectedItems.includes(id)
    console.log("initialVal",initialVal)
    const [checked, setChecked] = useState(initialVal)

    const handleChange = () => {
        if (selectedTestRunForRerun !== null) {
            handleChangeFromProp(id,!checked)
            setChecked(!checked)
            const selectedItems = TableStore.getState().selectedItems.flat()
            const newCheckedState = !checked
    
            let newSelectedItems
            if (newCheckedState) {
                newSelectedItems = [...new Set([...selectedItems, id])]
            } else {
                newSelectedItems = selectedItems.filter(item => item !== id)
            }
    
            console.log("newSelectedItems",newSelectedItems)
            TableStore.getState().setSelectedItems(newSelectedItems)
            selectItems(newSelectedItems)
        } else {
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
    }

    return (
        <Checkbox
            checked={checked}
            onChange={handleChange}
        />
    )
}

export default IssuesCheckbox