import { Box, Icon, TextField } from '@shopify/polaris'
import { SearchIcon } from "@shopify/polaris-icons";
import React, { useState } from 'react'

function SearchField(props) {

    const [searchValue, setSearchValue] = useState("")
    const SearchFieldIcon =  (
        <Box>
            <Icon source={SearchIcon} />   
        </Box>
    )

    const searchResult = (item) =>{
        setSearchValue(item)
        const filterRegex = new RegExp(item, 'i');
        const resultOptions = props.items.filter((option) =>
            option.label.match(filterRegex)
        );
        
        props.getSearchedItems(resultOptions)
    }

    return (
        <TextField  
            prefix={SearchFieldIcon} 
            onChange={searchResult} 
            value={searchValue}
            placeholder={props.placeholder}
        />
    )
}

export default SearchField