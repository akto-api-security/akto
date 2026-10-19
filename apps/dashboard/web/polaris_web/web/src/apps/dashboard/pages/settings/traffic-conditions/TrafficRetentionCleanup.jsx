import { useState } from "react"
import { Banner, Box, Button, HorizontalStack, LegacyCard, Select, Text, TextField, VerticalStack } from "@shopify/polaris"
import func from "@/util/func"
import { usePermissions } from "@/util/permissions"
import observeFunc from "../../observe/transform"
import AllowedAction from "../../../components/shared/AllowedAction"
import trafficFiltersRequest from "./api"
import TrafficRetentionResults from "./TrafficRetentionResults"
import { AGE_OPTIONS, CUSTOM_AGE, MAX_DAYS, MIN_DAYS, resolveDays } from "./trafficRetention"

function isValidPreview(resp) {
    return !!resp && Array.isArray(resp.collections) && typeof resp.complete === "boolean"
}

function TrafficRetentionCleanup() {
    const { canCall } = usePermissions()
    const [ageChoice, setAgeChoice] = useState("180")
    const [customDays, setCustomDays] = useState("")
    const [loading, setLoading] = useState(false)
    const [starting, setStarting] = useState(false)
    const [preview, setPreview] = useState(null)
    const [previewFailed, setPreviewFailed] = useState(false)

    const days = resolveDays(ageChoice, customDays)

    // A preview describes one cutoff; changing the age makes it stale, so it must not stay deletable.
    const changeAge = (value) => { setAgeChoice(value); setPreview(null); setPreviewFailed(false) }
    const changeCustomDays = (value) => { setCustomDays(value); setPreview(null); setPreviewFailed(false) }

    const runPreview = async () => {
        setLoading(true)
        setPreview(null)
        setPreviewFailed(false)
        try {
            const resp = await trafficFiltersRequest.previewTrafficRetentionCleanup(days)
            if (isValidPreview(resp)) {
                setPreview(resp)
            } else {
                setPreviewFailed(true)
            }
        } catch (e) {
            // request.js has already toasted the server's message.
            setPreviewFailed(true)
        } finally {
            setLoading(false)
        }
    }

    const startDelete = async () => {
        setStarting(true)
        try {
            const resp = await trafficFiltersRequest.startTrafficRetentionCleanup(preview.cutoffEpoch)
            if (resp?.started) {
                func.setToast(true, false, "Cleanup started. Progress is written to the logs page (Settings > Logs).")
                setPreview(null)
            } else {
                func.setToast(true, true, "The cleanup did not start. Please try again.")
            }
        } catch (e) {
            // request.js has already toasted the server's message.
        } finally {
            setStarting(false)
        }
    }

    const confirmDelete = () => {
        func.showConfirmationModal(
            `Permanently delete ${observeFunc.formatNumberWithCommas(preview.totalStaleApis)} APIs with no traffic since ${func.prettifyEpoch(preview.cutoffEpoch)}, along with their samples, sensitive-data records and traffic stats. This cannot be undone.`,
            "Delete APIs",
            startDelete
        )
    }

    const canDelete = !!preview && preview.complete && preview.totalStaleApis > 0 && !starting

    return (
        <LegacyCard title="Delete APIs with no recent traffic">
            <LegacyCard.Section>
                <VerticalStack gap="4">
                    <Text variant="bodyMd" color="subdued">
                        Finds APIs whose last observed traffic is older than the chosen age, using the host header of each API.
                        Run a dry run first to see how many APIs would be deleted per collection. Deactivated collections are not included.
                    </Text>
                    <HorizontalStack gap="3" blockAlign="end">
                        <Box minWidth="260px">
                            <Select label="Age" options={AGE_OPTIONS} value={ageChoice} onChange={changeAge} />
                        </Box>
                        {ageChoice === CUSTOM_AGE && (
                            <TextField
                                label="Days"
                                type="number"
                                min={MIN_DAYS}
                                max={MAX_DAYS}
                                value={customDays}
                                onChange={changeCustomDays}
                                autoComplete="off"
                                helpText={`${MIN_DAYS} to ${MAX_DAYS}`}
                            />
                        )}
                        <AllowedAction allowed={canCall("api/previewTrafficRetentionCleanup")}>
                            <Button onClick={runPreview} loading={loading} disabled={days === null}>Dry run</Button>
                        </AllowedAction>
                        <AllowedAction allowed={canCall("api/startTrafficRetentionCleanup")}>
                            <Button destructive onClick={confirmDelete} disabled={!canDelete} loading={starting}>Delete APIs</Button>
                        </AllowedAction>
                    </HorizontalStack>
                    {previewFailed && (
                        <Banner status="critical" title="Dry run unavailable">
                            <Text variant="bodyMd">The dry run could not be completed, so nothing is shown. Nothing was deleted.</Text>
                        </Banner>
                    )}
                    {preview && <TrafficRetentionResults preview={preview} />}
                </VerticalStack>
            </LegacyCard.Section>
        </LegacyCard>
    )
}

export default TrafficRetentionCleanup
