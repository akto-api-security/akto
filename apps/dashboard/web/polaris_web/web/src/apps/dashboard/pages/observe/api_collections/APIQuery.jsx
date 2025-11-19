import CollectionComponent from "../../../components/CollectionComponent"
import OperatorDropdown from "../../../components/layouts/OperatorDropdown";
import { VerticalStack, Card, Button, HorizontalStack, Collapsible, Text, Box, Icon, Popover, ActionList, Checkbox } from "@shopify/polaris";
import PageWithMultipleCards from "../../../components/layouts/PageWithMultipleCards";
import TitleWithInfo from "@/apps/dashboard/components/shared/TitleWithInfo"
import React, { useState, useReducer, useCallback, useMemo, useEffect } from 'react'
import { produce } from "immer"
import ApiEndpoints from "./ApiEndpoints";
import api from "../api"
import { ChevronDownMinor, ChevronUpMinor, FileMinor } from "@shopify/polaris-icons"
import func from "@/util/func";
import SaveAsCollectionModal from "./api_query_component/SaveAsCollectionModal";
import { useSearchParams, useNavigate } from "react-router-dom";
import PersistStore from "../../../../main/PersistStore";
import collectionsApi from "./api"
import { getDashboardCategory, mapLabel } from "../../../../main/labelHelper";

function APIQuery() {
    const [conditions, dispatchConditions] = useReducer(produce((draft, action) => func.conditionsReducer(draft, action)), []);
    const [endpointListFromConditions, setEndpointListFromConditions] = useState({})
    const [sensitiveParams, setSensitiveParams] = useState({})
    const [open, setOpen] = useState(true);
    const handleToggle = useCallback(() => setOpen((open) => !open), []);
    const [apiCount, setApiCount] = useState(0)
    const [active, setActive] = useState(false);
    const collectionsMap = PersistStore.getState().collectionsMap
    const [isUpdate, setIsUpdate] = useState(false)
    const [moreActions, setMoreActions] = useState(false);
    const [skipTagsMismatch, setSkipTagsMismatch] = useState(true);

    const [searchParams, setSearchParams] = useSearchParams();
    const navigate = useNavigate();
    const collectionId = (searchParams && searchParams.get("collectionId") !== null) ? searchParams.get("collectionId") : -1

    const getEmptyCondition = (type) => {
        return { data: {}, operator: "AND", type: type };
    }

    const emptyCondition = getEmptyCondition('CUSTOM')

    const handleAddField = useCallback(() => {
        dispatchConditions({ type: "add", obj: emptyCondition });
    }, []);

    const openModal = useCallback(() => {
        setActive(true);
    }, []);

    const getApiCollection = () => {
        collectionsApi.getCollection(collectionId).then((res) => {
            (res[0].conditions || []).forEach((x, index) => {
                let tempKey = x.type
                if(x.actualType && x?.actualType !== undefined){
                    tempKey = x.actualType
                }
                const tempEmptyCondition = getEmptyCondition(tempKey)
                dispatchConditions({ type: "add", obj: tempEmptyCondition })
                dispatchConditions({ type: "updateKey", index: index, key: "operator", obj: x.operator })
                if(x.type === 'CUSTOM'){
                    let data = {
                        [x.apisList[0]['apiCollectionId']]: x.apisList.map((obj) => {
                            let temp = obj 
                            delete temp['apiCollectionId']
                            return temp
                        })
                    }
                    dispatchConditions({ obj: data ,key: "data", type: "overwrite", index: index })
                }else{
                    let temp = JSON.parse(JSON.stringify(x))
                    delete temp['type']
                    delete temp['operator']
                    if(x?.actualType !== undefined && x?.actualType === 'HOST_REGEX'){
                        temp['host_regex'] = temp.regex
                        delete temp['regex']
                    }
                    delete temp['actualType']
                    
                    dispatchConditions({index: index, type: "overwrite", obj: temp, key: "data"})
                }
                
            })
        })
    }

    useEffect(() => {
        if(collectionId.length > 0 && collectionsMap && Object.keys(collectionsMap).length > 0) {
            if(collectionsMap.hasOwnProperty(collectionId)){
                setIsUpdate(true)
                getApiCollection();
                exploreEndpoints() ;
            }else{
                func.setToast(true, true, "No such collection exists")
                const newSearchParams = new URLSearchParams(searchParams);
                newSearchParams.delete('collectionId');
                setSearchParams(newSearchParams);
            }
        }
    },[collectionId])

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
            let endpointListFromConditions = await api.getEndpointsListFromConditions(dt, skipTagsMismatch);
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
    }, [prepareData, skipTagsMismatch]);

    // eslint-disable-next-line react-hooks/exhaustive-deps
    const modalComponent =
        <SaveAsCollectionModal
            key="save-as-collection-modal"
            createNewCollection={createNewCollection}
            active={active}
            setActive={setActive}
        ></SaveAsCollectionModal>

    // eslint-disable-next-line react-hooks/exhaustive-deps
    const collapsibleComponent =
        <VerticalStack gap={"0"} key="conditions-filters">
            <Box background={"bg-subdued"} width="100%" padding={"2"} onClick={handleToggle} key="collapsible-component-header">
                <HorizontalStack align="space-between">
                    <Text variant="headingSm">
                        {endpointListFromConditions.data ? apiCount > 200 ? `Listing 200 sample ${mapLabel("endpoints", getDashboardCategory())} out of total ` + apiCount + ` ${mapLabel("endpoints", getDashboardCategory())}` : `Listing total ` + apiCount + ` ${mapLabel("endpoints", getDashboardCategory())}` : `Select filters to see ${mapLabel("endpoints", getDashboardCategory())}`}
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
                            <HorizontalStack gap={4} align="space-between">
                                <Checkbox
                                    label="Hide mismatched collections"
                                    checked={skipTagsMismatch}
                                    onChange={setSkipTagsMismatch}
                                    helpText="Filter out endpoints from collections with tags-mismatch=true"
                                />
                                <Button onClick={exploreEndpoints}>Explore {mapLabel("endpoints", getDashboardCategory())}</Button>
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

    const handleClick = () => {
        if(isUpdate){
            let dt = prepareData()
            api.updateCustomCollection(collectionId, dt).then((res) => {
                try {
                    func.setToast(true, false, "Conditions updated successfully")
                } catch (error) {
                    func.setToast(true,true, "Error in updating conditions")
                }
            })
        }else{
            openModal()
        }
    }

    const primaryActionLabel = isUpdate ? 'Update conditions' : 'Save as API Group'

    const findMissingUrls = useCallback(() => {
        navigate(`/dashboard/observe/debug-endpoints`);
        setMoreActions(false);
    }, [navigate]);

    const isAktoUser = window.USER_NAME && window.USER_NAME.includes('akto.io');

    const secondaryActionsComp = isAktoUser ? (
        <HorizontalStack gap={2}>
            <Popover
                active={moreActions}
                activator={(
                    <Button onClick={() => setMoreActions(!moreActions)} disclosure removeUnderline>
                        More Actions
                    </Button>
                )}
                autofocusTarget="first-node"
                onClose={() => { setMoreActions(false) }}
            >
                <Popover.Pane fixed>
                    <ActionList
                        actionRole="menuitem"
                        sections={
                            [
                                {
                                    title: 'Debug Mode',
                                    items: [
                                        {
                                            content: 'Find Missing Urls',
                                            onAction: findMissingUrls,
                                            prefix: <Box><Icon source={FileMinor} /></Box>,
                                            disabled: false
                                        }
                                    ]
                                }
                            ]
                        }
                    />
                </Popover.Pane>
            </Popover>
        </HorizontalStack>
    ) : null;

    return (
        <PageWithMultipleCards
            title={
                <TitleWithInfo
                    tooltipContent={"Query across your API ecosystem and create meaningful groups effortlessly"}
                    titleText={"Explore Mode"}
                />
            }
            primaryAction={<Button id={"explore-mode-query-page"} primary secondaryActions onClick={handleClick}>{primaryActionLabel}</Button>}
            secondaryActions={secondaryActionsComp}
            components={components}
            backUrl="dashboard/observe/query_mode"
        />
    )
}

export default APIQuery