import CollectionComponent from "../../../components/CollectionComponent"
import OperatorDropdown from "../../../components/layouts/OperatorDropdown";
import { VerticalStack, Card, Button, HorizontalStack, Collapsible, Text, Box, Icon, Modal, TextField } from "@shopify/polaris";
import PageWithMultipleCards from "../../../components/layouts/PageWithMultipleCards";
import TitleWithInfo from "@/apps/dashboard/components/shared/TitleWithInfo"
import React, { useState, useReducer, useCallback, useMemo } from 'react'
import { produce } from "immer"
import ApiEndpoints from "./ApiEndpoints";
import api from "../api"
import { ChevronDownMinor, ChevronUpMinor } from "@shopify/polaris-icons"
import func from "@/util/func";
import SaveAsCollectionModal from "./api_query_component/SaveAsCollectionModal";

function APIQuery() {
    const emptyCondition = { data: {}, operator: "AND", type: "CUSTOM" };
    const [conditions, dispatchConditions] = useReducer(produce((draft, action) => conditionsReducer(draft, action)), []);
    const [endpointListFromConditions, setEndpointListFromConditions] = useState({})
    const [sensitiveParams, setSensitiveParams] = useState({})
    const [open, setOpen] = useState(true);
    const handleToggle = useCallback(() => setOpen((open) => !open), []);
    const [apiCount, setApiCount] = useState(0)
    const [active, setActive] = useState(false);
    const [newCollectionName, setNewCollectionName] = useState('');

    const handleNewCollectionNameChange =
        useCallback(
            (newValue) => setNewCollectionName(newValue),
            []);

    function conditionsReducer(draft, action) {
        switch (action.type) {
            case "add": draft.push(action.obj); break;
            case "overwrite": draft[action.index][action.key] = {};
            case "update": draft[action.index][action.key] = { ...draft[action.index][action.key], ...action.obj }; break;
            case "updateKey": draft[action.index] = { ...draft[action.index], [action.key]: action.obj }; break;
            case "delete": return draft.filter((item, index) => index !== action.index);
            case "clear": return [];
            default: break;
        }
    }

    const handleAddField = useCallback(() => {
        dispatchConditions({ type: "add", obj: emptyCondition });
    }, []);

    const openModal = useCallback(() => {
        setActive(true);
    }, []);

    const prepareData = useCallback(() => {
        let dt = [];
        conditions.forEach(condition => {
            if (condition.type === "CUSTOM") {
                let apiList = [];
                let collectionId = Object.keys(condition.data)[0];
                if (collectionId !== undefined && condition.data[collectionId].length > 0) {
                    condition.data[collectionId].forEach(x =>
                        apiList.push({
                            apiCollectionId: parseInt(collectionId),
                            url: x.url,
                            method: x.method
                        }));
                }
                dt.push({ type: condition.type, operator: condition.operator, data: { apiList: apiList } });
            } else {
                dt.push(condition);
            }
        });
        return dt;
    }, [conditions]);

    const createNewCollection = async (newCollectionName) => {
        if (newCollectionName.length === 0) {
            func.setToast(true, true, "Collection name cannot be empty");
            return;
        }
        let dt = prepareData();
        if (dt && dt.length > 0) {
            await api.createCustomCollection(newCollectionName, dt);
            setActive(false);
            func.setToast(true, false, <div data-testid="collection_creation_message">{"API collection created successfully"}</div>);
        } else {
            func.setToast(true, true, <div data-testid="collection_creation_message">{"No endpoints selected"}</div>);
        }
    }

    const handleClearFunction = useCallback(() => {
        dispatchConditions({ type: "clear" });
        setEndpointListFromConditions({});
        setSensitiveParams({});
    }, []);


    const exploreEndpoints = useCallback(async () => {
        let dt = prepareData();
        if (dt.length > 0) {
            let endpointListFromConditions = await api.getEndpointsListFromConditions(dt);
            let sensitiveParams = await api.loadSensitiveParameters(-1);
            if (endpointListFromConditions || sensitiveParams) {
                setEndpointListFromConditions(endpointListFromConditions);
                setSensitiveParams(sensitiveParams);
                setApiCount(endpointListFromConditions.apiCount);
            }
        } else {
            setEndpointListFromConditions({});
            setSensitiveParams({});
        }
    }, [prepareData]);

    const modalComponent =
        <SaveAsCollectionModal
            key="save-as-collection-modal"
            createNewCollection={createNewCollection}
            active={active}
            setActive={setActive}
        ></SaveAsCollectionModal>

    const collapsibleComponent =
        <VerticalStack gap={"0"} key="conditions-filters">
            <Box background={"bg-subdued"} width="100%" padding={"2"} onClick={handleToggle} key="collapsible-component-header">
                <HorizontalStack align="space-between">
                    <Text variant="headingSm">
                        {endpointListFromConditions.data ? apiCount > 200 ? `Listing 200 sample endpoints out of total ` + apiCount + ` endpoints` : `Listing total ` + apiCount + ` endpoints` : "Select filters to see endpoints"}
                    </Text>
                    <Box>
                        <Icon source={open ? ChevronDownMinor : ChevronUpMinor} />
                    </Box>
                </HorizontalStack>
            </Box>
            <Collapsible
                key="basic-collapsible"
                open={open}
                id="basic-collapsible"
                transition={{ duration: '200ms', timingFunction: 'ease-in-out' }}
            >
                <VerticalStack gap={"0"} key="conditions-component">
                    <Card>
                        <VerticalStack gap="4">
                            {
                                conditions.length > 0 && conditions.map((condition, index) => (
                                    <CollectionComponent
                                        condition={condition}
                                        index={index}
                                        key={`collections-condition-${index}`}
                                        dispatch={dispatchConditions}
                                        operatorComponent={<OperatorDropdown
                                            items={[{
                                                label: 'OR',
                                                value: 'OR',
                                            },
                                            {
                                                label: 'AND',
                                                value: 'AND'
                                            }]}
                                            label={condition.operator}
                                            selected={(value) => {
                                                dispatchConditions({ type: "updateKey", index: index, key: "operator", obj: value })
                                            }} />}
                                    />
                                ))
                            }
                            <HorizontalStack gap={4} align="start">
                                <Button onClick={handleAddField}>Add condition</Button>
                                {
                                    conditions.length > 0 ? <Button plain destructive onClick={handleClearFunction}>Clear all</Button> : null
                                }
                            </HorizontalStack>
                            <HorizontalStack gap={4} align="end">
                                <Button onClick={exploreEndpoints}>Explore endpoints</Button>
                            </HorizontalStack>
                        </VerticalStack>
                    </Card>
                </VerticalStack>
            </Collapsible>
        </VerticalStack>

    const components = useMemo(() => [
        modalComponent,
        collapsibleComponent,
        <ApiEndpoints
            key="endpoint-table"
            endpointListFromConditions={endpointListFromConditions}
            sensitiveParamsForQuery={sensitiveParams}
            isQueryPage={true}
        />
    ], [modalComponent, collapsibleComponent, endpointListFromConditions, sensitiveParams]);

    return (
        <PageWithMultipleCards
            title={
                <TitleWithInfo
                    tooltipContent={"Via explore mode, you can explore api's and create a collection"}
                    titleText={"Explore Mode"}
                />
            }
            primaryAction={<Button id={"explore-mode-query-page"} primary secondaryActions onClick={openModal}>Save as collection</Button>}
            isFirstPage={false}
            components={components}
        />
    )
}

export default APIQuery