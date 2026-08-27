import { Autocomplete, Avatar, Icon, Link, TextContainer, Popover, TextField, Listbox, Box, Checkbox } from '@shopify/polaris';
import { SearchMinor, ChevronDownMinor } from '@shopify/polaris-icons';
import React, { useState, useCallback, useEffect } from 'react';
import func from "@/util/func";

// Include-mode "select all" sentinel: stored as the sole value, means "match present + future" while staying Include (mirrors an empty Exclude bucket on the backend).
// Must match wildcardAllServers in guardrails-service/pkg/validator/service.go exactly — this is the value sent over the wire.
// Not "*" — updateSelection below does option.value.match(selectedItem), and a bare "*" is an invalid regex (crashes).
export const ALL_VALUES_SENTINEL = '__all__';

function DropdownSearch(props) {

    const id = props.id ? props.id : "dropdown-search"

    const { disabled, label, placeholder, optionsList, setSelected, value , avatarIcon, preSelected, allowMultiple, itemName, dropdownSearchKey, isNested, sliceMaxVal, showSelectedItemLabels=false, searchDisable=false, textfieldRequiredIndicator=false, showSelectAllMinOptions=5, headerContent, negated=false, onToggleNegated} = props

    const deselectedOptions = optionsList
    const [selectedOptions, setSelectedOptions] = useState(preSelected ? preSelected : []);
    const [inputValue, setInputValue] = useState(value ? value : undefined);
    const [options, setOptions] = useState(deselectedOptions);
    const [loading, setLoading] = useState(false);
    const [checked,setChecked] = useState(false)
    // Used only by the headerContent branch below (self-managed popover, see there)
    const [popoverActive, setPopoverActive] = useState(false)
    useEffect(() => {
        if(value!==undefined){
            setInputValue((prev) => {
                if(prev === value){
                    return prev;
                }
                return value;
            });
        }
        if(preSelected!==undefined){
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
            if (onToggleNegated) {
                // Negation-aware row: "all" is negated=true with nothing excluded, or Include with just the wildcard sentinel — either way present + future, not a full snapshot.
                setChecked(negated
                    ? preSelected.length === 0
                    : (preSelected.length === 1 && preSelected[0] === ALL_VALUES_SENTINEL))
            } else if(preSelected.length === totalItems){
                setChecked(true)
            }else{
                setChecked(false)
            }
        }
    }, [deselectedOptions, value, preSelected, negated])

    // Exclude mode counts what's excluded, not what's included, so "0 selected" reads as "All"
    const negatedText = (count) => count === 0 ? 'All selected' : `All except ${count}`;

    const updateText = useCallback(
        (value) => {
            setInputValue(value);

            if (!loading) {
                setLoading(true);
            }

            const defaultSliceValue = sliceMaxVal || 20

            setTimeout(() => {
                if (value === '' && selectedOptions.length === 0) {
                    const options = deselectedOptions.slice(0, defaultSliceValue);
                    const title = deselectedOptions.length != defaultSliceValue && options.length >= defaultSliceValue
                        ? `Showing ${options.length} result${func.addPlurality(options.length)} only. (type more to refine results)`
                        : "Showing all results";
                    const nestedOptions = [{
                        title: title,
                        options: options
                    }]
                    setOptions(nestedOptions);
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
                          title: opt.title,
                          options,
                        });
                      });
                }else{
                    resultOptions = deselectedOptions.filter((option) =>
                        option[searchKey].match(filterRegex)
                    ).slice(0, defaultSliceValue);

                    const title = deselectedOptions.length !== defaultSliceValue && resultOptions.length >= defaultSliceValue
                        ? `Showing ${resultOptions.length} result${func.addPlurality(resultOptions.length)} only. (type more to refine results)`
                        : "Showing all results";

                    resultOptions = [{
                        title: title,
                        options: resultOptions
                    }]
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
                let optionsArr = Array.isArray(options?.[0]?.options) ? options?.[0]?.options : options
                const matchedOption = optionsArr.find((option) => {  
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
                if (negated) {
                    setInputValue(negatedText(selected.length));
                }
                else if(showSelectedItemLabels) {
                    if(selectedText.length === optionsList.length) setInputValue("All items selected");
                    else setInputValue(func.getSelectedItemsText(selectedText))
                }
                else setInputValue(`${selected.length} ${itemName ? itemName : "item"}${selected.length == 1 ? "" : "s"} selected`)
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
        if (onToggleNegated) {
            // Present + future, without switching tabs: Exclude nothing (negated) or the wildcard sentinel (Include).
            // "Deselect all" from either always resets to a blank Include row — there's no meaningful "opposite" of Exclude-nothing.
            if (checked) {
                onToggleNegated(false);
                updateSelection([]);
            } else if (negated) {
                updateSelection([]);
            } else {
                updateSelection([ALL_VALUES_SENTINEL]);
            }
            return;
        }
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
            {...(!searchDisable ? {onChange:updateText}:{})}
            label={label}
            value={inputValue}
            {...(!searchDisable ? { 
                prefix: (
                    <div style={{ display: 'flex', gap: '4px', alignItems: 'center' }}>
                        <Icon source={SearchMinor} color="base" />
                        {avatarIcon && avatarIcon.length > 0 ? <Avatar customer size="extraSmall" name={avatarIcon} source={avatarIcon} /> : null}
                    </div>
                ) 
            } : {})}
            suffix={
                <span
                    onClick={() => document.getElementById(id)?.focus()}
                    style={{ cursor: 'pointer', display: 'flex', alignItems: 'center' }}
                >
                    <Icon source={ChevronDownMinor} color="base" />
                </span>
            }
            placeholder={placeholder}
            autoComplete="off"
            requiredIndicator={textfieldRequiredIndicator}
            {...(!searchDisable? {onFocus:handleFocusEvent}: {})}
        />
    );

    const showSelectAll = (allowMultiple && optionsList.length >= showSelectAllMinOptions)
    const checkboxLabel = checked ? <Link removeUnderline>Deselect all</Link> : <Link removeUnderline>Select all</Link>

    const emptyState = (
        <React.Fragment>
            <Icon source={SearchMinor} />
            <div style={{ textAlign: 'center' }}>
                <TextContainer>Could not find any results</TextContainer>
            </div>
        </React.Fragment>
    );

    // headerContent needs to render inside the option-list popover; Autocomplete has no slot for that, so build the popover from Listbox instead (same primitive Autocomplete uses internally).
    if (headerContent) {
        const sections = (options[0]?.options ? options : [{ title: '', options }]).slice(0, sliceMaxVal || 20);
        const noResults = !loading && sections.every(s => (s.options || []).length === 0);

        const popoverTextField = (
            <TextField
                id={id}
                disabled={disabled}
                {...(!searchDisable ? { onChange: updateText } : {})}
                label={label}
                value={inputValue}
                {...(!searchDisable ? {
                    prefix: (
                        <div style={{ display: 'flex', gap: '4px', alignItems: 'center' }}>
                            <Icon source={SearchMinor} color="base" />
                            {avatarIcon && avatarIcon.length > 0 ? <Avatar customer size="extraSmall" name={avatarIcon} source={avatarIcon} /> : null}
                        </div>
                    )
                } : {})}
                suffix={<Icon source={ChevronDownMinor} color="base" />}
                // Focusing clears the field to search — keep showing the current selection as a
                // (grayed) placeholder instead of the generic hint, so it isn't lost while searching.
                placeholder={value || placeholder}
                autoComplete="off"
                requiredIndicator={textfieldRequiredIndicator}
                onFocus={() => { setPopoverActive(true); if (!searchDisable) handleFocusEvent(); }}
            />
        );

        return (
            <>
                <style>{`#${id} { cursor: pointer; }`}</style>
                <Popover
                    active={popoverActive}
                    activator={popoverTextField}
                    onClose={() => setPopoverActive(false)}
                    preferredPosition="below"
                    fullWidth
                    // Measure the whole TextField, not just the inner <input> (default), so fullWidth matches the field exactly
                    preferInputActivator={false}
                >
                    <Popover.Pane fixed>
                        {headerContent}
                        {showSelectAll && (
                            // Click target is the link itself, not the whole row — matches Listbox.Action's own footprint
                            <div style={{ padding: '10px 12px', borderBottom: '1px solid var(--p-border-subdued, #e1e3e5)' }}>
                                <Link removeUnderline onClick={selectAllFunc}>{checked ? 'Deselect all' : 'Select all'}</Link>
                            </div>
                        )}
                    </Popover.Pane>
                    <Popover.Pane>
                        <Listbox
                            accessibilityLabel={placeholder}
                            onSelect={(val) => {
                                if (allowMultiple) {
                                    const isWildcardAll = onToggleNegated && !negated && selectedOptions.length === 1 && selectedOptions[0] === ALL_VALUES_SENTINEL;
                                    if (isWildcardAll) {
                                        // Carving an exception out of "Include: everyone" only exists in Exclude mode — switch to it with just this one excluded.
                                        onToggleNegated(true);
                                        updateSelection([val]);
                                        return;
                                    }
                                    const next = selectedOptions.includes(val)
                                        ? selectedOptions.filter(v => v !== val)
                                        : [...selectedOptions, val];
                                    updateSelection(next);
                                } else {
                                    updateSelection([val]);
                                    setPopoverActive(false);
                                }
                            }}
                        >
                            {sections.map((section, si) => (
                                <Listbox.Section key={si} title={<Listbox.Header>{section.title || ''}</Listbox.Header>}>
                                    {(section.options || []).map(opt => {
                                        // Include+wildcard is unambiguous (only reachable via an explicit Select-all click) — show every row checked. Exclude+empty stays literal since a bare tab switch reaches it too.
                                        const isIncludeWildcard = !negated && selectedOptions.length === 1 && selectedOptions[0] === ALL_VALUES_SENTINEL;
                                        const isSelected = isIncludeWildcard ? true : selectedOptions.includes(opt.value);
                                        return (
                                            <Listbox.Option key={opt.value} value={opt.value} selected={isSelected} disabled={opt.disabled}>
                                                {allowMultiple ? (
                                                    // Explicit Checkbox: TextOption's own checkbox needs Combobox's context, which this popover doesn't provide
                                                    <div style={{ padding: '6px 12px' }}>
                                                        <Checkbox
                                                            label={<div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>{opt.media}{opt.label}</div>}
                                                            checked={isSelected}
                                                            disabled={opt.disabled}
                                                        />
                                                    </div>
                                                ) : (
                                                    <Listbox.TextOption selected={isSelected} disabled={opt.disabled}>
                                                        <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
                                                            {opt.media}
                                                            {opt.label}
                                                        </div>
                                                    </Listbox.TextOption>
                                                )}
                                            </Listbox.Option>
                                        );
                                    })}
                                </Listbox.Section>
                            ))}
                            {noResults && (
                                <Box padding="4">
                                    {emptyState}
                                </Box>
                            )}
                        </Listbox>
                    </Popover.Pane>
                </Popover>
            </>
        );
    }

    return (
            <>
                <style>{`#${id} { cursor: pointer; }`}</style>
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
            </>
    );
}

export default DropdownSearch
