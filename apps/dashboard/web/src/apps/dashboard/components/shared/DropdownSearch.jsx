import { Autocomplete, Avatar, Icon, TextContainer } from '@shopify/polaris';
import { SearchMinor, ChevronDownMinor } from '@shopify/polaris-icons';
import React, { useState, useCallback, useEffect } from 'react';
import func from "@/util/func";
function DropdownSearch({ disabled, label, placeholder, optionsList, setSelected, value , avatarIcon, preSelected, allowMultiple, itemName}) {

    const deselectedOptions = optionsList
    const [selectedOptions, setSelectedOptions] = useState(preSelected ? preSelected : []);
    const [inputValue, setInputValue] = useState(value ? value : undefined);
    const [options, setOptions] = useState(deselectedOptions);
    const [loading, setLoading] = useState(false);


    useEffect(() => {
        setOptions((prev) => {
            if(func.deepComparison(prev,deselectedOptions)){
                return prev;
            }
            setInputValue(value ? value : undefined);
            setSelectedOptions(preSelected ? preSelected : []);
            return deselectedOptions;
        })
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

    const handleFocusEvent = () => {
        updateText('');
    }

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
            setSelectedOptions([...selected]);

            if (avatarIcon) {
                setInputValue(selected[0])
            } else if (allowMultiple) {
                setInputValue(`${selected.length} ${itemName ? itemName : "item"}${selected.length == 1 ? "" : "s"} selected`)
            }
            else {
                setInputValue(selectedText[0] || '');
            }

            if (allowMultiple) {
                setSelected(selected);
            } else {
                setSelected(selected[0])
            }
        },
        [options],
    );

    const textField = (
        <Autocomplete.TextField
            disabled={disabled}
            onChange={updateText}
            label={label}
            value={inputValue}
            prefix={
                <div style={{display: 'flex', gap: '4px', alignItems: 'center'}}>
                    <Icon source={SearchMinor} color="base" />
                    {avatarIcon && avatarIcon.length > 0 ? <Avatar customer size="extraSmall" name={avatarIcon} source={avatarIcon}/> : null}
                </div>
            }
            suffix={<Icon source={ChevronDownMinor} color="base" />}
            placeholder={placeholder}
            autoComplete="off"
            onFocus={handleFocusEvent}
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
                {...(allowMultiple ? {allowMultiple:true} : {} )}
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
