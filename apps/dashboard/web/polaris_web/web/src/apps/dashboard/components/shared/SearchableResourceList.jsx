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

  // items can arrive after the first render (e.g. collections still loading)
  useEffect(() => {
    if (value === '') setResourceItems(items)
  }, [items])

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
        // plain text search: characters like ( or * in a name must not break it
        const filterRegex = new RegExp(item.replace(/[.*+?^${}()|[\]\\]/g, '\\$&'), 'i')
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