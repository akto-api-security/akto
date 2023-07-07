import { Autocomplete, Box, Icon, Text } from '@shopify/polaris';
import React, { useCallback, useMemo, useState } from 'react'
import {SearchMinor} from "@shopify/polaris-icons"

function SearchWithList(props) {

    const deselectedOptions = useMemo(() => props.searchItems,[props.searchItems],)
    const [inputValue, setInputValue] = useState('');
    const [options, setOptions] = useState(deselectedOptions);

    const updateText = useCallback(value => {
          setInputValue(value);
    
          if (value === '') {
            setOptions(deselectedOptions);
            return;
          }
    
          const filterRegex = new RegExp(value, 'i');
          const resultOptions = deselectedOptions.filter((option) =>
            option.label.match(filterRegex),
          );
          setOptions(resultOptions);
        },
        [deselectedOptions],
      );
        const updateSelection = (selected) =>{
            props.getSelected(selected[0])
        }

      const textField = (
        <Autocomplete.TextField
          onChange={updateText}
          value={inputValue}
          prefix={<Icon source={SearchMinor} color="base" />}
          placeholder={`Search from ${props.searchItems.length} collections available.`}
          autoComplete="off"
          {...(props.connectedRight ? {connectedRight: props.connectedRight} : {})}
        />
      );

      const emptyState = (
        <Box>
            <Icon source={SearchMinor} />
            <div style={{textAlign: 'center'}}>
                <Text>Search string did not match any results.</Text>
            </div>
        </Box>
      )

      const searchWithList = (
        <Autocomplete
            options={options}
            selected={options}
            onSelect={updateSelection}
            textField={textField}
            emptyState={emptyState}
        />
      )

  return (
    searchWithList
  )
}

export default SearchWithList