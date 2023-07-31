import { Button, Card, Collapsible, Icon, LegacyCard, Link, Page, Text, TextContainer, TextField } from "@shopify/polaris"
import { useState } from "react";
import api from "../api"
import Store from "../../../store";
import { useEffect } from "react";
import TestingStore from "../testingStore";

function HardCoded() {

    const setToastConfig = Store(state => state.setToastConfig)
    const authMechanism = TestingStore(state => state.authMechanism)

    const [userConfig, setUserConfig] = useState({
        authHeaderKey: "",
        authHeaderValue: ""
    })
    const [hasChanges, setHasChanges] = useState(false)

    useEffect(() => {
        if (authMechanism && authMechanism.type === "HARDCODED") {
            const authParam = authMechanism.authParams[0]
            setUserConfig({
                authHeaderKey: authParam.key,
                authHeaderValue: authParam.value
            })
        }
    }, [])

    function updateUserConfig(field, value) {
        setUserConfig(prev => ({
            ...prev,
            [field]: value
        }))
        setHasChanges(true)
    }

    async function handleSave() {
        await api.addAuthMechanism(
            "HARDCODED",
            [],
            [{
                "key": userConfig.authHeaderKey,
                "value": userConfig.authHeaderValue,
                "where": "HEADER"
            }]
        )
        setToastConfig({ isActive: true, isError: false, message: "Hard coded auth token saved successfully!" })
    }       

    return (
        <div>
            <Text variant="headingMd">Inject hard-coded attacker auth token</Text>
            <br />
            <TextField
                label="Auth header key"
                value={userConfig.authHeaderKey} placeholder='' onChange={(authHeaderKey) => updateUserConfig("authHeaderKey", authHeaderKey)} />
            <br />
            <TextField label="Auth header value" value={userConfig.authHeaderValue} placeholder='' onChange={(authHeaderValue) => updateUserConfig("authHeaderValue", authHeaderValue)} />
            <br />
            <Button
                primary
                disabled={!hasChanges}
                onClick={handleSave}
            >
                Save changes
            </Button>
        </div>
    )
}

export default HardCoded