import React, { useState } from 'react'

import { Button, InlineGrid, InlineStack, TextField, BlockStack } from '@shopify/polaris';
import {DeleteIcon} from "@shopify/polaris-icons"
import Dropdown from '../../../../components/layouts/Dropdown';

function AdvancedSettingsComponent({dispatchConditions, conditions, hideButton}) {
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

    const [showAdvancedSettings, setShowAdvancedSettings] = useState(hideButton ? hideButton : false)
    const getLabel = (val) => {
        return operatorTypeOptions.filter((x) => x.value === val)[0].label
    }

    return (
        <BlockStack gap={"3"}>
            {hideButton ? null : <Button fullWidth={false} variant='plain' onClick={() => setShowAdvancedSettings(!showAdvancedSettings)}>Advanced configurations</Button>}
            {showAdvancedSettings ?
                <BlockStack gap={"200"}>
                    {conditions.map((condition, index) => {
                        return(
                            <InlineStack gap={"100"} key={index} wrap={false}>
                                <div style={{flex: 1}}>
                                    <InlineStack gap={"200"} wrap={false}>
                                        <Button size="medium">AND</Button>
                                        <Dropdown
                                            id={`operator-type-${index}`}
                                            menuItems={operatorTypeOptions} 
                                            initial={() => getLabel(condition?.operator?.type)} 
                                            selected={(type) => handleTypeSelected(type,index)} 
                                        />
                                    </InlineStack>
                                </div>
                                <div style={{flex: 3}}>
                                    <InlineGrid columns={2} gap={"200"}>
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
                                    </InlineGrid>
                                </div>
                                <Button size='large' icon={DeleteIcon} onClick={() => handleDelete(index)} />
                            </InlineStack>
                        )
                    })}
                    <InlineStack align="space-between">
                        <Button onClick={handleAddField}>Add condition</Button>
                    </InlineStack>
                </BlockStack> : null
            }
        </BlockStack>
    )
}

export default AdvancedSettingsComponent