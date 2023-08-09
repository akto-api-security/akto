import { Button, LegacyCard, VerticalStack, Box, HorizontalStack } from '@shopify/polaris'
import React from 'react'
import ConditionComponent from './ConditionComponent';
import Dropdown from './layouts/Dropdown';
import { DeleteMinor } from "@shopify/polaris-icons"

function TestRolesConditionsPicker(props) {

    const {title, param, selectOptions, conditions, setConditions} = props

    const handleAddTextField = () => {
        setConditions((prev) => {
            prev =  [...prev, { operator: selectOptions[0]?.operators?.label ? selectOptions[0]?.operators?.label : "OR", 
            type: selectOptions[0].value, value: ''}]
            return prev;
        })
    };

    const handleDelete = (index) => {
        setConditions((prev) => {
            return prev.filter((item, i) => {return i!=index});
        })
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

    const getOption = (field) => {
        const option = selectOptions.filter((item) => {
            return item.value == field.type
        })[0]
        return option;
    }
    const getConditions = (field) => {
        const option = getOption(field)
        if (option.operators) {
            return option.operators
        }
        return orAndConditions;
    }

    const handleConditionSelected = (value, index) => {
        setConditions((prev) => {
            prev[index].operator = value;
            return [...prev];
        })
    }

    const textFieldsComponent = (
        <VerticalStack gap="4">
            {conditions.length > 0 && conditions.map((condition, index) => (
                <div style={{ display: "flex", gap: "4px" }} key={index}>
                    {index > 0 ? (
                        <div style={{ flex: "1" }}>
                            <Dropdown
                                menuItems={getConditions(condition)} initial={condition.operator}
                                selected={(value) => handleConditionSelected(value, index)} />
                        </div>
                    ) : null}
                    <div style={{ flex: "7" }}>
                        <ConditionComponent
                            param={param}
                            selectOptions={selectOptions}
                            condition={condition}
                            index={index}
                            setConditions={setConditions}
                        />
                    </div>
                    <Button icon={DeleteMinor} onClick={() => handleDelete(index)} />
                </div>
            ))}
        </VerticalStack>
    )

    return (
        <LegacyCard.Section title={title}>
            {textFieldsComponent}
            <br/>
            <Button onClick={handleAddTextField}>Add condition</Button>
        </LegacyCard.Section>
    )

}

export default TestRolesConditionsPicker