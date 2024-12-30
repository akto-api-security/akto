import { Autocomplete, Avatar, Checkbox, HorizontalStack, Icon, Link, Text, TextContainer } from '@shopify/polaris';
import { SearchMinor, ChevronDownMinor } from '@shopify/polaris-icons';
import React, { useState, useCallback, useEffect } from 'react';
import func from "@/util/func";
function DropdownSearch(props) {

    const id = props.id ? props.id : "dropdown-search"

    const { disabled, label, placeholder, optionsList, setSelected, value , avatarIcon, preSelected, allowMultiple, itemName, dropdownSearchKey, isNested, sliceMaxVal, dynamicTitle} = props

    const deselectedOptions = optionsList
    const [selectedOptions, setSelectedOptions] = useState(preSelected ? preSelected : []);
    const [inputValue, setInputValue] = useState(value ? value : undefined);
    const [options, setOptions] = useState(deselectedOptions);
    const [loading, setLoading] = useState(false);
    const [checked,setChecked] = useState(false)


    useEffect(() => {
        if(value!=undefined){
            setInputValue((prev) => {
                if(prev == value){
                    return prev;
                }
                return value;
            });
        }
        if(preSelected!=undefined){
            setSelectedOptions((prev) => {
                if(func.deepComparison(prev,preSelected)){
                    return prev;
                }
                return [...preSelected];
            });

        }
        setOptions((prev) => {
            if(selectedOptions.length > 0 || prev.length > 0){
                return prev;
            }
            return deselectedOptions;
        })

        if(allowMultiple){
            let totalItems = deselectedOptions.length
            if(isNested){
                totalItems = 0
                deselectedOptions.forEach((opt) => {
                    totalItems += opt.options.length
                })
            }
            if(preSelected.length === totalItems){
                setChecked(true)
            }else{
                setChecked(false)
            }
        }
    }, [deselectedOptions, value, preSelected])

    const updateText = useCallback(
        (value) => {
            setInputValue(value);

            if (!loading) {
                setLoading(true);
            }

            setTimeout(() => {
                if (value === '' && selectedOptions.length === 0) {
                    setOptions(deselectedOptions);
                    setLoading(false);
                    return;
                }
                const filterRegex = new RegExp(value, 'i');
                const searchKey = dropdownSearchKey ? dropdownSearchKey : "label"
                let resultOptions = []
                if(isNested){
                    deselectedOptions.forEach((opt) => {
                        const options = opt.options.filter((option) =>
                          option[searchKey].match(filterRegex),
                        );
                
                        resultOptions.push({
                          title: dynamicTitle ? `Showing ${options.length} result${func.addPlurality(options.length)} (type more to refine results)` : opt.title,
                          options,
                        });
                      });
                }else{
                    resultOptions = deselectedOptions.filter((option) =>
                    option[searchKey].match(filterRegex)
                );
                }
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

    const selectAllFunc = () => {
        if(!checked){
            let valueArr = []
            if(isNested){
                deselectedOptions.forEach((opt) => {
                    opt.options.forEach((option) =>
                      valueArr.push(option.value)
                    );
                })
            }else{
                deselectedOptions.map((opt) => valueArr.push(opt.value))
            }
            updateSelection(valueArr)
            setChecked(true)
        }else{
            setChecked(false)
            updateSelection([])
        }
        
    }

    const textField = (
        <Autocomplete.TextField
            id={id}
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

    const showSelectAll = (allowMultiple && optionsList.length > 5)
    const checkboxLabel = checked ? <Link removeUnderline>Deselect all</Link> : <Link removeUnderline>Select all</Link>

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
                options={options.slice(0,sliceMaxVal || 20)}
                selected={selectedOptions}
                onSelect={updateSelection}
                emptyState={emptyState}
                loading={loading}
                textField={textField}
                preferredPosition='below'
                {...(showSelectAll ? {actionBefore:{
                    content: checkboxLabel,
                    onAction: () => selectAllFunc(),
                }} : {})}
            >
            </Autocomplete>
    );
}

export default DropdownSearch
