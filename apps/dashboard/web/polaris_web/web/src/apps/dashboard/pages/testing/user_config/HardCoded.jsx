import { Button, Card, Collapsible, FormLayout, HorizontalGrid, HorizontalStack, Icon, LegacyCard, Link, Page, Text, TextContainer, TextField, Tooltip } from "@shopify/polaris"
import { InfoMinor } from "@shopify/polaris-icons"
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
            <FormLayout>
                <FormLayout.Group>
                <TextField
                    id={"auth-header-key-field"}
                    label={(
                        <HorizontalStack gap="2">
                            <Text>Auth header key</Text>
                            <Tooltip content="Please enter name of the header which contains your auth token. This field is case-sensitive. eg Authorization" dismissOnMouseOut width="wide" preferredPosition="below">
                                <Icon source={InfoMinor} color="base" />
                            </Tooltip>
                        </HorizontalStack>
                    )}
                    value={userConfig.authHeaderKey} placeholder='' onChange={(authHeaderKey) => updateUserConfig("authHeaderKey", authHeaderKey)} />   
                <TextField 
                    id={"auth-header-value-field"}
                    label={(
                        <HorizontalStack gap="2">
                            <Text>Auth header value</Text>
                            <Tooltip content="Please enter the value of the auth token." dismissOnMouseOut width="wide" preferredPosition="below">
                                <Icon source={InfoMinor} color="base" />
                            </Tooltip>
                        </HorizontalStack>
                    )}
                    value={userConfig.authHeaderValue} placeholder='' onChange={(authHeaderValue) => updateUserConfig("authHeaderValue", authHeaderValue)} />`
                </FormLayout.Group>
            </FormLayout>
            <br />
            <Button
                id={"save-token"}
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