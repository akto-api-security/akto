import React, { useEffect, useState } from 'react'
import { HorizontalStack, Text, TextField, VerticalStack } from "@shopify/polaris"
import DropdownSearch from "../../../../components/shared/DropdownSearch"
import AgentOutput from './AgentOutput';
import { useAgentsStore } from '../../agents.store';

interface OutputSelectorProps {
  onHandleSelect: (selectedChoice: any, outputOptions: any) => void;
  processOutput: Record<string, any>;
}

export const getMessageFromObj = (obj: any, key:string) => {
    if(typeof obj === "string"){
        return obj;
    }else{
        if(obj[key]){
            return obj[key];
        }
    }
}

function OutputSelector({onHandleSelect, processOutput} : OutputSelectorProps) {

    const { currentAgent } = useAgentsStore();

    const noOptionsReturned = processOutput?.outputOptions.length === 0

    const getStringMessage = (type: string, options: any[]) => {

        if(currentAgent?.id=="GROUP_APIS"){
            return ""
        }

        let maxOutputOptions = type === "multiple" ? 3 : 1;
        let messageString = "";
        options.slice(0, maxOutputOptions).forEach((option: any, index: any) => {
            messageString += getMessageFromObj(option, "textValue")
            if ((index + 1) < maxOutputOptions) {
                messageString += ",";
            }
            messageString += " ";
        })

        if (maxOutputOptions + 1 === options.length && type === "multiple") {
            messageString += "and " + options[options.length - 1]
        } else if (maxOutputOptions < options.length && type === "multiple") {
            messageString += "and " + (options.length - maxOutputOptions) + " more...";
        }
        return messageString;
    }
    const auxMessage = noOptionsReturned ? "\n No options were returned \n" : "\n We are moving forward with the following option(s):\n"; 

    const messageString = processOutput?.outputMessage;

    const allowMultiple = processOutput?.selectionType === "multiple"
    const initialValue = noOptionsReturned ? "" : (!allowMultiple ?
        getMessageFromObj(processOutput?.outputOptions[0], "textValue") :
        processOutput?.outputOptions.map((option: any) => (option?.value !== undefined ? option?.value : (option?.textValue !== undefined ? option?.textValue : option))))

    const [filteredChoices, setFilteredChoices] = useState(initialValue);
    const handleSelected = (selectedChoices: any) => { 
        setFilteredChoices(selectedChoices);
    }

    useEffect(() => {
        onHandleSelect(filteredChoices, processOutput)
    },[filteredChoices])

    return (
        <VerticalStack gap={"3"}>
            <VerticalStack gap={"1"}>
                <Text variant="bodyMd" as="span">{messageString}</Text>
                <Text variant="headingMd" color="subdued" as="span">{getStringMessage(processOutput?.selectionType, processOutput?.outputOptions)}</Text>
            </VerticalStack>
            <AgentOutput/>
            <HorizontalStack gap={"3"}>
            <Text variant="bodyMd" as="span">{auxMessage}</Text>
            {
                noOptionsReturned ? <TextField labelHidden={true} label="" autoComplete="off" value={filteredChoices as string} onChange={(val: string) => setFilteredChoices(val)} /> :
                    <HorizontalStack gap={"2"}>
                         <DropdownSearch
                            id={"dropdown-search"}
                            allowMultiple={allowMultiple}
                            optionsList={processOutput?.outputOptions.map((option: any) => {
                                // TODO: optionally take this function for transformation.
                                return {
                                    label: option?.textValue !== undefined ? option?.textValue : option,
                                    value: option?.value !== undefined ? option?.value : (option?.textValue !== undefined ? option?.textValue : option),
                                }
                            })}
                            placeholder={"Edit choice(s)"}
                            setSelected={(selectedChoices: any) => handleSelected(selectedChoices)}
                            preSelected={allowMultiple? filteredChoices: [filteredChoices]}
                            value={allowMultiple ? undefined : filteredChoices}
                            sliceMaxVal={80}
                        />
                    </HorizontalStack>
            }
            </HorizontalStack>
        </VerticalStack>
    )
}

export default OutputSelector
