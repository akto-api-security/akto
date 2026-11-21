import { Box, Button, HorizontalStack, Text, TextField, Tooltip, VerticalStack } from '@shopify/polaris'
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

const defaultTokenName = () => `token-${new Date().toISOString().slice(0, 10)}`

// fixedScope hides the scope step, showToken=false hides the token box (caller shows it), startStep offsets the step numbers.
function RuntimeTokenField({ id = "select-runtime-token-expiry", expiryStepText = "Select the expiry time of the token.", tokenStepText = "Copy the token below.", onTokenChange, codeStyle = false, initialName, onCreated, fixedScope, showToken = true, startStep = 1 }) {
    const [name, setName] = useState(initialName || defaultTokenName())
    const [apiToken, setApiToken] = useState("")
    const [tokenHint, setTokenHint] = useState("")
    const [selectedExpiryDuration, setSelectedExpiryDuration] = useState(6)
    const [selectedScope, setSelectedScope] = useState([])
    const [scopeOptions, setScopeOptions] = useState([])
    const [loading, setLoading] = useState(false)
    const [nameError, setNameError] = useState("")
    // bumped on "Generate another" so the dropdowns remount with fresh state
    const [formKey, setFormKey] = useState(0)
    const ref = useRef(null)

    const created = apiToken !== ""
    const expiryStepNo = startStep
    const scopeStepNo = fixedScope ? null : startStep + 1
    const tokenStepNo = startStep + (fixedScope ? 1 : 2)

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
        if (!fixedScope) fetchModuleTypes()
    }, [])

    const generateToken = async () => {
        const trimmedName = name.trim()
        if (!trimmedName) {
            setNameError("Token name is required")
            return
        }
        setNameError("")
        setLoading(true)
        try {
            const resp = await api.createAuthToken(trimmedName, fixedScope || selectedScope, selectedExpiryDuration)
            if (!resp?.apiToken) return
            setApiToken(resp.apiToken)
            setTokenHint(resp.hint || "")
            if (onTokenChange) onTokenChange(resp.apiToken)
            if (onCreated) onCreated(resp)
        } catch (e) {
            // the request layer already toasts the error
        } finally {
            setLoading(false)
        }
    }

    const generateAnother = () => {
        setApiToken("")
        setTokenHint("")
        setName(initialName || defaultTokenName())
        setFormKey(key => key + 1)
        if (onTokenChange) onTokenChange("")
    }

    const tokenOutput = codeStyle ? (
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
    )

    return (
        <VerticalStack gap="2" key={formKey}>
            <div ref={ref} />

            <span>{expiryStepNo}. {expiryStepText} </span>
            <Box maxWidth="280px" paddingInlineStart={"4"}>
                <VerticalStack gap="2">
                    <TextField
                        label="Token name"
                        value={name}
                        onChange={(value) => { setName(value); setNameError("") }}
                        error={nameError}
                        maxLength={64}
                        disabled={created}
                        autoComplete="off"
                        helpText="Helps you recognise this token later in Module Info."
                    />
                    <Dropdown
                        id={id}
                        menuItems={expiryDurationOptions}
                        value={getLabelFromValue(selectedExpiryDuration)}
                        initial={selectedExpiryDuration}
                        disabled={created}
                        selected={(type) => setSelectedExpiryDuration(type)}
                    />
                </VerticalStack>
            </Box>

            {!fixedScope && (
                <>
                    <span>{scopeStepNo}. (Optional) Select the scope(s) this token should be restricted to. Leave empty to generate a legacy (unscoped) token. </span>
                    <Box maxWidth="280px" paddingInlineStart={"4"}>
                        <Dropdown
                            id={`${id}-scope`}
                            allowMultiple
                            placeHolder="Select scope(s)"
                            itemName="scope"
                            value={selectedScope.length > 0 ? `${selectedScope.length} scope${selectedScope.length === 1 ? '' : 's'} selected` : ''}
                            menuItems={scopeOptions}
                            preSelected={selectedScope}
                            disabled={created}
                            selected={(scope) => setSelectedScope(scope)}
                        />
                    </Box>
                </>
            )}

            {created ? (
                <>
                    <span>{tokenStepNo}. {tokenStepText} </span>
                    <Box paddingInlineStart={"4"}>
                        <VerticalStack gap="2">
                            {showToken && tokenOutput}
                            <Text variant="bodySm" color="critical">
                                Copy it now, it won't be shown again.
                                {tokenHint ? ` Token ends with …${tokenHint}` : ""}
                            </Text>
                            <HorizontalStack>
                                <Button onClick={generateAnother}>Generate another</Button>
                            </HorizontalStack>
                        </VerticalStack>
                    </Box>
                </>
            ) : (
                <>
                    <span>{tokenStepNo}. Generate the token. </span>
                    <Box paddingInlineStart={"4"}>
                        <HorizontalStack>
                            <Button primary loading={loading} onClick={generateToken}>Generate token</Button>
                        </HorizontalStack>
                    </Box>
                </>
            )}
        </VerticalStack>
    )
}

export default RuntimeTokenField
