import PageWithMultipleCards from "../../../components/layouts/PageWithMultipleCards"
import authTypesApi from "./api";
import { useNavigate } from "react-router-dom";
import { useState, useCallback, useEffect } from "react";
import { Modal, Button, Box } from "@shopify/polaris";
import GithubSimpleTable from "../../../components/tables/GithubSimpleTable";
import func from "@/util/func";
import {
    CustomersMinor,
    ClockMinor
  } from '@shopify/polaris-icons';

function AuthTypes() {
    const headers = [
        {
            text: "Name",
            value: "name",
            itemOrder: 1
        },
        {
            text: "Last updated",
            value: "updatedTimestamp",
            itemOrder: 3,
            icon:ClockMinor
        },
        {
            text: "Created by",
            value: "createdBy",
            itemOrder: 3,
            icon:CustomersMinor
        }
    ]

    const resourceName = {
        singular: 'auth type',
        plural: 'auth types',
    };

    const [authTypes, setAuthTypes] = useState([]);
    const [loading, setLoading] = useState(false);
    const navigate = useNavigate()


    const handleRedirect = () => {
        navigate("details")
    }

    const [resetModalActive, setResetModalActive] = useState(false);

    const handleResetModalChange = useCallback(() => setResetModalActive(!resetModalActive), [resetModalActive]);
    const handleReset = () => {
        authTypesApi.resetAllCustomAuthTypes().then((res) => {
            func.setToast(true, false, "Custom auth types reset")
        }).catch((err) => {
            func.setToast(true, true, "Unable to reset auth types")
        });
        handleResetModalChange();
    }

    const getActions = (item) => {
        return [{
            items: [{
                content: 'Edit',
                onAction: () => navigate("details", { state: { name: item?.name, active: item?.active,
                    headerConditions: item?.headerKeys, payloadConditions: item?.payloadKeys } }),
            }]
        }]
    }

    useEffect(() => {
        setLoading(true);

        async function fetchData() {
            await authTypesApi.fetchCustomAuthTypes().then((res) => {
                let usersMap = res.usersMap;
                setAuthTypes(res.customAuthTypes.map((authType) => {
                    authType.id = authType.name
                    authType.updatedTimestamp = func.prettifyEpoch(authType.timestamp);
                    authType.createdBy = usersMap[authType.creatorId]
                    return authType;
                }));
                setLoading(false);
            })
        }
        fetchData();
    }, [])

    return (
        <Box>
        <PageWithMultipleCards
            title={"Auth types"}
            primaryAction={<Button primary onClick={handleRedirect}>Create new auth type</Button>}
            secondaryActions={
                <Button onClick={() => handleResetModalChange()}>Reset</Button>
            }
            isFirstPage={true}
            components={[
                <GithubSimpleTable
                    key="table"
                    data={authTypes}
                    resourceName={resourceName}
                    headers={headers}
                    loading={loading}
                    getActions={getActions}
                    hasRowActions={true}
                />
            ]}
        />
            <Modal
                open={resetModalActive}
                onClose={handleResetModalChange}
                title="Reset authentication types"
                primaryAction={{
                    content: 'Reset',
                    onAction: handleReset,
                }}
                secondaryActions={[
                    {
                        content: 'Cancel',
                        onAction: handleResetModalChange,
                    },
                ]}
            >
                <Modal.Section>
                    Are you sure you want to reset all custom auth types in your API inventory?
                </Modal.Section>
            </Modal>
        </Box>
    )
}

export default AuthTypes