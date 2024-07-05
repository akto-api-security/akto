import PageWithMultipleCards from "../../../components/layouts/PageWithMultipleCards"
import authTypesApi from "./api";
import { useNavigate } from "react-router-dom";
import { useState, useCallback, useEffect } from "react";
import { Modal, Button, Box } from "@shopify/polaris";
import GithubSimpleTable from "../../../components/tables/GithubSimpleTable";
import func from "@/util/func";
import {
    CustomersMinor,
    ClockMinor,
    CircleTickMajor
  } from '@shopify/polaris-icons';
import EmptyScreensLayout from "../../../components/banners/EmptyScreensLayout";
import { AUTH_TYPES_PAGE_DOCS_URL } from "../../../../main/onboardingData";

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
        },
        {
            text: "",
            value: "isActive",
            itemOrder: 3,
            icon:CircleTickMajor
        },
    ]

    const resourceName = {
        singular: 'auth type',
        plural: 'auth types',
    };

    const [authTypes, setAuthTypes] = useState([]);
    const [showEmptyScreen, setShowEmptyScreen] = useState(false)
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
                    headerConditions: item?.headerKeys, payloadConditions: item?.payloadKeys } })
            }]
        }]
    }

    useEffect(() => {
        setLoading(true);

        async function fetchData() {
            await authTypesApi.fetchCustomAuthTypes().then((res) => {
                let usersMap = res.usersMap;
                setShowEmptyScreen(res.customAuthTypes.length === 0)
                setAuthTypes(res.customAuthTypes.map((authType) => {
                    authType.id = authType.name
                    authType.updatedTimestamp = func.prettifyEpoch(authType.timestamp);
                    authType.createdBy = usersMap[authType.creatorId]
                    authType.isActive = authType.active ? "Active" : "Inactive"
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
            showEmptyScreen ? 
                <EmptyScreensLayout key={"emptyScreen"}
                    iconSrc={"/public/file_lock.svg"}
                    headingText={"No Auth type"}
                    description={"Define custom auth mechanism based on where you send the auth key. We support auth key detection in header, payload and even cookies in your APIs."}
                    buttonText={"Create auth type"}
                    redirectUrl={"/dashboard/settings/auth-types/details"}
                    learnText={"Creating auth type"}
                    docsUrl={AUTH_TYPES_PAGE_DOCS_URL}
                />

            
            : <GithubSimpleTable
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
                    Please mark the auth types you wish to reset as inactive, before resetting. Are you sure you want to reset all custom auth types in your API inventory?
                </Modal.Section>
            </Modal>
        </Box>
    )
}

export default AuthTypes