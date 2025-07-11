import React, { useState } from 'react'

import { Button, HorizontalGrid, HorizontalStack, TextField, VerticalStack } from '@shopify/polaris';
import { DeleteMinor } from "@shopify/polaris-icons"
import Dropdown from '../../../../components/layouts/Dropdown';

export const operatorTypeOptions = {
    "ADD_HEADER": { heading: "Add Header", status: "info" },
    "ADD_BODY_PARAM": { heading: "Add Body Param", status: "info" },
    "MODIFY_HEADER": { heading: "Modify Header", status: "warning" },
    "MODIFY_BODY_PARAM": { heading: "Modify Body Param", status: "warning" },
    "DELETE_HEADER": { heading: "Delete Header", status: "critical" },
    "DELETE_BODY_PARAM": { heading: "Delete Body Param", status: "critical" }
};

const menuItems = Object.keys(operatorTypeOptions).map(key => ({
    value: key,
    label: operatorTypeOptions[key].heading,
}));

function AdvancedSettingsComponent({ dispatchConditions, conditions, hideButton }) {
    const emptyCondition = { data: { key: '', value: '' }, operator: { 'type': 'ADD_HEADER' } }

    const handleTypeSelected = (type, index) => {
        dispatchConditions({ type: "update", index: index, key: 'operator', obj: { "type": type } })
    }

    const handleValueChange = (index, value) => {
        dispatchConditions({ type: 'update', index: index, key: "data", obj: { "value": value } })
    }

    const handleKeyChange = (index, value) => {
        dispatchConditions({ type: 'update', index: index, key: "data", obj: { "key": value } })
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

    return (
        <VerticalStack gap={"3"}>
                {hideButton ? null : <div style={{marginTop:"1.2rem"}}> <HorizontalStack align='start'><Button removeUnderline={false} fullWidth={false} plain monochrome onClick={() => setShowAdvancedSettings(!showAdvancedSettings)}>Show advance configurations</Button></HorizontalStack> </div>}
                {showAdvancedSettings ?
                    <VerticalStack gap={"2"}>
                        {conditions.map((condition, index) => {
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