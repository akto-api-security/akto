import React, { useEffect, useState, useReducer } from 'react'
import { LegacyCard, HorizontalGrid, TextField } from '@shopify/polaris'
import { useLocation, useNavigate } from 'react-router-dom'
import TestRolesConditionsPicker from '../../../components/TestRolesConditionsPicker';
import func from "@/util/func";
import api from '../api';
import transform from '../transform';
import DetailsPage from '../../../components/DetailsPage';
import {produce} from "immer"

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
    const isNew = location?.state != undefined && Object.keys(location?.state).length > 0 ? false : true
    const pageTitle = isNew ? "Add test role" : "Configure test role"
    const initialItems = isNew ? { name: "" } : location.state;
    const [conditions, dispatchConditions] = useReducer(produce((draft, action) => conditionsReducer(draft, action)), []);
    const [roleName, setRoleName] = useState("");
    const [change, setChange] = useState(false)
    const resetFunc = () => {
        setChange(false);
        setRoleName(initialItems.name ? initialItems.name : "");
        dispatchConditions({type:"replace", conditions:transform.createConditions(initialItems.endpoints)})
    }
    useEffect(() => {
        resetFunc()
    }, [])

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

    const saveAction = async () => {
        let andConditions = transform.filterContainsConditions(conditions, 'AND')
        let orConditions = transform.filterContainsConditions(conditions, 'OR')
        if (!(andConditions || orConditions) || roleName.length == 0) {
            func.setToast(true, true, "Please select valid values for a test role")
        } else {
            if (isNew) {
                api.addTestRoles(roleName, andConditions, orConditions).then((res) => {
                    func.setToast(true, false, "Test role added")
                    setChange(false);
                    navigate(null, { state: { name: roleName, endpoints: { andConditions: andConditions, orConditions: orConditions } }, 
                        replace:true })
                }).catch((err) => {
                    func.setToast(true, true, "Unable to add test role")
                })
            } else {
                api.updateTestRoles(roleName, andConditions, orConditions).then((res) => {
                    func.setToast(true, false, "Test role updated")
                    setChange(false);
                    navigate(null, { state: { name: roleName, endpoints: { andConditions: andConditions, orConditions: orConditions } },
                        replace:true })
                }).catch((err) => {
                    func.setToast(true, true, "Unable to update test role")
                })
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

    const descriptionCard = (
        <LegacyCard title="Details" key="desc">
            <LegacyCard.Section>
                <HorizontalGrid gap="4" columns={2}>
                    <TextField
                        label="Name" value={roleName}
                        placeholder='New test role name' onChange={isNew ? handleTextChange : () => { }}
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
            case "delete": draft = draft.filter((item, index) => index !== action.index);
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

    let components = [descriptionCard, conditionsCard]

    return (
        <DetailsPage
        pageTitle={pageTitle}
        saveAction={saveAction}
        discardAction={resetFunc}
        isDisabled={compareFunc}
        components={components}
        />
    )
}

export default TestRoleSettings;