import { Box, Icon, ResourceList, TextField } from '@shopify/polaris'
import {SearchMinor} from "@shopify/polaris-icons"
import React, { useEffect, useState } from 'react'

function SearchableResourceList({ resourceName, items, renderItem, loading, isFilterControlEnabale, selectable, onSelectedItemsChange, alreadySelectedItems }) {
  /*
    While implementing renderItem, make sure the ResourceItem has a key prop passed to it to avoid rendering issues.
  */
  
  const [value, setValue] = useState('')
  const [selectedItems, setSelectedItems] = useState(alreadySelectedItems || [])
  const [resourceItems, setResourceItems] = useState(items)

  useEffect(() => {
    setResourceItems(items)
  }, [])

  useEffect(() => {
    if(onSelectedItemsChange) {
      onSelectedItemsChange(selectedItems)
    }
  }, [selectedItems])

  const searchResult = (item) => {
    setValue(item)
    if(item === '') {
      setResourceItems(items)
    } else {
        const filterRegex = new RegExp(item, 'i')
        const resultOptions = items.filter((option) => {
            if(option.name) {
              return option.name.match(filterRegex)
            } else if(option.login) {
              return option.login.match(filterRegex)
            } else if(option.collectionName) {
              return option.collectionName.match(filterRegex)
            }
          }
        );

        setResourceItems(resultOptions)
    }
  }

  const onSelectionHandler = (items) => {
    setSelectedItems(items)
  }

  const filterControlComp = (
    <TextField
			prefix={
        <Box>
          <Icon source={SearchMinor} />   
        </Box>
      }
			onChange={searchResult}
			value={value}
			placeholder={`Search ${resourceName}`}
		/>
  )

  const isSelectable = (
    selectable ? {
      selectable: true,
      selectedItems: selectedItems,
      onSelectionChange: (items) => onSelectionHandler(items)
    } : {
      selectable: false
    }
  )

  return (
    <ResourceList
      resourceName={{ singular: resourceName, plural: `${resourceName}s` }}
      items={resourceItems}
      renderItem={renderItem}
      loading={loading}
      filterControl={isFilterControlEnabale ? filterControlComp : undefined}
      {...isSelectable}
    />
  )
}

export default SearchableResourceList