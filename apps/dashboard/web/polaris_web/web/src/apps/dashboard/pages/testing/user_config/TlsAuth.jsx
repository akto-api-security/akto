import { useEffect, useState } from "react";
import TestingStore from "../testingStore";
import { Divider, LegacyCard, TextField, VerticalStack } from "@shopify/polaris";

function TlsAuth(props) {

    const { setInformation } = props

    const authMechanism = TestingStore(state => state.authMechanism)
    const [authParams, setAuthParams] = useState([{
        certAuthorityCertificate: "",
        clientCertificate: "",
        clientKey: "",
        certificateType: "PEM",
    }])

    useEffect(() => {
        if (authMechanism && authMechanism?.type.toUpperCase() === "TLS_AUTH") {
            setAuthParams(authMechanism.authParams)
        }
    }, [authMechanism])

    useEffect(() => {
        setInformation({ authParams })
    }, [authParams])

    function getLabel(key) {
        switch (key) {
            case "certAuthorityCertificate":
                return "CA certificate"
            case "clientCertificate":
                return "Client certificate"
            case "clientKey":
                return "Client key"
            case "certificateType":
                return "Certificate type"
            default:
                return key
        }
    }

    return (
        <VerticalStack>
            <LegacyCard title="Add client TLS settings" sectioned>
                <Divider />
                <LegacyCard.Section>
                    {
                        authParams.map((authParam, index) => {
                            return (
                                <div key={index} >
                                    <VerticalStack gap="4">
                                        {
                                            Object.keys(authParam).map((key, i) => {

                                                if(getLabel(key) === key) {
                                                    return <></>
                                                }

                                                return (
                                                    <TextField
                                                        multiline
                                                        label={getLabel(key)}
                                                        value={authParam[key]}
                                                        disabled={key === "certificateType"}
                                                        onChange={(value) => {
                                                            setAuthParams(prev => {
                                                                return prev.map((authParam, i) => {
                                                                    if (i === index) {
                                                                        return { ...authParam, [key]: value }
                                                                    } else
                                                                        return authParam
                                                                })
                                                            })
                                                        }}
                                                    />
                                                )
                                            })
                                        }
                                    </VerticalStack>
                                </div>
                            )
                        })}
                </LegacyCard.Section>
            </LegacyCard>
        </VerticalStack>
    )
}

export default TlsAuth;