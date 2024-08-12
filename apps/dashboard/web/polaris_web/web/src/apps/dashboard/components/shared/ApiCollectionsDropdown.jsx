import {
    Listbox,
    Combobox,
    Icon,
} from '@shopify/polaris';

import { SearchMinor } from '@shopify/polaris-icons';

import { useState, useCallback, useMemo } from 'react';
import PersistStore from '../../../main/PersistStore';


function ApiCollectionsDropdown({ selectedCollections, setSelectedCollections }) {

    const allCollections = PersistStore(state => state.allCollections)

    const deselectedOptions = useMemo(
        () => allCollections.map(collection => ({
            value: collection.name,
            label: collection.name
        })),
        [],
    );

    const [selectedOptions, setSelectedOptions] = useState(selectedCollections ? selectedCollections : []);
    const [inputValue, setInputValue] = useState('');
    const [options, setOptions] = useState(deselectedOptions);

    const updateText = useCallback(
        (value) => {
            setInputValue(value);

            if (value === '') {
                setOptions(deselectedOptions);
                return;
            }

            const filterRegex = new RegExp(value, 'i');
            const resultOptions = deselectedOptions.filter((option) =>
                option.label.match(filterRegex),
            );
            setOptions(resultOptions);
        },
        [deselectedOptions],
    );

    const updateSelection = useCallback(
        (selected) => {
            if (selectedOptions.includes(selected)) {
                setSelectedOptions(
                    selectedOptions.filter((option) => option !== selected),
                );
            } else {
                setSelectedOptions([...selectedOptions, selected]);
            }

            updateText('');
        },
        [selectedOptions, updateText],
    );

    const optionsMarkup =
        options.length > 0
            ? options.map((option) => {
                const { label, value } = option;

                return (
                    <Listbox.Option
                        key={`${value}`}
                        value={value}
                        selected={selectedOptions && selectedOptions.includes(value)}
                        accessibilityLabel={label}
                    >
                        {label}
                    </Listbox.Option>
                );
            })
            : null;

    const handleClose = () => {
        setSelectedCollections(selectedOptions)
    }

    return (
        <div style={{ }}>
            <Combobox
                allowMultiple
                preferredPosition='below'
                activator={
                    <Combobox.TextField
                        prefix={<Icon source={SearchMinor} />}
                        onChange={updateText}
                        label="Select collections"
                        labelHidden
                        value={inputValue}
                        placeholder="Select collections"
                        autoComplete="off"
                        
                    />
                }
                onClose={handleClose}
            >
                {optionsMarkup ? (
                    <Listbox onSelect={updateSelection}>{optionsMarkup}</Listbox>
                ) : null}
            </Combobox>

        </div>
    );
}

export default ApiCollectionsDropdown