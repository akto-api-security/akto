const transform = {
    modifyItemsGroup(it): itemGroups.forEach(itemGroup => {

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
}

export default transform