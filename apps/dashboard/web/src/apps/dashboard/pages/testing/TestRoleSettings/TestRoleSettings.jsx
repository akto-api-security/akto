import React, { useEffect, useState } from 'react'
import PageWithMultipleCards from '../../../components/layouts/PageWithMultipleCards';
import { LegacyCard, HorizontalGrid, TextField } from '@shopify/polaris'
import ContextualLayout from '../../../components/layouts/ContextualLayout';
import { useLocation, useNavigate } from 'react-router-dom'
import TestRolesConditionsPicker from '../../../components/TestRolesConditionsPicker';
import func from "@/util/func";
import api from '../api';
import transform from '../transform';
import Store from '../../../store';

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
    const [conditions, setConditions] = useState([]);
    const [roleName, setRoleName] = useState("");
    const [change, setChange] = useState(false)
    const resetFunc = () => {
        setChange(false);
        setRoleName(initialItems.name ? initialItems.name : "");
        setConditions(transform.createConditions(initialItems.endpoints));
    }
    const setToastConfig = Store(state => state.setToastConfig)
    const setToast = (isActive, isError, message) => {
        setToastConfig({
            isActive: isActive,
            isError: isError,
            message: message
        })
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
            setToast(true, true, "Please select valid values for a test role")
        } else {
            if (isNew) {
                api.addTestRoles(roleName, andConditions, orConditions).then((res) => {
                    setToast(true, false, "Test role added")
                    setChange(false);
                    navigate(null, { state: { name: roleName, endpoints: { andConditions: andConditions, orConditions: orConditions } } })
                }).catch((err) => {
                    setToast(true, true, "Unable to add test role")
                })
            } else {
                api.updateTestRoles(roleName, andConditions, orConditions).then((res) => {
                    setToast(true, false, "Test role updated")
                    setChange(false);
                    navigate(null, { state: { name: roleName, endpoints: { andConditions: andConditions, orConditions: orConditions } } })
                }).catch((err) => {
                    setToast(true, true, "Unable to update test role")
                })
            }
        }
    }
    const navigateBack = () => {
        navigate("/dashboard/testing/roles")
    }

    const handleChange = (val) => {
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
                        placeholder='New test role name' onChange={isNew ? handleChange : () => { }}
                    />
                </HorizontalGrid>
            </LegacyCard.Section>
        </LegacyCard>
    )

    const conditionsCard = (
        <LegacyCard title="Details" key="condition">
            <TestRolesConditionsPicker
                title="Role endpoint conditions"
                param="Endpoint"
                conditions={conditions}
                setConditions={setConditions}
                selectOptions={selectOptions}
            />
        </LegacyCard>
    )

    let components = [descriptionCard, conditionsCard]

    const pageMarkup = (
        <PageWithMultipleCards title={pageTitle}
            backAction={{ onAction: navigateBack }}
            divider
            components={components}
        />
    )

    return (
        <ContextualLayout
            saveAction={saveAction}
            discardAction={resetFunc}
            isDisabled={compareFunc}
            pageMarkup={pageMarkup}
        />
    )
}

export default TestRoleSettings;