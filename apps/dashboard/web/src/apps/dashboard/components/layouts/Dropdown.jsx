import { Autocomplete, Box, Icon } from '@shopify/polaris'
import React, { useCallback, useEffect, useMemo, useState } from 'react'
import {CircleRightMajor, SelectMinor} from "@shopify/polaris-icons"

function Dropdown(props) {
    const deselectedOptions = useMemo(() => props.menuItems,[props.menuItems],);
    const [selectedOptions, setSelectedOptions] = useState([]);
    const [inputValue, setInputValue] = useState(props.initial);
    const [options, setOptions] = useState(deselectedOptions);

    const updateSelection = useCallback(selected => {
        const selectedValue = selected.map((selectedItem) => {
            const matchedOption = options.find((option) => {
                return option.value.match(selectedItem);
            });
            return matchedOption && matchedOption.label;
        });
        props.selected(selected[0])
        setSelectedOptions(selected);
        setInputValue(selectedValue[0]);
    },[options]);

    const getLabel  = (id) => {
        props.menuItems.forEach(element => {
            if(element.value === id){
                setInputValue(element.label)
                let arr = [id]
                setSelectedOptions(arr)
            }
        });
    }

    useEffect(()=>{
        getLabel(props.initial)
        setOptions(deselectedOptions)
    },[deselectedOptions])

    const textField = (
        <Autocomplete.TextField
            value={inputValue}
            autoComplete="off"
            {...props.label ? {label : props.label} : null}
            {...props.helpText ? {helpText : props.helpText} : null}
            {...props.placeHolder ? {placeholder : props.placeHolder} : null}
            suffix={<Icon source={SelectMinor} />}
        />

    );
    return (
        <Autocomplete
            options={options}
            selected={selectedOptions}
            onSelect={updateSelection}
            textField={textField}
            preferredPosition='below'
            {...props.subItems ? {actionBefore:{
                content: props.subContent,
                wrapOverflow: true,
                onAction: props.subclick,
                suffix: <Box><Icon source={CircleRightMajor}/></Box>
            }} : null}
        />
    )
}

export default Dropdown