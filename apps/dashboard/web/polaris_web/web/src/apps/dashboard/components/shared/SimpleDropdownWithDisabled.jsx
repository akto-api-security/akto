import { Autocomplete, Icon, Link, TextContainer } from '@shopify/polaris';
import { ChevronDownMinor } from '@shopify/polaris-icons';
import React, { useState, useCallback, useEffect } from 'react';

function SimpleDropdownWithDisabled(props) {
    const id = props.id ? props.id : "simple-dropdown"

    const {
        disabled,
        label,
        placeholder,
        optionsList,
        setSelected,
        value,
        preSelected,
        itemName,
        disabledOptions=[]
    } = props

    const deselectedOptions = optionsList.map(option => ({
        ...option,
        disabled: disabledOptions.includes(option.value)
    }));

    const [selectedOptions, setSelectedOptions] = useState(preSelected ? preSelected : []);
    const [inputValue, setInputValue] = useState(value ? value : '');
    const [checked, setChecked] = useState(false);

    useEffect(() => {
        if (value !== undefined) {
            setInputValue(value);
        }

        if (preSelected !== undefined) {
            setSelectedOptions(preSelected);
        }

        if (preSelected) {
            setChecked(preSelected.length === optionsList.length);
        }
    }, [value, preSelected, optionsList]);

    const updateSelection = useCallback(
        (selected) => {
            const filteredSelected = selected.filter(
                selectedItem => !disabledOptions.includes(selectedItem)
            );

            const selectedText = filteredSelected.map((selectedItem) => {
                const matchedOption = optionsList.find((option) => {
                    if (typeof option.value === "string")
                        return option.value.match(selectedItem);
                    else
                        return option.value === selectedItem
                });
                return matchedOption && matchedOption.label;
            });

            setSelectedOptions(filteredSelected);

            if (selectedText.length === optionsList.length) {
                setInputValue("All items selected");
            } else {
                setInputValue(`${filteredSelected.length} ${itemName ? itemName : "item"}${filteredSelected.length === 1 ? "" : "s"} selected`);
            }

            setSelected(filteredSelected);
        },
        [optionsList, disabledOptions, itemName, setSelected],
    );

    const selectAllFunc = useCallback(() => {
        if (!checked) {
            const valueArr = deselectedOptions
                .filter(opt => !opt.disabled)
                .map(opt => opt.value);

            updateSelection(valueArr);
            setChecked(true);
        } else {
            updateSelection([]);
            setChecked(false);
        }
    }, [checked, deselectedOptions, updateSelection]);

    const textField = (
        <Autocomplete.TextField
            id={id}
            disabled={disabled}
            label={label}
            value={inputValue}
            suffix={<Icon source={ChevronDownMinor} color="base" />}
            placeholder={placeholder}
            autoComplete="off"
        />
    );

    const showSelectAll = optionsList.length > 5;
    const checkboxLabel = checked ?
        <Link removeUnderline>Deselect all</Link> :
        <Link removeUnderline>Select all</Link>;

    const emptyState = (
        <div style={{ textAlign: 'center' }}>
            <TextContainer>No options available</TextContainer>
        </div>
    );

    const renderOption = (option, isSelected) => {
        const { label, disabled } = option;

        if (disabled) {
            return (
                <div
                    style={{
                        opacity: 0.5,
                        cursor: 'not-allowed',
                        backgroundColor: '#f4f6f8',
                        padding: '8px',
                        color: '#637381'
                    }}
                >
                    {label} (already selected)
                </div>
            );
        }

        return undefined;
    };

    return (
        <Autocomplete
            allowMultiple={true}
            options={[{title: "Options", options: deselectedOptions}]}
            selected={selectedOptions}
            onSelect={updateSelection}
            emptyState={emptyState}
            textField={textField}
            preferredPosition='below'
            {...(showSelectAll ? {actionBefore: {
                content: checkboxLabel,
                onAction: selectAllFunc,
            }} : {})}
            renderOption={renderOption}
        />
    );
}

export default SimpleDropdownWithDisabled;
