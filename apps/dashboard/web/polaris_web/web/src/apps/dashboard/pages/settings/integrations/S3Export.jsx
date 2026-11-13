import { Banner, Box, Button, Checkbox, ChoiceList, DataTable, LegacyCard, Select, Text, TextField, Tooltip, VerticalStack } from "@shopify/polaris"
import { ClipboardMinor } from "@shopify/polaris-icons"
import { useEffect, useRef, useState } from "react"
import IntegrationsLayout from "./IntegrationsLayout"
import settingRequests from "../api"
import func from "@/util/func"
import { usePermissions, whenAllowed } from "@/util/permissions"

const AWS_REGIONS = [
    "us-east-1", "us-east-2", "us-west-1", "us-west-2",
    "af-south-1", "ap-east-1", "ap-south-1", "ap-south-2",
    "ap-southeast-1", "ap-southeast-2", "ap-southeast-3", "ap-southeast-4",
    "ap-northeast-1", "ap-northeast-2", "ap-northeast-3",
    "ca-central-1", "eu-central-1", "eu-central-2",
    "eu-west-1", "eu-west-2", "eu-west-3", "eu-north-1", "eu-south-1", "eu-south-2",
    "il-central-1", "me-south-1", "me-central-1", "sa-east-1"
]

const REGION_OPTIONS = [{ label: "Select a region", value: "" }, ...AWS_REGIONS.map(region => ({ label: region, value: region }))]

const DATASET_LABELS = {
    ATLAS_TRACES: "Atlas traces (LLM requests, responses, tokens, model, user)",
    ATLAS_GUARDRAIL_EVENTS: "Atlas guardrail activity"
}

const INTERVAL_OPTIONS = [
    { label: "Every hour", value: "1" },
    { label: "Every 6 hours", value: "6" },
    { label: "Every 24 hours", value: "24" }
]

const BACKFILL_OPTIONS = [
    { label: "None", value: "0" },
    { label: "Last 24 hours", value: "24" },
    { label: "Last 7 days", value: "168" }
]

const BUCKET_REGEX = /^[a-z0-9][a-z0-9.-]{1,61}[a-z0-9]$/
const ROLE_ARN_REGEX = /^arn:aws[a-z-]*:iam::\d{12}:role\/.+$/
const KMS_ARN_REGEX = /^arn:aws[a-z-]*:kms:[a-z0-9-]+:\d{12}:(key|alias)\/.+$/

const EMPTY_FORM = {
    bucket: "",
    region: "",
    prefix: "",
    roleArn: "",
    kmsKeyArn: "",
    datasets: [],
    includePayloads: true,
    intervalHours: "1",
    initialBackfillHours: "24"
}

const datasetLabel = (name) => DATASET_LABELS[name] || name.split("_").map(word => word.charAt(0) + word.slice(1).toLowerCase()).join(" ")

const shortDatasetLabel = (name) => datasetLabel(name).replace(/ \(.*\)$/, "")

const normalizePrefix = (prefix) => (prefix || "").trim().replace(/^\/+|\/+$/g, "")

const toPolicyJson = (policy) => JSON.stringify(policy, null, 2)

const buildTrustPolicy = (aktoRoleArn, externalId) => toPolicyJson({
    Version: "2012-10-17",
    Statement: [{
        Effect: "Allow",
        Principal: { AWS: aktoRoleArn },
        Action: "sts:AssumeRole",
        Condition: { StringEquals: { "sts:ExternalId": externalId } }
    }]
})

const buildPermissionsPolicy = (bucket, prefix, kmsKeyArn) => {
    const normalizedPrefix = normalizePrefix(prefix)
    const objectsArn = `arn:aws:s3:::${bucket.trim() || "<bucket>"}/${normalizedPrefix ? normalizedPrefix + "/" : ""}akto/*`
    return toPolicyJson({
        Version: "2012-10-17",
        Statement: [
            { Effect: "Allow", Action: "s3:PutObject", Resource: objectsArn },
            ...(kmsKeyArn.trim() ? [{ Effect: "Allow", Action: "kms:GenerateDataKey", Resource: kmsKeyArn.trim() }] : [])
        ]
    })
}

// Inline errors only for values that are filled but malformed
const getConnectionErrors = (form) => ({
    bucket: form.bucket.trim() && !BUCKET_REGEX.test(form.bucket.trim()) ? "Enter a valid S3 bucket name (3-63 lowercase letters, numbers, dots or hyphens)" : "",
    roleArn: form.roleArn.trim() && !ROLE_ARN_REGEX.test(form.roleArn.trim()) ? "Enter a valid IAM role ARN, e.g. arn:aws:iam::123456789012:role/akto-s3-export" : "",
    kmsKeyArn: form.kmsKeyArn.trim() && !KMS_ARN_REGEX.test(form.kmsKeyArn.trim()) ? "Enter a valid KMS key or alias ARN" : ""
})

const hasConnectionInputs = (form) => !!(form.bucket.trim() && form.region && form.roleArn.trim())

function CopyField({ label, value, helpText, multiline }) {
    const ref = useRef(null)

    return (
        <TextField
            label={label}
            value={value}
            helpText={helpText}
            readOnly
            monospaced
            multiline={multiline}
            autoComplete="off"
            connectedRight={
                <Tooltip dismissOnMouseOut content="Copy">
                    <Box>
                        <div ref={ref} />
                        <Button icon={ClipboardMinor} onClick={() => func.copyToClipboard(value, ref, `${label} copied.`)} />
                    </Box>
                </Tooltip>
            }
        />
    )
}

function S3Export() {

    const { canCall } = usePermissions()
    const [form, setForm] = useState(EMPTY_FORM)
    const [externalId, setExternalId] = useState("")
    const [aktoRoleArn, setAktoRoleArn] = useState("")
    const [availableDatasets, setAvailableDatasets] = useState([])
    const [jobStatus, setJobStatus] = useState(null)
    const [enabled, setEnabled] = useState(false)
    const [isLoading, setIsLoading] = useState(true)
    const [isSaving, setIsSaving] = useState(false)
    const [isTesting, setIsTesting] = useState(false)

    const updateField = (field) => (value) => setForm(prev => ({ ...prev, [field]: value }))

    async function fetchS3ExportIntegration() {
        try {
            const response = await settingRequests.fetchS3ExportIntegration()
            const integration = response?.s3ExportIntegration || {}
            const datasets = response?.availableDatasets || []
            const isEnabled = !!integration.enabled

            setAvailableDatasets(datasets)
            setAktoRoleArn(response?.aktoRoleArn || "")
            setExternalId(integration.externalId || "")
            setJobStatus(response?.jobStatus || null)
            setEnabled(isEnabled)
            setForm({
                bucket: integration.bucket || "",
                region: integration.region || "",
                prefix: integration.prefix || "",
                roleArn: integration.roleArn || "",
                kmsKeyArn: integration.kmsKeyArn || "",
                datasets: isEnabled ? (integration.datasets || []) : datasets,
                includePayloads: isEnabled ? !!integration.includePayloads : true,
                intervalHours: String(integration.intervalHours || 1),
                initialBackfillHours: EMPTY_FORM.initialBackfillHours
            })
        } finally {
            setIsLoading(false)
        }
    }

    useEffect(() => {
        fetchS3ExportIntegration()
    }, [])

    const connectionPayload = () => ({
        bucket: form.bucket.trim(),
        region: form.region,
        prefix: normalizePrefix(form.prefix),
        roleArn: form.roleArn.trim(),
        kmsKeyArn: form.kmsKeyArn.trim()
    })

    async function testConnection() {
        setIsTesting(true)
        try {
            await settingRequests.testS3ExportIntegration(connectionPayload())
            func.setToast(true, false, "Connection successful. Akto can write to your bucket.")
        } catch (error) {
            // request interceptor already shows the server error
        } finally {
            setIsTesting(false)
        }
    }

    async function saveS3ExportIntegration() {
        setIsSaving(true)
        try {
            await settingRequests.saveS3ExportIntegration({
                ...connectionPayload(),
                datasets: form.datasets,
                includePayloads: form.includePayloads,
                intervalHours: Number(form.intervalHours),
                initialBackfillHours: Number(form.initialBackfillHours)
            })
            func.setToast(true, false, "Successfully saved S3 export")
            await fetchS3ExportIntegration()
        } catch (error) {
            // request interceptor already shows the server error
        } finally {
            setIsSaving(false)
        }
    }

    async function removeS3ExportIntegration() {
        setIsSaving(true)
        try {
            await settingRequests.removeS3ExportIntegration()
            func.setToast(true, false, "Successfully removed S3 export")
            await fetchS3ExportIntegration()
        } catch (error) {
            // request interceptor already shows the server error
        } finally {
            setIsSaving(false)
        }
    }

    const errors = getConnectionErrors(form)
    const hasErrors = Object.values(errors).some(Boolean)
    const isConfigured = !!aktoRoleArn
    const isBusy = isLoading || isSaving || isTesting
    const isTestDisabled = isBusy || !isConfigured || !hasConnectionInputs(form) || hasErrors
    const isSaveDisabled = isTestDisabled || form.datasets.length === 0

    const cardContent = (
        <VerticalStack gap="2">
            <Text variant="bodyMd">
                Akto periodically exports selected datasets to an Amazon S3 bucket in your own AWS account, as gzipped NDJSON files.
                Akto writes by assuming an IAM role in your account, protected by an Akto-generated external ID.
                The data stays in your account and you control its retention.
            </Text>
            <Text variant="bodyMd" color="subdued">
                Layout: s3://&lt;bucket&gt;/&lt;prefix&gt;/akto/&lt;dataset&gt;/dt=YYYY-MM-DD/hour=HH/part-NNNN.ndjson.gz, with a _SUCCESS marker written once an hour is complete. The layout works with Athena and Glue partitions.
            </Text>
        </VerticalStack>
    )

    const roleSetupCard = (
        <LegacyCard sectioned title="Step 1: Create an IAM role in your AWS account">
            <VerticalStack gap="4">
                {!isLoading && !isConfigured &&
                    <Banner status="warning">
                        S3 export isn't configured for this deployment yet. Contact Akto support to enable it.
                    </Banner>
                }
                <Text variant="bodyMd" color="subdued">
                    Create a role with the trust policy and permissions policy below. Both update as you fill in the form.
                </Text>
                <CopyField label="Akto AWS principal" value={aktoRoleArn} helpText="The principal allowed to assume your role" />
                <CopyField label="External ID" value={externalId} helpText="Required in the trust policy condition" />
                <CopyField label="Trust policy" value={buildTrustPolicy(aktoRoleArn, externalId)} multiline={12} />
                <CopyField label="Permissions policy" value={buildPermissionsPolicy(form.bucket, form.prefix, form.kmsKeyArn)} multiline={form.kmsKeyArn.trim() ? 14 : 8} />
            </VerticalStack>
        </LegacyCard>
    )

    const formCard = (
        <LegacyCard
            title="Step 2: Configure the export"
            primaryFooterAction={{
                content: isSaving ? "Saving..." : "Save",
                onAction: saveS3ExportIntegration,
                disabled: isSaveDisabled,
                loading: isSaving,
                ...whenAllowed(canCall("api/saveS3ExportIntegration"))
            }}
            secondaryFooterActions={[
                {
                    content: isTesting ? "Testing..." : "Test connection",
                    onAction: testConnection,
                    disabled: isTestDisabled,
                    loading: isTesting,
                    ...whenAllowed(canCall("api/testS3ExportIntegration"))
                },
                ...(enabled ? [{
                    content: "Remove",
                    destructive: true,
                    onAction: () => func.showConfirmationModal(
                        "Remove the S3 export? Exports stop immediately. Files already written to your bucket are not deleted.",
                        "Remove",
                        removeS3ExportIntegration
                    ),
                    disabled: isBusy,
                    ...whenAllowed(canCall("api/removeS3ExportIntegration"))
                }] : [])
            ]}
        >
            <LegacyCard.Section>
                <VerticalStack gap="4">
                    <TextField
                        label="Bucket name"
                        value={form.bucket}
                        onChange={updateField("bucket")}
                        placeholder="my-company-akto-export"
                        error={errors.bucket}
                        requiredIndicator
                        autoComplete="off"
                    />
                    <Select
                        label="Region"
                        options={REGION_OPTIONS}
                        value={form.region}
                        onChange={updateField("region")}
                        helpText="Region of the bucket"
                        requiredIndicator
                    />
                    <TextField
                        label="Prefix"
                        value={form.prefix}
                        onChange={updateField("prefix")}
                        placeholder="security-data"
                        helpText="Optional folder inside the bucket. Akto writes under <prefix>/akto/"
                        autoComplete="off"
                    />
                    <TextField
                        label="Role ARN"
                        value={form.roleArn}
                        onChange={updateField("roleArn")}
                        placeholder="arn:aws:iam::123456789012:role/akto-s3-export"
                        helpText="ARN of the IAM role created in step 1"
                        error={errors.roleArn}
                        requiredIndicator
                        autoComplete="off"
                    />
                    <TextField
                        label="KMS key ARN"
                        value={form.kmsKeyArn}
                        onChange={updateField("kmsKeyArn")}
                        placeholder="arn:aws:kms:us-east-1:123456789012:key/..."
                        helpText="Optional. Set only if the bucket uses SSE-KMS with a customer-managed key"
                        error={errors.kmsKeyArn}
                        autoComplete="off"
                    />
                    <ChoiceList
                        allowMultiple
                        title="Datasets"
                        choices={availableDatasets.map(name => ({ label: datasetLabel(name), value: name }))}
                        selected={form.datasets}
                        onChange={updateField("datasets")}
                    />
                    <Checkbox
                        label="Include request/response payloads"
                        checked={form.includePayloads}
                        onChange={updateField("includePayloads")}
                        helpText="Payloads contain prompt and response content, which may include sensitive data"
                    />
                    <Select
                        label="Export frequency"
                        options={INTERVAL_OPTIONS}
                        value={form.intervalHours}
                        onChange={updateField("intervalHours")}
                    />
                    {!enabled &&
                        <Select
                            label="Initial backfill"
                            options={BACKFILL_OPTIONS}
                            value={form.initialBackfillHours}
                            onChange={updateField("initialBackfillHours")}
                            helpText="How far back to export on the first run"
                        />
                    }
                </VerticalStack>
            </LegacyCard.Section>
        </LegacyCard>
    )

    const formatSeconds = (seconds) => seconds ? func.epochToDateTime(seconds) : "Never"

    const statusRows = Object.keys({ ...jobStatus?.lastRecordCounts, ...jobStatus?.exportedUntilMs }).map(dataset => [
        shortDatasetLabel(dataset),
        jobStatus?.exportedUntilMs?.[dataset] ? func.epochToDateTime(jobStatus.exportedUntilMs[dataset] / 1000) : "-",
        String(jobStatus?.lastRecordCounts?.[dataset] ?? "-")
    ])

    const statusCard = enabled ? (
        <LegacyCard title="Export status" sectioned>
            <VerticalStack gap="4">
                {jobStatus?.lastError &&
                    <Banner status="critical" title="Last export failed">
                        <p>{jobStatus.lastError}</p>
                    </Banner>
                }
                <VerticalStack gap="1">
                    <Text variant="bodyMd">Last successful export: {formatSeconds(jobStatus?.lastSuccessAt)}</Text>
                    <Text variant="bodyMd">Last run: {formatSeconds(jobStatus?.lastRunAt)}</Text>
                </VerticalStack>
                {statusRows.length > 0 &&
                    <DataTable
                        columnContentTypes={["text", "text", "numeric"]}
                        headings={["Dataset", "Exported until", "Records in last run"]}
                        rows={statusRows}
                    />
                }
            </VerticalStack>
        </LegacyCard>
    ) : null

    return (
        <IntegrationsLayout
            title="Amazon S3 export"
            cardContent={cardContent}
            component={<VerticalStack gap="4">{roleSetupCard}{formCard}</VerticalStack>}
            secondaryComponent={statusCard}
            readOnly={!canCall("api/saveS3ExportIntegration")}
        />
    )
}

export default S3Export
