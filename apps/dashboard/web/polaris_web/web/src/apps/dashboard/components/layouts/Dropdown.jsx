import { Autocomplete, Box, Icon } from '@shopify/polaris'
import React, { useCallback, useEffect, useMemo, useState } from 'react'
import {CircleRightMajor, ChevronDownMinor} from "@shopify/polaris-icons"
import func from "@/util/func"

function Dropdown(props) {

    const id = props.id ? props.id : "dropdown";

    const deselectedOptions = useMemo(() => {
        if (!props.menuItems || !Array.isArray(props.menuItems)) {
            return [];
        }

        return props.menuItems.map(option => ({
            ...option,
            disabled: props?.disabledOptions?.includes?.(option.value) || false
        }));
    }, [props.menuItems, props.disabledOptions]);


    const [selectedOptions, setSelectedOptions] = useState(props.preSelected || []);
    const [inputValue, setInputValue] = useState(props.value || props.initial || '');
    const [options, setOptions] = useState(deselectedOptions || []);


    const updateSelection = useCallback(
        (selected) => {
            const filteredSelected = selected.filter(selectedItem => {
                if (!props.disabledOptions || !Array.isArray(props.disabledOptions)) {
                    return true;
                }
                return !props.disabledOptions.includes(selectedItem);
            });

            const selectedText = filteredSelected.map((selectedItem) => {
                const matchedOption = options.find((option) => {
                    if (typeof option.value === "string")
                        return option.value.match(selectedItem);
                    else
                        return option.value === selectedItem
                });
                return matchedOption && matchedOption.label;
            });

            setSelectedOptions([...filteredSelected]);

            if (props?.allowMultiple) {
                if (props?.showSelectedItemLabels) {
                    if (selectedText.length === (props.menuItems?.length || 0)) {
                        setInputValue("All items selected");
                    } else if (typeof func.getSelectedItemsText === 'function') {
                        setInputValue(func.getSelectedItemsText(selectedText));
                    } else {
                        setInputValue(`${filteredSelected.length} selected`);
                    }
                } else {
                    setInputValue(`${filteredSelected.length} ${props?.itemName ? props?.itemName : "item"}${filteredSelected.length === 1 ? "" : "s"} selected`);
                }
            } else {
                setInputValue(selectedText[0] || '');
            }

            if (props?.allowMultiple) {
                props.selected(filteredSelected);
            } else {
                props.selected(filteredSelected[0]);
            }
        },
        [options, props.menuItems, props.disabledOptions, props.showSelectedItemLabels, props.itemName, props.allowMultiple],
    );

    const getLabel = (id) => {
        if (!props.menuItems || !Array.isArray(props.menuItems)) {
            return;
        }
        const matchingElement = props.menuItems.find(element => element.value === id);

        if (matchingElement) {
            setInputValue((prev) => {
                if (prev === matchingElement.label) {
                    return prev;
                }
                return matchingElement.label;
            });

            const arr = [id];
            setSelectedOptions((prev) => {
                if (func.deepComparison && typeof func.deepComparison === 'function' && func.deepComparison(prev, arr)) {
                    return arr;
                }
                return arr;
            });
        }
    }



    useEffect(() => {
        if (props.initial) {
            getLabel(props.initial);
        }

        setOptions(deselectedOptions);

        if (props.preSelected !== undefined) {
            setSelectedOptions(props.preSelected);
        }

        if (props.value !== undefined) {
            setInputValue(props.value);
        }


    }, [deselectedOptions, props.initial, props.preSelected, props.value, props.menuItems, props.allowMultiple])

    const textField = (
        <Autocomplete.TextField
            id={id}
            disabled={props?.disabled || false}
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
            {...props?.allowMultiple === true? {allowMultiple:true} : {}}
            {...(props.subItems ? {actionBefore:{
                content: props.subContent,
                wrapOverflow: true,
                onAction: props.subClick,
                suffix: <Box><Icon source={CircleRightMajor}/></Box>
            }} : null)}
        />
    )
}

export default Dropdown