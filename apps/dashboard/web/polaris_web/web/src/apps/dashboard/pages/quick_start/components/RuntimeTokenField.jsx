import { Box, Button, HorizontalStack, Text, Tooltip, VerticalStack } from '@shopify/polaris'
import { ClipboardMinor } from '@shopify/polaris-icons'
import { useEffect, useRef, useState } from 'react'
import api from '../api'
import func from "@/util/func"
import Dropdown from '../../../components/layouts/Dropdown'
import JsonComponent from './shared/JsonComponent'

const expiryDurationOptions = [
    { label: '1 month', value: 1 },
    { label: '3 months', value: 3 },
    { label: '6 months', value: 6 },
    { label: '9 months', value: 9 },
    { label: '12 months', value: 12 },
    { label: 'Never expire', value: -1 }
]

const getLabelFromValue = (value) => {
    const option = expiryDurationOptions.find(option => option.value === value)
    return option ? option.label : ''
}

function RuntimeTokenField({ id = "select-runtime-token-expiry", expiryStepText = "Select the expiry time of the token.", tokenStepText = "Copy the token below.", onTokenChange, codeStyle = false }) {
    const [apiToken, setApiToken] = useState("")
    const [selectedExpiryDuration, setSelectedExpiryDuration] = useState(6)
    const [selectedScope, setSelectedScope] = useState([])
    const [scopeOptions, setScopeOptions] = useState([])
    const ref = useRef(null)

    const fetchToken = async (expiryDuration, scope) => {
        await api.fetchRuntimeHelmCommand(expiryDuration, scope).then((resp) => {
            if (!resp) return
            setApiToken(resp?.apiToken)
            if (onTokenChange) onTokenChange(resp?.apiToken)
        })
    }

    const fetchModuleTypes = async () => {
        await api.fetchModuleTypes().then((resp) => {
            const moduleTypes = resp?.moduleTypes || []
            setScopeOptions(moduleTypes.map(value => ({
                label: value.split('_').map(func.toSentenceCase).join(' '),
                value
            })))
        })
    }

    useEffect(() => {
        fetchToken(selectedExpiryDuration, selectedScope)
        fetchModuleTypes()
    }, [])

    return (
        <VerticalStack gap="2">
            <div ref={ref} />

            <span>1. {expiryStepText} </span>
            <Box maxWidth="180px" paddingInlineStart={"4"}>
                <Dropdown
                    id={id}
                    menuItems={expiryDurationOptions}
                    value={getLabelFromValue(selectedExpiryDuration)}
                    initial={selectedExpiryDuration}
                    selected={(type) => { setSelectedExpiryDuration(type); fetchToken(type, selectedScope) }}
                />
            </Box>

            <span>2. (Optional) Select the scope(s) this token should be restricted to. Leave empty to generate a legacy (unscoped) token. </span>
            <Box maxWidth="280px" paddingInlineStart={"4"}>
                <Dropdown
                    id={`${id}-scope`}
                    allowMultiple
                    placeHolder="Select scope(s)"
                    menuItems={scopeOptions}
                    preSelected={selectedScope}
                    selected={(scope) => { setSelectedScope(scope); fetchToken(selectedExpiryDuration, scope) }}
                />
            </Box>

            <span>3. {tokenStepText} </span>
            <Box paddingInlineStart={"4"}>
                {codeStyle ? (
                    <div className="connector-code-token">
                        <JsonComponent
                            title="Token"
                            toolTipContent="Copy token"
                            onClickFunc={() => func.copyToClipboard(apiToken, ref, null)}
                            dataString={apiToken || " "}
                            language="text"
                            minHeight="72px"
                        />
                    </div>
                ) : (
                    <HorizontalStack gap="2" blockAlign="center" wrap={false}>
                        <Box background="bg-subdued" padding="2" borderRadius="2" borderWidth="1" borderColor="border-subdued">
                            <Text variant="bodyMd" breakWord>{apiToken}</Text>
                        </Box>
                        <Tooltip content="Copy token">
                            <Button icon={ClipboardMinor} plain onClick={() => func.copyToClipboard(apiToken, ref, null)} />
                        </Tooltip>
                    </HorizontalStack>
                )}
            </Box>
        </VerticalStack>
    )
}

export default RuntimeTokenField
