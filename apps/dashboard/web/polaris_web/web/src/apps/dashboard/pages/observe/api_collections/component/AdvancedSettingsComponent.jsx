import React, { useEffect, useState, useMemo, useCallback } from 'react'

import { Button, HorizontalGrid, HorizontalStack, TextField, VerticalStack } from '@shopify/polaris';
import { DeleteMinor } from "@shopify/polaris-icons"
import Dropdown from '../../../../components/layouts/Dropdown';
import DropdownSearch from '../../../../components/shared/DropdownSearch';
import TestingStore from '../../../testing/testingStore';
import ObserveStore from '../../observeStore';

export const operatorTypeOptions = {
    "ADD_HEADER": { heading: "Add Header", status: "info" },
    "ADD_BODY_PARAM": { heading: "Add Body Param", status: "info" },
    "MODIFY_HEADER": { heading: "Modify Header", status: "warning" },
    "MODIFY_BODY_PARAM": { heading: "Modify Body Param", status: "warning" },
    "DELETE_HEADER": { heading: "Delete Header", status: "critical" },
    "DELETE_BODY_PARAM": { heading: "Delete Body Param", status: "critical" },
    "ADD_URL_PARAM": { heading: "Add URL Param", status: "info" },
    "MODIFY_URL_PARAM": { heading: "Modify URL Param", status: "warning" },
};

const menuItems = Object.keys(operatorTypeOptions).map(key => ({
    value: key,
    label: operatorTypeOptions[key].heading,
}));

function AdvancedSettingsComponent({ dispatchConditions, conditions, hideButton}) {
    const emptyCondition = { data: { key: '', value: '' }, operator: { 'type': 'ADD_HEADER' } }

    const [localUrlsList, setLocalUrlsList] = useState([])

    const handleTypeSelected = (type, index) => {
        dispatchConditions({ type: "update", index: index, key: 'operator', obj: { "type": type } })
    }

    const handleValueChange = (index, value) => {
        dispatchConditions({ type: 'update', index: index, key: "data", obj: { "value": value } })
    }

    const handleKeyChange = (index, value) => {
        dispatchConditions({ type: 'update', index: index, key: "data", obj: { "key": value } })
    }

    const handleUrlChange = (index, urls) => {
        dispatchConditions({ type: 'update', index: index, key: "data", obj: { "urlsList": urls } })
    }

    const handlePositionChange = (index, position) => {
        dispatchConditions({ type: 'update', index: index, key: "data", obj: { "position": position } })
    }

    const handleDelete = (index) => {
        dispatchConditions({ type: "delete", index: index })
    };

    const handleAddField = () => {
        dispatchConditions({ type: "add", obj: emptyCondition })
    };

    const [showAdvancedSettings, setShowAdvancedSettings] = useState(hideButton ? hideButton : false)
    const getLabel = (val) => {
        return menuItems.filter((x) => x.value === val)[0].label
    }

    const endpointsFromTesting = TestingStore.getState().testingEndpointsApisList;
    const urlsFromObserve = ObserveStore.getState().filteredItems;   
    useEffect(() => {
        
        // Prioritize urlsList prop over endpointsFromTesting
        if(urlsFromObserve && urlsFromObserve.length > 0) {
            setLocalUrlsList(urlsFromObserve.map(endpoint => endpoint.method + " " + endpoint.endpoint))
        } else if(endpointsFromTesting && endpointsFromTesting.length > 0) {
            setLocalUrlsList(endpointsFromTesting.map(endpoint => endpoint.method + " " + endpoint.endpoint))
        } else {
            setLocalUrlsList([])
        }
    }, [urlsFromObserve, endpointsFromTesting])

    // Memoized URL options to prevent unnecessary re-renders
    const urlOptions = useMemo(() => {
        return localUrlsList.map(url => ({
            value: url,
            label: url
        }));
    }, [localUrlsList]);

    // Check if the operation is URL parameter related
    const isUrlParamOperation = useCallback((type) => {
        return type.includes("URL_PARAM");
    }, []);

    

    return (
        <VerticalStack gap={"3"}>
                {hideButton ? null : <div style={{marginTop:"1.2rem"}}> <HorizontalStack align='start'><Button removeUnderline={false} fullWidth={false} plain monochrome onClick={() => setShowAdvancedSettings(!showAdvancedSettings)}>Show advance configurations</Button></HorizontalStack> </div>}
                {showAdvancedSettings ?
                    <VerticalStack gap={"2"}>
                        {conditions.map((condition, index) => {
                            const isUrlParam = isUrlParamOperation(condition?.operator?.type);
                            
                            return (
                                <HorizontalStack gap={"1"} key={index} wrap={false}>
                                    <div style={{ flex: 1 }}>
                                        <HorizontalStack gap={"2"} wrap={false}>
                                            <Button plain removeUnderline size="medium">AND</Button>
                                            <Dropdown
                                                id={`operator-type-${index}`}
                                                menuItems={menuItems}
                                                initial={() => getLabel(condition?.operator?.type)}
                                                selected={(type) => handleTypeSelected(type, index)}
                                            />
                                        </HorizontalStack>
                                    </div>
                                    <div style={{ flex: 3 }}>
                                        {isUrlParam ? (
                                            <HorizontalGrid columns={3} gap={"2"}>
                                                <DropdownSearch
                                                    id={`url-select-${index}`}
                                                    placeholder="Select URLs"
                                                    optionsList={urlOptions}
                                                    setSelected={(urls) => handleUrlChange(index, urls)}
                                                    preSelected={condition?.data['urlsList'] || []}
                                                    allowMultiple
                                                    itemName="URL"
                                                />
                                                <TextField
                                                    id={`position-${index}`}
                                                    placeholder="Enter position"
                                                    value={condition?.data['position'] || ""}
                                                    onChange={(newValue) => handlePositionChange(index, newValue)}
                                                />
                                                <TextField
                                                    id={`value-url-${index}`}
                                                    placeholder="Enter value"
                                                    value={condition?.data['value'] || ""}
                                                    onChange={(newValue) => handleValueChange(index, newValue)}
                                                />
                                            </HorizontalGrid>
                                        ) : (
                                            <HorizontalGrid columns={2} gap={"2"}>
                                                <TextField
                                                    id={`keyname-${index}`}
                                                    placeholder={"Enter key name"}
                                                    value={condition?.data['key']|| ""}
                                                    onChange={(newValue) => handleKeyChange(index,newValue)}
                                                />
                                                <TextField
                                                    id={`value-type-${index}`}
                                                    placeholder={"Enter value"}
                                                    value={condition?.data['value']|| ""}
                                                    onChange={(newValue) => handleValueChange(index,newValue)}
                                                    disabled={condition?.operator?.type.toLowerCase().includes("delete")}
                                                />
                                            </HorizontalGrid>
                                        )}
                                    </div>
                                    <Button icon={DeleteMinor} onClick={() => handleDelete(index)} />
                                </HorizontalStack>
                            )
                        })}
                        <HorizontalStack align="space-between">
                            <Button onClick={handleAddField}>Add condition</Button>
                        </HorizontalStack>
                    </VerticalStack> : null
                }
        </VerticalStack>
    )
}

export default AdvancedSettingsComponent