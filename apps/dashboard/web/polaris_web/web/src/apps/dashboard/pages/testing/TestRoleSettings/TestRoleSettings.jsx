import React, { useEffect, useState, useReducer } from 'react'
import { LegacyCard, HorizontalGrid, TextField, Divider, Collapsible, LegacyStack, Button, FormLayout, HorizontalStack, Tooltip, Icon, Text, VerticalStack, Modal, Box } from '@shopify/polaris'
import { useLocation, useNavigate } from 'react-router-dom'
import TestRolesConditionsPicker from '../../../components/TestRolesConditionsPicker';
import func from "@/util/func";
import api from '../api';
import transform from '../transform';
import DetailsPage from '../../../components/DetailsPage';
import {produce} from "immer"
import HardCoded from '../user_config/HardCoded';
import LoginStepBuilder from '../user_config/LoginStepBuilder';
import { ChevronRightMinor, ChevronDownMinor, InfoMinor } from '@shopify/polaris-icons';
import ParamsCard from './ParamsCard';
import JsonRecording from '../user_config/JsonRecording';
import Dropdown from '../../../components/layouts/Dropdown';
import { useSearchParams } from 'react-router-dom';
import TestingStore from '../testingStore';

const selectOptions = [
    {
        label: 'contains',
        value: 'CONTAINS'
    },
    {
        label: 'belongs to',
        value: 'BELONGS_TO',
        operators: [
            {
                label: 'OR',
                value: 'OR',
            }
        ],
        type: "MAP"
    },
    {
        label: 'does not belongs to',
        value: 'NOT_BELONGS_TO',
        operators: [{
            label: 'AND',
            value: 'AND',
        }],
        type: "MAP"
    }
]

function TestRoleSettings() {
    const location = useLocation();
    const navigate = useNavigate()
    const [searchParams] = useSearchParams();
    const systemRole = searchParams.get("system")

    const isDataInState = location.state && location?.state !== undefined && Object.keys(location?.state).length > 0
    const isDataInSearch = searchParams.get("name")
    const isNew = !isDataInState && !isDataInSearch
    const pageTitle = isNew ? "Add test role" : "Configure test role"
    const [initialItems, setInitialItems] = useState({ name: "" })
    const [conditions, dispatchConditions] = useReducer(produce((draft, action) => conditionsReducer(draft, action)), []);
    const [roleName, setRoleName] = useState(systemRole || "");
    const [change, setChange] = useState(false);
    const [currentInfo, setCurrentInfo] = useState({steps: [], authParams: {}});
    const [hardCodeAuthInfo, setHardCodeAuthInfo] = useState({authParams:[]})
    const [showAuthComponent, setShowAuthComponent] = useState(false)
    const [showAuthDeleteModal, setShowAuthDeleteModal] = useState(false)
    const [deletedIndex, setDeletedIndex] = useState(-1);
    const [headerKey, setHeaderKey] = useState('') ;
    const [headerValue, setHeaderValue] = useState('');
    const [automationType, setAutomationType] = useState("LOGIN_STEP_BUILDER")
    const automationOptions = [
        { label: "Login Step Builder", value: "LOGIN_STEP_BUILDER" },
        { label: "JSON Recording", value: "RECORDED_FLOW" },
    ]
    const [advancedHeaderSettingsOpen, setAdvancedHeaderSettingsOpen] = useState(false)
    const [refresh, setRefresh] = useState(false)
    const setAuthMechanism = TestingStore.getState().setAuthMechanism
    const [editableDoc, setEditableDocs] = useState(-1)

    function getAuthWithCondList() {
        return  initialItems?.authWithCondList
    }

    const resetFunc = (newItems) => {
        setChange(false);
        setRoleName(newItems.name || systemRole || "");
        dispatchConditions({type:"replace", conditions:transform.createConditions(newItems.endpoints)})
    }
    useEffect(() => {
        if (!isNew) {
            let newItems = initialItems
            if (isDataInState) {
                newItems = location.state
                setInitialItems(location.state);
                resetFunc(newItems)
            } else {
                async function fetchData(){
                    await api.fetchTestRoles().then((res) => {
                        let testRole = res.testRoles.find((testRole) => testRole.name === searchParams.get("name"));
                        if (testRole) {
                            let oo = {...testRole, endpoints: testRole.endpointLogicalGroup.testingEndpoints}
                            setInitialItems(oo)
                            resetFunc(oo)
                        } else {
                            resetFunc(newItems)
                        }
                    })
                }
                fetchData();

            }
        } else {
            resetFunc(initialItems)
        }
    }, [refresh])

    useEffect(() => {
        if (func.deepComparison(conditions, transform.createConditions(initialItems.endpoints))) {
            setChange(false);
        } else {
            setChange(true);
        }
    }, [conditions])

    const compareFunc = () => {
        return !change
    }

    const handleSelectAutomationType = async(type) => {
        setAutomationType(type)
    }

    const saveAction = async (updatedAuth=false, authWithCondLists = null) => {
        let andConditions = transform.filterContainsConditions(conditions, 'AND')
        let orConditions = transform.filterContainsConditions(conditions, 'OR')
        if (!(andConditions || orConditions) || roleName.length === 0) {
            func.setToast(true, true, "Please select valid values for a test role")
        } else {
            if (isNew) {
                api.addTestRoles(roleName, andConditions, orConditions).then((_) => {
                    func.setToast(true, false, "Test role added")
                    setChange(false);
                    navigate(null, { state: { name: roleName, endpoints: { andConditions: andConditions, orConditions: orConditions } },
                        replace:true })
                }).catch((err) => {
                    func.setToast(true, true, "Unable to add test role")
                }).finally(() => {
                    setRefresh(!refresh)
                })
                func.setToast(true, false, "Creating a new test role.")
            } else {
                api.updateTestRoles(roleName, andConditions, orConditions).then((_) => {
                    if(!updatedAuth){
                        func.setToast(true, false, "Test role updated successfully.")
                    }
                    setChange(false);
                    navigate(null, { state: { name: roleName, endpoints: { andConditions: andConditions, orConditions: orConditions }, authWithCondList: authWithCondLists || getAuthWithCondList()},
                        replace:true })
                }).catch((err) => {
                    func.setToast(true, true, "Unable to update test role")
                }).finally(() => {
                    setRefresh(!refresh)
                })
                func.setToast(true, false, "Updating test role.")
            }
        }
    }

    const handleTextChange = (val) => {
        setRoleName(val);
        if (val == initialItems.name) {
            setChange(false);
        } else {
            setChange(true);
        }
    }



    const setHardCodedInfo = (obj) => {
        setHardCodeAuthInfo(prev => ({
            ...prev,
            authParams: obj.authParams
        }))
    }

    const handleOpenEdit = (authObj,index) => {
        setAuthMechanism(authObj.authMechanism)
        const headerKVPairs = authObj.headerKVPairs || {}

        if(Object.keys(headerKVPairs).length > 0){
            setAdvancedHeaderSettingsOpen(true)
        }
        setShowAuthComponent(true)
        setHardcodedOpen(true)
        setEditableDocs(index)
    }

    const handleDeleteAuth = async() => {
        const resp = await api.deleteAuthFromRole(initialItems.name,deletedIndex)
        setShowAuthDeleteModal(false)
        setDeletedIndex(-1);
        await saveAction(true, resp.selectedRole.authWithCondList)
        func.setToast(true, false, "Auth mechanism removed from role successfully.")
    }

    const descriptionCard = (
        <LegacyCard title="Details" key="desc">
            <LegacyCard.Section>
                <HorizontalGrid gap="4" columns={2}>
                    <TextField
                        label="Name" value={roleName} disabled={systemRole}
                        placeholder='New test role name' onChange={isNew ? handleTextChange : () => { }}
                        requiredIndicator
                    />
                </HorizontalGrid>
            </LegacyCard.Section>
        </LegacyCard>
    )

    function conditionsReducer(draft, action){

        switch(action.type){
            case "replace": return action.conditions; break;
            case "add": draft.push(action.condition); break;
            case "update":
                if(action.obj.type){
                    if(func.getOption(selectOptions, action.obj.type).type == "MAP"){
                        if(func.getOption(selectOptions, draft[action.index].type).type==undefined){
                            draft[action.index].value={}
                        }
                    } else {
                        draft[action.index].value=""
                    }
                    draft[action.index].operator = func.getConditions(selectOptions, action.obj.type)[0].label
                }
                draft[action.index] = {...draft[action.index], ...action.obj}; break;
            case "delete": return draft.filter((item, index) => index !== action.index);
            default: break;
        }
    }

    const conditionsCard = (
        <LegacyCard title="Details" key="condition">
            <TestRolesConditionsPicker
                title="Role endpoint conditions"
                param="Endpoint"
                conditions={conditions}
                dispatch={dispatchConditions}
                selectOptions={selectOptions}
            />
        </LegacyCard>
    )

    const deleteModalComp = (
        <Modal
            open={showAuthDeleteModal}
            onClose={() => {setShowAuthDeleteModal(false); setDeletedIndex(-1)}}
            title="Are you sure?"
            primaryAction={{
                content: 'Delete auth mechanism',
                onAction: handleDeleteAuth
            }}
        >
            <Modal.Section>
                <Text variant="bodyMd">Are you sure you want to this auth mechanism.</Text>
            </Modal.Section>
        </Modal>
    )

    const savedParamComponent = (
        getAuthWithCondList() && getAuthWithCondList() !== undefined && getAuthWithCondList().length > 0 ?
        <LegacyCard title={<Text variant="headingMd">Configured auth details</Text>} key={"savedAuth"}>
            <br/>
            <Divider />
            <LegacyCard.Section>
                <VerticalStack gap={6}>
                    {getAuthWithCondList().map((authObj,index)=> {
                        return(
                            <ParamsCard showEdit={() => handleOpenEdit(authObj, index)} dataObj={authObj} key={JSON.stringify(authObj)} handleDelete={() => {setDeletedIndex(index); setShowAuthDeleteModal(true)}}/>
                        )
                    })}
                </VerticalStack>
            </LegacyCard.Section>
            {deleteModalComp}
        </LegacyCard>
        : null
    )

    const [hardcodedOpen, setHardcodedOpen] = useState(true);

    const handleToggleHardcodedOpen = () => setHardcodedOpen((prev) => !prev)

    const handleLoginInfo = (obj) => {
        setCurrentInfo(prev => ({
            ...prev,
            steps: obj.steps,
            authParams: obj.authParams
        }))
    }

    const addAuthButton = (
        <HorizontalStack align="end" key="auth-button">
            {isNew ? <Tooltip content= "Save the role first"><Button disabled>Add auth</Button></Tooltip> : <Button primary onClick={() => setShowAuthComponent(true)}><div data-testid="add_auth_button">Add auth</div></Button>}
        </HorizontalStack>
    )

    const handleCancel = () => {
        setShowAuthComponent(false)
        setCurrentInfo({})
        setHeaderKey('')
        setHeaderValue('')
        setHardCodeAuthInfo({authParams:[]})
    }

    const handleSaveAuthMechanism = async() => {
        const apiCond = {[headerKey] : headerValue};
        let resp = {}
        if(hardcodedOpen){
            const automationType = "HardCoded";
            const authParamData = hardCodeAuthInfo.authParams
            if(editableDoc > -1){
                resp = await api.updateAuthInRole(initialItems.name, apiCond, editableDoc, authParamData, automationType)
            }else{
                resp = await api.addAuthToRole(initialItems.name, apiCond, authParamData, automationType, null)
            }
            
        }else{
            const automationType = "LOGIN_REQUEST";
            
            let recordedLoginFlowInput = null;
            if(currentInfo.steps && currentInfo.steps.length > 0){
                if (currentInfo.steps[0].type === "RECORDED_FLOW") {
                    recordedLoginFlowInput = {
                        content: currentInfo.steps[0].content,
                        tokenFetchCommand: currentInfo.steps[0].tokenFetchCommand,
                        outputFilePath: null,
                        errorFilePath: null,
                    }
                }
            }
            resp = await api.addAuthToRole(initialItems.name, apiCond, currentInfo.authParams, automationType, currentInfo.steps, recordedLoginFlowInput)
        }
        handleCancel()
        await saveAction(true, resp.selectedRole.authWithCondList)
        func.setToast(true, false, "Auth mechanism added to role successfully.")
    }

    const authCard = (
            <LegacyCard title="Authentication details" key="auth" secondaryFooterActions={[{content: 'Cancel' ,destructive: true, onAction: handleCancel}]} primaryFooterAction={{content: <div data-testid="save_token_details_button">Save</div>, onAction: handleSaveAuthMechanism}}>
                <LegacyCard.Section title="Token details">
                    <LegacyStack vertical>
                        <Button
                            id={"hardcoded-token-expand-button"}
                            onClick={handleToggleHardcodedOpen}
                            ariaExpanded={hardcodedOpen}
                            icon={hardcodedOpen ? ChevronDownMinor : ChevronRightMinor}
                            ariaControls="hardcoded"
                        >
                            Hard coded
                        </Button>
                        <Collapsible
                            open={hardcodedOpen}
                            id="hardcoded"
                            transition={{ duration: '500ms', timingFunction: 'ease-in-out' }}
                            expandOnPrint
                        >
                            <HardCoded showOnlyApi={true} extractInformation={true} setInformation={setHardCodedInfo}/>
                        </Collapsible>
                    </LegacyStack>
                
                    <LegacyStack vertical>
                        <Button
                            id={"automated-token-expand-button"}
                            onClick={handleToggleHardcodedOpen}
                            ariaExpanded={!hardcodedOpen}
                            icon={!hardcodedOpen ? ChevronDownMinor : ChevronRightMinor}
                            ariaControls="automated"
                        >
                            Automated
                        </Button>
                        <Collapsible
                            open={!hardcodedOpen}
                            id="automated"
                            transition={{ duration: '500ms', timingFunction: 'ease-in-out' }}
                            expandOnPrint
                        >

                            <div style={{ display: "grid", gridTemplateColumns: "max-content max-content", gap: "10px", alignItems: "center" }}>
                                <Text>Select automation type:</Text>
                                <Dropdown
                                    selected={handleSelectAutomationType}
                                    menuItems={automationOptions}
                                    initial={automationType}
                                />
                            </div>
                            <br />

                            { automationType === "LOGIN_STEP_BUILDER" && <LoginStepBuilder extractInformation = {true} showOnlyApi={true} setStoreData={handleLoginInfo}/> }
                            { automationType === "RECORDED_FLOW" && <JsonRecording extractInformation = {true} showOnlyApi={true} setStoreData={handleLoginInfo}/> }
                        </Collapsible>
                    </LegacyStack>
                </LegacyCard.Section>
                <LegacyCard.Section title="More settings">
                    <VerticalStack gap={"2"}>
                        <Box>
                            <Button disclosure={advancedHeaderSettingsOpen ? "up" : "down"} onClick={() => setAdvancedHeaderSettingsOpen(!advancedHeaderSettingsOpen)}>
                                Advanced Settings
                            </Button>
                        </Box>
                        <Collapsible
                            open={advancedHeaderSettingsOpen}
                            transition={{ duration: '300ms', timingFunction: 'ease-in-out' }}
                        >
                            <VerticalStack gap={"2"}>
                                <Text variant="headingMd">Api header conditions</Text>
                                <FormLayout>
                                    <FormLayout.Group>
                                    <TextField
                                        id={"auth-header-key-field"}
                                        label={(
                                            <HorizontalStack gap="2">
                                                <Text>Header key</Text>
                                                <Tooltip content="Please enter name of the header which contains your auth token. This field is case-sensitive. eg Authorization" dismissOnMouseOut width="wide" preferredPosition="below">
                                                    <Icon source={InfoMinor} color="base" />
                                                </Tooltip>
                                            </HorizontalStack>
                                        )}
                                        value={headerKey}
                                        onChange={setHeaderKey}
                                        />
                                    <TextField 
                                        id={"auth-header-value-field"}
                                        label={(
                                            <HorizontalStack gap="2">
                                                <Text>Header value</Text>
                                                <Tooltip content="Please enter the value of the auth token." dismissOnMouseOut width="wide" preferredPosition="below">
                                                    <Icon source={InfoMinor} color="base" />
                                                </Tooltip>
                                            </HorizontalStack>
                                        )}
                                        value={headerValue}
                                        onChange={setHeaderValue}
                                        />
                                    </FormLayout.Group>
                                </FormLayout>
                            </VerticalStack>
                        </Collapsible>
                    </VerticalStack>
                </LegacyCard.Section>
            </LegacyCard>
    )

    const authComponent = showAuthComponent ? authCard : addAuthButton

    let components = [descriptionCard, conditionsCard, authComponent, savedParamComponent]

    return (
        <DetailsPage
            pageTitle={pageTitle}
            backUrl="/dashboard/testing/roles"
            saveAction={saveAction}
            discardAction={() => resetFunc(initialItems)}
            isDisabled={compareFunc}
            components={components}
        />
    )
}

export default TestRoleSettings;