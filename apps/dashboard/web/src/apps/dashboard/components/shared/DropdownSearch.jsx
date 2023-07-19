import { Autocomplete, Icon, TextContainer } from '@shopify/polaris';
import { SearchMinor, ChevronDownMinor } from '@shopify/polaris-icons';
import React, { useState, useCallback, useMemo, useEffect } from 'react';

function DropdownSearch({ disabled, label, placeholder, optionsList, setSelected }) {

    const deselectedOptions = optionsList

    const [selectedOptions, setSelectedOptions] = useState([]);
    const [inputValue, setInputValue] = useState('');
    const [options, setOptions] = useState(deselectedOptions);
    const [loading, setLoading] = useState(false);

    useEffect(() => {
        setOptions(deselectedOptions)
    }, [deselectedOptions])

    const updateText = useCallback(
        (value) => {
            setInputValue(value);

            if (!loading) {
                setLoading(true);
            }

            setTimeout(() => {
                if (value === '') {
                    setOptions(deselectedOptions);
                    setLoading(false);
                    return;
                }
                const filterRegex = new RegExp(value, 'i');
                const resultOptions = deselectedOptions.filter((option) =>
                    option.label.match(filterRegex),
                );
                setOptions(resultOptions);
                setLoading(false);
            }, 300);
        },
        [deselectedOptions, loading],
    );

    const updateSelection = useCallback(
        (selected) => {
            const selectedText = selected.map((selectedItem) => {
                const matchedOption = options.find((option) => {  
                    if (typeof option.value === "string")
                        return option.value.match(selectedItem);
                    else 
                        return option.value === selectedItem
                });
                return matchedOption && matchedOption.label;
            });
            setSelectedOptions(selected);
            setInputValue(selectedText[0] || '');
            setSelected(selected[0])
        },
        [options],
    );

    const textField = (
        <Autocomplete.TextField
            disabled={disabled}
            onChange={updateText}
            label={label}
            value={inputValue}
            prefix={<Icon source={SearchMinor} color="base" />}
            suffix={<Icon source={ChevronDownMinor} color="base" />}
            placeholder={placeholder}
            autoComplete="off"
        />
    );

    const emptyState = (
        <React.Fragment>
            <Icon source={SearchMinor} />
            <div style={{ textAlign: 'center' }}>
                <TextContainer>Could not find any results</TextContainer>
            </div>
        </React.Fragment>
    );

    return (
            <Autocomplete
                options={options}
                selected={selectedOptions}
                onSelect={updateSelection}
                emptyState={emptyState}
                loading={loading}
                textField={textField}
                preferredPosition='below'
            />
    );
}

export default DropdownSearch
