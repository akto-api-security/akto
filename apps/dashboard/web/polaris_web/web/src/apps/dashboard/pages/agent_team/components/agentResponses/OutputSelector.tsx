import React, { useEffect, useState } from 'react'
import { HorizontalStack, Text, TextField, VerticalStack } from "@shopify/polaris"
import DropdownSearch from "../../../../components/shared/DropdownSearch"

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

    const getStringMessage = (type: string, options: any[]) =>{
        let maxOutputOptions = type === "multiple" ? 3 : 1;
        let messageString = "";
        options.slice(0, maxOutputOptions).forEach((option: any) => {
            messageString += getMessageFromObj(option, "textValue") + " ";
        })
        if(maxOutputOptions < options.length && type === "multiple"){
            messageString += "and " + (options.length - maxOutputOptions) + " more...";
        }
        return messageString;
    }
    const messageString = processOutput?.outputMessage + "\n We are moving forward with the following option(s):\n"; 

    const allowMultiple = processOutput?.selectionType === "multiple"
    const initialValue = !allowMultiple ?
        getMessageFromObj(processOutput?.outputOptions[0], "textValue") :
        processOutput?.outputOptions.map((option: any) => (option.value !== undefined ? option.value : JSON.stringify(option)));
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
                <Text variant="headingSm" color="subdued"  as="span">{getStringMessage(processOutput?.selectionType, processOutput?.outputOptions)}</Text>
            </VerticalStack>
            <HorizontalStack gap={"2"}>
                <Text variant="bodyMd" as="span">For editing or more info: </Text>
                {processOutput?.outputOptions.length > 1 ? <DropdownSearch
                    allowMultiple={allowMultiple}
                    optionsList={processOutput?.outputOptions.map((option: any) => {
                        // TODO: optionally take this function for transformation.
                        return {
                            label: option.textValue!==undefined ? option?.textValue : option,
                            value: option?.value!==undefined ? option?.value : JSON.stringify(option),
                        }
                    })}
                    placeHolder={"Edit choice(s)"}
                    setSelected={(selectedChoices: any) => handleSelected(selectedChoices)}
                    preSelected={filteredChoices}
                    value={allowMultiple ?`${filteredChoices.length} choice${filteredChoices.length===1 ? "" : "s"} selected` : filteredChoices}
                /> : <TextField labelHidden={true} label="" autoComplete="off" value={filteredChoices as string} onChange={(val:string) => setFilteredChoices(val)}/>}
            </HorizontalStack>

        </VerticalStack>
    )
}

export default OutputSelector
