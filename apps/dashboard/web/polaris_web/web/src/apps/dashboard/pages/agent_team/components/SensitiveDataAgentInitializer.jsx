import { Box, VerticalStack, Text } from "@shopify/polaris";
import React, { useState } from "react";
import DropdownSearch from "../../../components/shared/DropdownSearch";
import PersistStore from "../../../../main/PersistStore";

function SensitiveDataAgentInitializer() {

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
            value={selectedCollection!=-1 ? allCollections.filter(x => x.id==selectedCollection)[0].displayName : "Select collections"}
        />)
    }

    function CollectionSelector() {
        return (
            <Box as='div' paddingBlockStart={"5"}>
                <VerticalStack gap={"4"}>
                    <Text as='span' variant='bodyMd'>
                        Hey! Let's select an API collection to run the sensitive data type scanner on.
                    </Text>
                    <Box width='350px' paddingInlineStart={"2"}>
                        <CollectionDropDown/>
                    </Box>
                </VerticalStack>
            </Box>
        )
    }

    return <CollectionSelector />
}

export default SensitiveDataAgentInitializer;