import React, { useState } from 'react'

import { Button, HorizontalGrid, HorizontalStack, TextField, VerticalStack } from '@shopify/polaris';
import {DeleteMinor} from "@shopify/polaris-icons"
import Dropdown from '../../../../components/layouts/Dropdown';

function AdvancedSettingsComponent({dispatchConditions, conditions, showSave, handleSave}) {
    const emptyCondition = {data: {key: '', value: ''}, operator: {'type': 'ADD_HEADER'}}

    const operatorTypeOptions = [
        { value: "ADD_HEADER", label: "Add Header" },
        { value: "ADD_BODY_PARAM", label: "Add Body Param" },
        { value: "MODIFY_HEADER", label: "Modify Header" },
        { value: "MODIFY_BODY_PARAM", label: "Modify Body Param" },
        { value: "DELETE_HEADER", label: "Delete Header" },
        { value: "DELETE_BODY_PARAM", label: "Delete Body Param" }
    ];

    const handleTypeSelected = (type, index) => {
        dispatchConditions({ type: "update", index: index, key: 'operator', obj:{"type": type} })
    }

    const handleValueChange = (index, value) => {
        dispatchConditions({type: 'update', index: index, key: "data", obj: {"value":value } })
    }

    const handleKeyChange = (index, value) => {
        dispatchConditions({type: 'update', index: index, key: "data", obj: {"key":value } })
    }

    const handleDelete = (index) => {
        dispatchConditions({type:"delete", index: index})
    };

    const handleAddField = () => {
        dispatchConditions({type:"add", obj: emptyCondition})
    };

    const [showAdvancedSettings, setShowAdvancedSettings] = useState(false)
    const getLabel = (val) => {
        return operatorTypeOptions.filter((x) => x.value === val)[0].label
    }

    return (
        <VerticalStack gap={"3"}>
            <Button fullWidth={false} plain removeUnderline onClick={() => setShowAdvancedSettings(!showAdvancedSettings)}>Advanced configurations</Button>
            {showAdvancedSettings ?
                <VerticalStack gap={"2"}>
                    {conditions.map((condition, index) => {
                        return(
                            <HorizontalStack gap={"1"} key={index} wrap={false}>
                                <div style={{flex: 1}}>
                                    <HorizontalStack gap={"2"} wrap={false}>
                                        <Button plain removeUnderline size="medium">AND</Button>
                                        <Dropdown
                                            id={`operator-type-${index}`}
                                            menuItems={operatorTypeOptions} 
                                            initial={() => getLabel(condition?.operator?.type)} 
                                            selected={(type) => handleTypeSelected(type,index)} 
                                        />
                                    </HorizontalStack>
                                </div>
                                <div style={{flex: 3}}>
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
                        {showSave ? <Button primary onClick={handleSave}>Save</Button> : null}
                    </HorizontalStack>
                </VerticalStack> : null
            }
        </VerticalStack>
    )
}

export default AdvancedSettingsComponent