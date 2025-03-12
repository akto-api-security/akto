import React from 'react'
import { HorizontalStack, Text, VerticalStack } from "@shopify/polaris"
import DropdownSearch from "../../../../components/shared/DropdownSearch"

interface OutputSelectorProps {
  onHandleSelect: (selectedChoice: any) => void;
  processOutput: Record<string, any>;
}

function OutputSelector({onHandleSelect, processOutput} : OutputSelectorProps) {

    const getMessageFromObj = (obj: any, key:string) => {
        if(typeof obj === "string"){
            return obj;
        }else{
            if(obj[key]){
                return obj[key];
            }
        }
    }

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
    const messageString = processOutput?.outputMessage + "\n We are moving forward with the following option(s):\n" + getStringMessage(processOutput?.selectionType, processOutput?.outputOptions); 

    const initialValue = processOutput?.selectionType === 'single' ? getMessageFromObj(processOutput?.outputOptions[0], "value") : processOutput?.outputOptions.map((option: any) => option?.value);
    const [filteredChoices, setFilteredChoices] = React.useState<any[]>(initialValue);
    const handleSelected = (selectedChoices: any) => { 
        setFilteredChoices(selectedChoices);
        onHandleSelect(selectedChoices);
    }

    return (
        <VerticalStack gap={"3"}>
            <Text variant="bodyMd" as="span">{messageString}</Text>
            <HorizontalStack gap={"2"}>
                <Text variant="bodyMd" as="span">For editing or more info: </Text>
                <DropdownSearch
                    allowMultiple={processOutput?.selectionType === "multiple"}
                    options={processOutput?.outputOptions.map((option: any) => {
                        return {
                            label: option?.textValue,
                            value: option?.value,
                        }
                    })}
                    placeHolder={"Edit choice(s)"}
                    setSelected={handleSelected}
                    preSelected={filteredChoices}
                    value={`${filteredChoices.length} choices selected`}
                />
            </HorizontalStack>

        </VerticalStack>
    )
}

export default OutputSelector
