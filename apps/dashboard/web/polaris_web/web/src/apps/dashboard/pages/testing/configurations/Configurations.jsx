import { Button, Collapsible, Divider, LegacyCard, LegacyStack, Text } from "@shopify/polaris"
import { ChevronRightMinor, ChevronDownMinor } from '@shopify/polaris-icons';
import { useState } from "react";
import api from "../api"
import { useEffect } from "react";
import HardCoded from "./HardCoded";
import SpinnerCentered from "../../../components/progress/SpinnerCentered";
import TestingStore from "../testingStore";
import Automated from "./Automated";
import Store from "../../../store";
import PageWithMultipleCards from "../../../components/layouts/PageWithMultipleCards"
import GithubSimpleTable from "../../../components/tables/GithubSimpleTable";

const headers = [
    {
        title: "Config name",
        text: "Config name",
        value: "configName",
    },
    {
        title: "Status",
        text: "Status",
        value: "Status",
    },
    {   
        title: 'Values',
        text: 'Values', 
        value: 'values',
    },
    {
        title: 'Impacting categories', 
        text: 'Impacting categories', 
        value: 'impactingCategories',
    }
]


function Configurations() {

    const setToastConfig = Store(state => state.setToastConfig)
    const setAuthMechanism = TestingStore(state => state.setAuthMechanism)
    const [isLoading, setIsLoading] = useState(true)
    const [hardcodedOpen, setHardcodedOpen] = useState(true);


    async function fetchAllSubCategories() {
        setIsLoading(true)
        const allSubCategoriesResponse =  await api.fetchAllSubCategories(true)
        setIsLoading(false)
    }

    async function fetchAuthMechanismData() {
        setIsLoading(true)
        const authMechanismDataResponse = await api.fetchAuthMechanismData()
        if (authMechanismDataResponse && authMechanismDataResponse.authMechanism) {
            const authMechanism = authMechanismDataResponse.authMechanism
            setAuthMechanism(authMechanism)
            if (authMechanism.type === "HARDCODED") setHardcodedOpen(true)
            else setHardcodedOpen(false)
        }
        setIsLoading(false)
    }

    useEffect(() => {
        fetchAllSubCategories()
    }, [])

    async function handleStopAlltests() {
        await api.stopAllTests()
        setToastConfig({ isActive: true, isError: false, message: "All tests stopped!" })
    }

    const bodyComponent = (
        <GithubSimpleTable
            filters={[]}
            headers={headers}
            selectable={true}
            headings={headers}
            useNewRow={true}
            condensedHeight={true}
        />
    )

    const components = [bodyComponent]

    return (
        isLoading ? <SpinnerCentered /> 
           :<PageWithMultipleCards 
                components={components}
                isFirstPage={true}
                divider={true}
                title ={
                    <Text variant="headingLg">
                        Configurations
                    </Text>
                }
                primaryAction={{ content: 'Stop all tests', onAction: handleStopAlltests }}
            />

    )
}

export default Configurations