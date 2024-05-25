import { Button, LegacyCard, VerticalStack } from '@shopify/polaris'
import React from 'react'
import Dropdown from './layouts/Dropdown';
import {DeleteMinor} from "@shopify/polaris-icons"
import func from "@/util/func"
import ConditionComponent from './ConditionComponent';
import TitleWithInfo from './shared/TitleWithInfo';

function ConditionsPicker(props) {

    const id = props.id ? props.id : "condition"

    const {title, param, selectOptions, conditions, operator, dispatch, tooltipContent} = props

    const handleDelete = (index) => {
        dispatch({type:"delete", key: "condition", index: index})
    };

    const handleAddField = () => {
        dispatch({type:"add", key: "condition", condition: 
        { type: selectOptions[0].value, value: ''}})
    };

    const handleOperatorSelected = (value) =>{
        dispatch({type:"update", obj:{operator:value}})
    }

    const handleConditionDispatch = (val) => {
        dispatch({type:"update", key: "condition", index: val.index, obj:val.obj})
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
                                initial={operator}
                                selected={(value) => handleOperatorSelected(value, index)} />
                        </div>
                    ) : null}
                    <div style={{ flex: "7" }}>
                        <ConditionComponent
                            id={id}
                            param={param}
                            selectOptions={selectOptions}
                            condition={condition}
                            index={index}
                            dispatch={handleConditionDispatch}
                        />
                    </div>
                    <Button id={`${id}-delete-button-${index}`} icon={DeleteMinor} onClick={() => handleDelete(index)} />
                </div>
            ))}
        </VerticalStack>
    )

    return (
        <LegacyCard.Section title={
            <TitleWithInfo titleText={title} tooltipContent={tooltipContent} textProps={{variant: 'headingSm', color: 'subdued'}} />
        } >
            {fieldsComponent}
            <br/>
            <Button id={`${id}-add-condition`} onClick={handleAddField}>Add condition</Button>
        </LegacyCard.Section>
    )
}

export default ConditionsPicker