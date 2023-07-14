import { Button, ButtonGroup, LegacyCard, TextField, VerticalStack } from '@shopify/polaris'
import React, { useState } from 'react'
import Dropdown from './layouts/Dropdown';
import {DeleteMinor} from "@shopify/polaris-icons"

function ConditionsPicker(props) {

    const {title, param, items, initialItems} = props

    const [condition, setCondition] = useState("OR")
    const [textFields, setTextFields] = useState(initialItems);

    const handleChange = (value, index) => {
        const updatedFields = [...textFields];
        updatedFields[index].value = value;
        setTextFields(updatedFields);
    };

    const handleDelete = (index) => {
        const updatedFields = [...textFields];
        updatedFields.splice(index, 1);
        setTextFields(updatedFields);
    };

    const handleAddTextField = () => {
        const updatedFields = [...textFields, { value: '', param: param, regex: items[0].value }];
        setTextFields(updatedFields);
    };

    const orAndConditions = [
        {
            label: 'OR',
            value: 'OR',
        },
        {
            label: 'AND',
            value: 'AND'
        }
    ]

    const handleConditionSelected = (val) =>{
        setCondition(val)
        console.log(textFields)
    }
    const handleRegexSelected = (value,index) =>{
        const updatedFields = [...textFields];
        updatedFields[index].regex = value;
        setTextFields(updatedFields);
    }

    const prefixLeft= (index) =>(
        <ButtonGroup>
            {index > 0 ? (<Dropdown menuItems={orAndConditions} initial={condition} selected={handleConditionSelected}/>) : null}
            <TextField value={param} />
            <Dropdown menuItems={items} initial={items[0].value} selected={(val) => handleRegexSelected(val,index)}/>
        </ButtonGroup>
    )

    const textFieldsComponent = (
        <VerticalStack gap="4">
            {textFields.length > 0 && textFields.map((field,index)=> (
                <TextField connectedLeft={prefixLeft(index)} connectedRight={<Button icon={DeleteMinor} onClick={() => handleDelete(index)} />} 
                            value={field.value}
                            onChange={(newValue) => handleChange(newValue, index)}
                            key={index}
                />
            ))}
        </VerticalStack>
    )

    return (
        <LegacyCard.Section title={title}>
            {textFieldsComponent}
            <br/>
            <Button onClick={handleAddTextField}>Add Condition</Button>
        </LegacyCard.Section>
    )
}

export default ConditionsPicker