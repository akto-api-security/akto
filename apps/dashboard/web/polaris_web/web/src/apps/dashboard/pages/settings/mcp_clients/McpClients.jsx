import { Fragment, useEffect, useState } from "react"
import { Box, Button, Checkbox, Divider, Form, HorizontalStack, LegacyCard, Text, TextField, VerticalStack } from "@shopify/polaris"
import PageWithMultipleCards from "../../../components/layouts/PageWithMultipleCards"
import TitleWithInfo from "../../../components/shared/TitleWithInfo"
import func from "@/util/func"
import settingRequests from "../api"

const SAVE_FAILED = "Unable to save. Try again."

function McpClients() {
    const [knownOnly, setKnownOnly] = useState(false)
    const [uris, setUris] = useState([])
    const [newUri, setNewUri] = useState("")
    const [loading, setLoading] = useState(true)
    const [saving, setSaving] = useState(false)

    const apply = (settings) => {
        setKnownOnly(!!settings?.mcpKnownClientsOnly)
        setUris(settings?.mcpAllowedRedirectUris || [])
    }

    useEffect(() => {
        settingRequests.fetchAdminSettings()
            .then((resp) => apply(resp?.accountSettings))
            .catch(() => func.setToast(true, true, "Unable to load MCP client settings"))
            .finally(() => setLoading(false))
    }, [])

    // The server answers with the saved settings, so the page always shows what is stored
    const save = async (call, message) => {
        setSaving(true)
        try {
            const resp = await call()
            apply(resp?.accountSettings)
            func.setToast(true, false, message)
            return true
        } catch (err) {
            func.setToast(true, true, err?.response?.data?.actionErrors?.[0] || SAVE_FAILED)
            return false
        } finally {
            setSaving(false)
        }
    }

    const addUri = async () => {
        if (await save(() => settingRequests.addMcpAllowedRedirectUri(newUri.trim()), "Redirect URL allowed")) {
            setNewUri("")
        }
    }

    const removeUri = (uri) => func.showConfirmationModal(
        `Remove ${uri}? Apps that depend on it are disconnected when "Only allow known MCP clients" is on.`,
        "Remove",
        () => save(() => settingRequests.deleteMcpAllowedRedirectUri(uri), "Redirect URL removed")
    )

    const accessCard = (
        <LegacyCard key="mcp-known-only" title="Access" sectioned>
            <VerticalStack gap="3">
                <Checkbox
                    label="Only allow known MCP clients"
                    checked={knownOnly}
                    disabled={loading || saving}
                    onChange={(checked) => save(() => settingRequests.updateMcpKnownClientsOnly(checked), checked ? "Only known MCP clients can connect" : "Any MCP client can connect, with a warning for unknown ones")}
                />
                <Text as="p" color="subdued">
                    Apps such as Claude, ChatGPT and VS Code are on Akto's built-in list. When this is on, any other app that connects over the internet cannot be approved, and apps that are no longer allowed are disconnected (their access ends within an hour). Apps that run on the user's own device are not affected. When it is off, users see an unverified-app warning for unknown apps.
                </Text>
            </VerticalStack>
        </LegacyCard>
    )

    const urlsCard = (
        <LegacyCard key="mcp-allowed-urls" title="Allowed redirect URLs" sectioned>
            <VerticalStack gap="4">
                <Text as="p" color="subdued">
                    Add the redirect URL of an app you trust. End it with * to allow everything under that path. These are allowed in addition to the built-in list.
                </Text>
                <Form onSubmit={addUri}>
                    <HorizontalStack gap="2" blockAlign="center" wrap={false}>
                        <div style={{ flex: 1 }}>
                            <TextField
                                label="Redirect URL"
                                labelHidden
                                value={newUri}
                                onChange={setNewUri}
                                placeholder="https://app.example.com/oauth/callback"
                                autoComplete="off"
                                disabled={saving}
                            />
                        </div>
                        <Button submit loading={saving} disabled={!newUri.trim()}>Add</Button>
                    </HorizontalStack>
                </Form>
                <Divider />
                {uris.length === 0 ? <Text as="p" color="subdued">No custom URLs yet.</Text> : (
                    <VerticalStack gap="2">
                        <Text as="h3" variant="headingSm">Allowed URLs ({uris.length})</Text>
                        <LegacyCard>
                            {uris.map((uri, index) => (
                                <Fragment key={uri}>
                                    {index > 0 ? <Divider /> : null}
                                    <Box padding="3">
                                        <HorizontalStack align="space-between" blockAlign="center" wrap={false} gap="4">
                                            <Text as="span" breakWord>{uri}</Text>
                                            <Button plain destructive disabled={saving} onClick={() => removeUri(uri)}>Remove</Button>
                                        </HorizontalStack>
                                    </Box>
                                </Fragment>
                            ))}
                        </LegacyCard>
                    </VerticalStack>
                )}
            </VerticalStack>
        </LegacyCard>
    )

    return (
        <PageWithMultipleCards
            title={<TitleWithInfo titleText="MCP clients" tooltipContent="Control which apps can connect to Akto as MCP clients" />}
            isFirstPage={true}
            components={[accessCard, urlsCard]}
        />
    )
}

export default McpClients
