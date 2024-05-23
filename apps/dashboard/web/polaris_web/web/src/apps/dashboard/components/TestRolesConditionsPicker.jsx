import { Button, LegacyCard, VerticalStack } from '@shopify/polaris'
import React from 'react'
import ConditionComponent from './ConditionComponent';
import Dropdown from './layouts/Dropdown';
import { DeleteMinor } from "@shopify/polaris-icons"
import func from "@/util/func"

function TestRolesConditionsPicker(props) {

    const id = props.id ? props.id : "condition"

    const {title, param, selectOptions, conditions, dispatch} = props

    const handleAddField = () => {
        dispatch({type:"add", condition: 
        { operator: selectOptions[0]?.operators?.label ? selectOptions[0]?.operators?.label : "OR", 
        type: selectOptions[0].value, value: ''}})
    };

    const handleDelete = (index) => {
        dispatch({type:"delete", index: index})
    };

    const handleOperatorSelected = (value, index) => {
        dispatch({type:"update", index:index, obj:{operator:value}})
    }

    const fieldsComponent = (
        <VerticalStack gap="4">
            {conditions.length > 0 && conditions.map((condition, index) => (
                <div style={{ display: "flex", gap: "4px" }} key={index}>
                    {index > 0 ? (
                        <div style={{ flex: "1" }}>
                            <Dropdown
                                id={`${id}-menu-${index}`}
                                menuItems={func.getConditions(selectOptions, condition.type)} 
                                initial={condition.operator}
                                selected={(value) => handleOperatorSelected(value, index)} />
                        </div>
                    ) : null}
                    <div style={{ flex: "7" }}>
                        <ConditionComponent
                            param={param}
                            selectOptions={selectOptions}
                            condition={condition}
                            index={index}
                            dispatch={dispatch}
                        />
                    </div>
                    <Button icon={DeleteMinor} onClick={() => handleDelete(index)} />
                </div>
            ))}
        </VerticalStack>
    )

    return (
        <LegacyCard.Section title={title}>
            {fieldsComponent}
            <br/>
            <Button onClick={handleAddField}><div data-testid="add_condition_button">Add condition</div></Button>
        </LegacyCard.Section>
    )

}

export default TestRolesConditionsPicker