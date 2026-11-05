import { useEffect, useState } from "react"
import { useSearchParams } from "react-router-dom"
import { Avatar, Banner, Box, Button, Checkbox, Divider, HorizontalStack, LegacyCard, Page, Spinner, Text, VerticalStack } from "@shopify/polaris"
import DropdownSearch from "../../components/shared/DropdownSearch"
import { PRODUCT_LABELS } from "../../components/shared/productLabels"
import mcpOAuthRequests from "./api"

const CONTEXT_OPTIONS = Object.entries(PRODUCT_LABELS).map(([value, label]) => ({ label, value }))

const EXPIRED_MESSAGE = "This authorization request is invalid or has expired. Start the connection again from your MCP client."

const SECONDS_PER_DAY = 24 * 60 * 60

// A brand-new registration is a signal worth showing next to the destination
const registeredLabel = (createdAt) => {
    if (!createdAt) return null
    const days = Math.floor((Date.now() / 1000 - createdAt) / SECONDS_PER_DAY)
    return days < 1 ? "Today" : `${days} day${days === 1 ? "" : "s"} ago`
}

const contextLabel = (value) => CONTEXT_OPTIONS.find((option) => option.value === value)?.label || value

function DetailRow({ label, value }) {
    return (
        <HorizontalStack align="space-between" blockAlign="center" gap="4" wrap={false}>
            <Text as="span" color="subdued">{label}</Text>
            <Text as="span" fontWeight="semibold" breakWord>{value}</Text>
        </HorizontalStack>
    )
}

function McpOAuthConsent() {
    const [searchParams] = useSearchParams()
    const requestId = searchParams.get("request_id")

    const [info, setInfo] = useState(null)
    const [contextSource, setContextSource] = useState("API")
    const [error, setError] = useState(requestId ? "" : EXPIRED_MESSAGE)
    const [loading, setLoading] = useState(!!requestId)
    const [pendingAction, setPendingAction] = useState(null)
    const [confirmUnverified, setConfirmUnverified] = useState(false)
    const submitting = pendingAction !== null

    // Hide the Intercom launcher on this screen
    useEffect(() => {
        window?.Intercom?.("update", { hide_default_launcher: true })
        return () => window?.Intercom?.("update", { hide_default_launcher: false })
    }, [])

    useEffect(() => {
        if (!requestId) return
        mcpOAuthRequests.fetchAuthorizeRequest(requestId)
            .then((resp) => {
                setInfo(resp)
                setContextSource(resp.contextSource || "API")
            })
            .catch((err) => setError(err?.response?.data?.actionErrors?.[0] || EXPIRED_MESSAGE))
            .finally(() => setLoading(false))
    }, [requestId])

    // Both outcomes return to the MCP client via its redirect URL
    const submit = async (actionName, action) => {
        setPendingAction(actionName)
        try {
            const resp = await action()
            if (!resp?.redirectUrl) {
                throw new Error("Missing redirect URL")
            }
            window.location.href = resp.redirectUrl
        } catch (err) {
            setError(err?.response?.data?.actionErrors?.[0] || EXPIRED_MESSAGE)
            setPendingAction(null)
        }
    }

    const renderContent = () => {
        if (loading) {
            return <Box padding="8"><HorizontalStack align="center"><Spinner size="large" accessibilityLabel="Loading connection request" /></HorizontalStack></Box>
        }
        if (error || !info) {
            return <Banner status="critical" title="Unable to continue">{error || EXPIRED_MESSAGE}</Banner>
        }
        // Only products the user has a role in (re-checked on approve)
        const allowedOptions = CONTEXT_OPTIONS.filter((option) => info.allowedContexts?.includes(option.value))
        return (
            <LegacyCard>
                <LegacyCard.Section>
                    <VerticalStack gap="3" inlineAlign="center">
                        <Avatar customer name={info.clientName} size="large" />
                        <Text as="h2" variant="headingLg" alignment="center">Authorize {info.clientName}</Text>
                        <Text as="p" color="subdued" alignment="center">
                            This app wants to use Akto on your behalf. It will have the same permissions as your role in this account.
                        </Text>
                    </VerticalStack>
                </LegacyCard.Section>
                {info.unverifiedRedirect ? (
                    <LegacyCard.Section>
                        <VerticalStack gap="3">
                            <Banner status="warning" title="Unverified app">
                                Access will be sent to {info.redirectHost}, which Akto does not recognize. Only continue if you trust it.
                            </Banner>
                            <Checkbox label={`I trust ${info.redirectHost}`} checked={confirmUnverified} onChange={setConfirmUnverified} />
                        </VerticalStack>
                    </LegacyCard.Section>
                ) : null}
                <LegacyCard.Section>
                    <VerticalStack gap="3">
                        <DetailRow label="Signed in as" value={info.userLogin} />
                        <Divider />
                        <DetailRow label="Account" value={info.accountName} />
                        <Divider />
                        <DetailRow label="Redirects to" value={info.redirectUri || info.redirectHost} />
                        {registeredLabel(info.clientCreatedAt) ? (
                            <>
                                <Divider />
                                <DetailRow label="App registered" value={registeredLabel(info.clientCreatedAt)} />
                            </>
                        ) : null}
                    </VerticalStack>
                </LegacyCard.Section>
                <LegacyCard.Section>
                    <VerticalStack gap="2">
                        {allowedOptions.length > 1 ? (
                            <DropdownSearch
                                id="mcp-oauth-product"
                                label="Product"
                                placeholder="Select a product"
                                optionsList={allowedOptions}
                                setSelected={setContextSource}
                                preSelected={[contextSource]}
                                value={contextLabel(contextSource)}
                                searchDisable={true}
                            />
                        ) : (
                            <DetailRow label="Product" value={contextLabel(contextSource)} />
                        )}
                        <Text as="p" variant="bodySm" color="subdued">
                            {allowedOptions.length > 1 ? "Which Akto product the app works with, limited to those you have access to. " : ""}
                            To use a different account, switch accounts from the top bar and reload this page.
                        </Text>
                    </VerticalStack>
                </LegacyCard.Section>
                <LegacyCard.Section>
                    <VerticalStack gap="3">
                        <HorizontalStack gap="3" wrap={false}>
                            <Box width="100%">
                                <Button fullWidth disabled={submitting && pendingAction !== "deny"} loading={pendingAction === "deny"} onClick={() => submit("deny", () => mcpOAuthRequests.denyAuthorizeRequest(requestId))}>Deny</Button>
                            </Box>
                            <Box width="100%">
                                <Button fullWidth primary disabled={(submitting && pendingAction !== "allow") || (info.unverifiedRedirect && !confirmUnverified)} loading={pendingAction === "allow"} onClick={() => submit("allow", () => mcpOAuthRequests.approveAuthorizeRequest(requestId, contextSource, confirmUnverified))}>Allow access</Button>
                            </Box>
                        </HorizontalStack>
                        <Text as="p" variant="bodySm" color="subdued" alignment="center">
                            Only continue if you started this connection from {info.clientName}.
                        </Text>
                    </VerticalStack>
                </LegacyCard.Section>
            </LegacyCard>
        )
    }

    return (
        <Page narrowWidth>
            <Box paddingBlockStart="8" paddingBlockEnd="8">{renderContent()}</Box>
        </Page>
    )
}

export default McpOAuthConsent
