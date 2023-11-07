import { Box, Icon, TextField } from '@shopify/polaris'
import {SearchMinor} from "@shopify/polaris-icons"
import React, { useState } from 'react'

function SearchField(props) {

    const [searchValue, setSearchValue] = useState("")
    const SearchIcon =  (
        <Box>
            <Icon source={SearchMinor} />   
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
            prefix={SearchIcon} 
            onChange={searchResult} 
            value={searchValue}
            placeholder={props.placeholder}
        />
    )
}

export default SearchField