import { Box, VerticalStack, Text, Button } from "@shopify/polaris";
import React, { useState } from "react";
import DropdownSearch from "../../../components/shared/DropdownSearch";
import PersistStore from "../../../../main/PersistStore";
import agentApi from '../api'
import func from "../../../../../util/func";

function SensitiveDataAgentInitializer(props) {

    const { agentType } = props

    const [selectedCollection, setSelectedCollection] = useState(-1);
    const allCollections = PersistStore(state => state.allCollections)

    function CollectionDropDown() {
        return (<DropdownSearch
            placeholder="Select collections"
            optionsList={
                allCollections.filter(x => !x.deactivated).map((x) => {
                    return {
                        label: x.displayName,
                        value: x.id,
                    }
                })
            }
            setSelected={(x) => {
                setSelectedCollection(x);
            }}
            value={selectedCollection != -1 ? allCollections.filter(x => x.id == selectedCollection)[0].displayName : "Select collections"}
        />)
    }

    async function startSensitiveDataAgent(collectionId) {
        if (collectionId == -1) {
            func.setToast(true, true, "Please select collections to run the agent")
            return
        }

        await agentApi.createAgentRun({
            agent: agentType,
            data: {
                apiCollectionIds: [collectionId]
            }
        })
        func.setToast(true, false, "Agent run scheduled")
    }

    function StartButton() {
        return <Button onClick={() => startSensitiveDataAgent(selectedCollection)} >
            Let's start!!
        </Button>
    }

    function CollectionSelector() {
        return (
            <Box as='div' paddingBlockStart={"5"}>
                <VerticalStack gap={"4"}>
                    <Text as='span' variant='bodyMd'>
                        Hey! Let's select an API collection to run the sensitive data type scanner on.
                    </Text>
                    <Box width='350px' paddingInlineStart={"2"}>
                        <CollectionDropDown />
                    </Box>
                    <StartButton />
                </VerticalStack>
            </Box>
        )
    }

    return <CollectionSelector />
}

export default SensitiveDataAgentInitializer;