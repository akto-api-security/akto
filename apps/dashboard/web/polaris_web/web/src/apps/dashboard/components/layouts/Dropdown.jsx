import { Autocomplete, Box, Icon } from '@shopify/polaris'
import React, { useCallback, useEffect, useMemo, useState } from 'react'
import {CircleRightMajor, ChevronDownMinor} from "@shopify/polaris-icons"
import func from "@/util/func"

function Dropdown(props) {

    const id = props.id ? props.id : "dropdown";

    const deselectedOptions = useMemo(() => props.menuItems,[props.menuItems],);
    const [selectedOptions, setSelectedOptions] = useState([]);
    const [inputValue, setInputValue] = useState(props.initial);
    const [options, setOptions] = useState(deselectedOptions);

    const updateSelection = useCallback(selected => {
        const selectedValue = selected.map((selectedItem) => {
            const matchedOption = options.find((option) => {
                if (typeof option.value === "string")
                    return option.value.match(selectedItem);
                else 
                    return option.value === selectedItem
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
                setInputValue((prev) => {
                    if(prev == element.label){
                        return prev
                    }
                    return element.label;
                })
                let arr = [id]
                setSelectedOptions((prev) => {
                    if(func.deepComparison(prev, arr)){
                        return arr;
                    }
                    return arr;
                })
            }
        });
    }

    useEffect(()=>{
        getLabel(props.initial)
        setOptions(deselectedOptions)
    },[deselectedOptions, props.initial])

    const textField = (
        <Autocomplete.TextField
            id={id}
            value={inputValue}
            autoComplete="off"
            {...props.label ? {label : props.label} : null}
            {...props.helpText ? {helpText : props.helpText} : null}
            {...props.placeHolder ? {placeholder : props.placeHolder} : null}
            suffix={<Icon source={ChevronDownMinor} />}
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
                onAction: props.subClick,
                suffix: <Box><Icon source={CircleRightMajor}/></Box>
            }} : null}
        />
    )
}

export default Dropdown