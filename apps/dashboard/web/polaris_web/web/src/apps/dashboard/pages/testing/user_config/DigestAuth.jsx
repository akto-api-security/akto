import { useEffect, useState } from "react";
import TestingStore from "../testingStore";
import { Divider, LegacyCard, TextField, VerticalStack, Text, FormLayout, Select } from "@shopify/polaris";

function DigestAuth(props) {
    const { setInformation } = props;
    
    const authMechanism = TestingStore(state => state.authMechanism);
    const [authParams, setAuthParams] = useState([{
        username: "",
        password: "",
        targetUrl: "",
        method: "GET",
        algorithm: "SHA-256"
    }]);

    useEffect(() => {
        if (authMechanism && authMechanism?.type.toUpperCase() === "DIGEST_AUTH") {
            // Load existing digest auth configuration
            if (authMechanism.authParams && authMechanism.authParams.length > 0) {
                const existingParam = authMechanism.authParams[0];
                setAuthParams([{
                    username: existingParam.username || "",
                    password: existingParam.password || "",
                    targetUrl: existingParam.targetUrl || "",
                    method: existingParam.method || "GET",
                    algorithm: existingParam.algorithm || "SHA-256"
                }]);
            }
        }
    }, [authMechanism]);

    useEffect(() => {
        // Transform digest auth data to the format expected by backend
        const digestAuthParams = [];
        const param = authParams[0];

        // Always push all parameters to maintain structure
        digestAuthParams.push({
            key: 'username',
            value: param.username || '',
            where: 'HEADER'
        });
        digestAuthParams.push({
            key: 'password',
            value: param.password || '',
            where: 'HEADER'
        });
        digestAuthParams.push({
            key: 'targetUrl',
            value: param.targetUrl || '',
            where: 'HEADER'
        });
        digestAuthParams.push({
            key: 'method',
            value: param.method || 'GET',
            where: 'HEADER'
        });
        digestAuthParams.push({
            key: 'algorithm',
            value: param.algorithm || 'SHA-256',
            where: 'HEADER'
        });

        setInformation({ authParams: digestAuthParams });
    }, [authParams, setInformation]);

    function updateAuthParam(index, field, value) {
        const updatedParams = [...authParams];
        updatedParams[index][field] = value;
        setAuthParams(updatedParams);
    }

    return (
        <VerticalStack>
            <LegacyCard title="Digest Authentication Configuration" sectioned>
                <Divider />
                <LegacyCard.Section>
                    <VerticalStack gap="4">
                        <Text color="subdued">
                            Configure digest authentication credentials for API testing. 
                            Digest auth uses a challenge-response mechanism with cryptographic hashing.
                        </Text>
                        
                        <FormLayout>
                            <FormLayout.Group>
                                <TextField
                                    label="Username"
                                    value={authParams[0].username}
                                    onChange={(value) => updateAuthParam(0, 'username', value)}
                                    placeholder="Enter username"
                                    autoComplete="off"
                                    helpText="Username for digest authentication"
                                />
                                <TextField
                                    label="Password"
                                    type="password"
                                    value={authParams[0].password}
                                    onChange={(value) => updateAuthParam(0, 'password', value)}
                                    placeholder="Enter password"
                                    autoComplete="off"
                                    helpText="Password for digest authentication"
                                />
                            </FormLayout.Group>
                            
                            <TextField
                                label="Target URL"
                                value={authParams[0].targetUrl}
                                onChange={(value) => updateAuthParam(0, 'targetUrl', value)}
                                placeholder="https://api.example.com/endpoint"
                                helpText="Enter the complete URL of the API endpoint that requires digest auth"
                                autoComplete="off"
                            />
                            
                            <TextField
                                label="HTTP Method"
                                value={authParams[0].method}
                                onChange={(value) => updateAuthParam(0, 'method', value)}
                                placeholder="GET"
                                helpText="HTTP method for the initial challenge request (default: GET)"
                                autoComplete="off"
                            />

                            <Select
                                label="Algorithm"
                                options={[
                                    { label: "SHA-256", value: "SHA-256" },
                                    { label: "MD5", value: "MD5" }
                                ]}
                                value={authParams[0].algorithm}
                                onChange={(value) => updateAuthParam(0, 'algorithm', value)}
                                helpText="Hashing algorithm for digest authentication"
                            />
                        </FormLayout>
                        
                        <VerticalStack gap="2">
                            <Text variant="headingSm">How Digest Authentication Works:</Text>
                            <Text color="subdued" variant="bodyMd">
                                1. Initial request is sent to the server without credentials
                            </Text>
                            <Text color="subdued" variant="bodyMd">
                                2. Server responds with 401 and a challenge containing nonce, realm, etc.
                            </Text>
                            <Text color="subdued" variant="bodyMd">
                                3. Client computes a hash using the credentials and challenge parameters
                            </Text>
                            <Text color="subdued" variant="bodyMd">
                                4. Client sends the request again with the computed digest in the Authorization header
                            </Text>
                            <Text color="subdued" variant="bodyMd">
                                5. This process happens automatically for each test request
                            </Text>
                        </VerticalStack>
                    </VerticalStack>
                </LegacyCard.Section>
            </LegacyCard>
        </VerticalStack>
    );
}

export default DigestAuth;