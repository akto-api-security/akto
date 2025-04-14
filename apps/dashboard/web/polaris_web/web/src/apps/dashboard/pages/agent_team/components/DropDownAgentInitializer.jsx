import { Box, VerticalStack, Text, Button } from "@shopify/polaris";
import React from "react";
import DropdownSearch from "../../../components/shared/DropdownSearch";

function DataSelector(props) {

    const { optionsList, setData, data, startAgent, agentText, agentProperty } = props
    return (
        <Box as='div' paddingBlockStart={"5"}>
            <VerticalStack gap={"4"}>
                <Text as='span' variant='bodyMd'>
                    {agentText}
                </Text>
                <Box width='350px' paddingInlineStart={"2"}>
                    <DropdownSearch
                        placeholder={`Select ${agentProperty}`}
                        allowMultiple
                        optionsList={optionsList}
                        setSelected={setData}
                        preSelected={data}
                    />
                </Box>
                <StartButton startAgent={startAgent} data={data} />
            </VerticalStack>
        </Box>
    )
}

function StartButton(props) {
    const { startAgent, data } = props
    return <Button onClick={() => startAgent(data)} >
        Let's start!!
    </Button>
}

function DropDownAgentInitializer(props) {

    const { optionsList, data, setData, startAgent, agentText, agentProperty } = props

    return <DataSelector
        optionsList={optionsList}
        data={data}
        setData={setData}
        startAgent={startAgent}
        agentText={agentText}
        agentProperty={agentProperty}
    />
}

export default DropDownAgentInitializer