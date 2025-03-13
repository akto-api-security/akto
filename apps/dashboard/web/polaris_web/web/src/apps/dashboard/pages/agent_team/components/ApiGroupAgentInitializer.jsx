import { Box, VerticalStack, Text, Button } from "@shopify/polaris";
import React, { useState } from "react";
import DropdownSearch from "../../../components/shared/DropdownSearch";
import PersistStore from "../../../../main/PersistStore";
import agentApi from '../api'
import func from "../../../../../util/func";

function ApiGroupAgentInitializer(props) {
    
        const { agentType } = props
    
        const [selectedCollections, setSelectedCollections] = useState([]);
        const allCollections = PersistStore(state => state.allCollections)
        const optionsList = allCollections.filter(x => !x.deactivated).map((x) => {
            return {
                label: x.displayName,
                value: x.id,
            }
        })
    
        async function startAgent(collectionIds) {
            if (collectionIds.length === 0) {
                func.setToast(true, true, "Please select collections to run the agent")
                return
            }
    
            await agentApi.createAgentRun({
                agent: agentType,
                data: {
                    apiCollectionIds: collectionIds
                }
            })
            func.setToast(true, false, "Agent run scheduled")
        }
    
        function StartButton() {
            return <Button onClick={() => startAgent(selectedCollections)} >
                Let's start!!
            </Button>
        }
    
        function CollectionSelector() {
            return (
                <Box as='div' paddingBlockStart={"5"}>
                    <VerticalStack gap={"4"}>
                        <Text as='span' variant='bodyMd'>
                            Hey! Let's select API collections to run the API grouping agent on.
                        </Text>
                        <Box width='350px' paddingInlineStart={"2"}>
                            <DropdownSearch
                                placeholder={"Select collections"}
                                allowMultiple
                                optionsList={optionsList}
                                setSelected={setSelectedCollections}
                                preSelected={selectedCollections}
                                value={`${selectedCollections.length} collections selected`}
                            />
                        </Box>
                        <StartButton />
                    </VerticalStack>
                </Box>
            )
        }
    
        return <CollectionSelector />
    }
    

export default ApiGroupAgentInitializer