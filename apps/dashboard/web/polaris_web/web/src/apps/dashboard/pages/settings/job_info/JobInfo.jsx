import { Badge, Box, Divider, HorizontalStack, LegacyCard, Modal, Text, TextField, Tooltip, VerticalStack } from "@shopify/polaris"
import { DeleteMajor } from "@shopify/polaris-icons"
import { useEffect, useState } from "react"
import settingRequests from "../api"
import func from "@/util/func"
import EmptyCard from "../../dashboard/new_components/EmptyCard"
import GithubSimpleTable from "../../../components/tables/GithubSimpleTable"
import { CellType } from "../../../components/tables/rows/GithubRow"
import ConfirmationModal from "../../../components/shared/ConfirmationModal"

const STATUS_BADGE_CLASS = {
    COMPLETED: "SUCCESS",
    FAILED: "CRITICAL",
    RUNNING: "MEDIUM",
    SCHEDULED: "LOW",
    STOPPED: "HIGH",
}

const resourceName = {
    singular: "job",
    plural: "jobs",
}

const headings = [
    { text: "Job Type", value: "jobTypeComp", title: "Job Type" },
    { text: "Sub Type", value: "subType", title: "Sub Type" },
    { text: "Status", value: "statusComp", title: "Status" },
    { text: "Scheduled At", value: "scheduledAtStr", title: "Scheduled At", type: CellType.TEXT },
    { text: "Started At", value: "startedAtStr", title: "Started At", type: CellType.TEXT },
    { text: "Finished At", value: "finishedAtStr", title: "Finished At", type: CellType.TEXT },
    { text: "Error", value: "errorComp", title: "Error" },
    { title: "", type: CellType.ACTION },
]

const KeyValueRow = ({ label, value }) => (
    <HorizontalStack align="space-between" blockAlign="start" wrap={false} gap="4">
        <Box minWidth="200px">
            <Text variant="bodySm" tone="subdued" fontWeight="medium">{label}</Text>
        </Box>
        <Box maxWidth="340px">
            <Text variant="bodySm" breakWord>{value || "-"}</Text>
        </Box>
    </HorizontalStack>
)

const ErrorBlock = ({ error }) => {
    const lines = error.split(/\n/).map(l => l.trim()).filter(Boolean)
    return (
        <VerticalStack gap="2">
            <Text variant="bodySm" tone="subdued" fontWeight="medium">Error</Text>
            <Box background="bg-subdued" padding="3" borderRadius="2">
                <VerticalStack gap="1">
                    {lines.map((line, i) => (
                        <Text key={i} variant="bodySm" tone="critical" breakWord>{line}</Text>
                    ))}
                </VerticalStack>
            </Box>
        </VerticalStack>
    )
}

const JobInfo = () => {
    const [jobs, setJobs] = useState([])
    const [filterText, setFilterText] = useState("")
    const [selectedJob, setSelectedJob] = useState(null)

    const fetchJobs = async () => {
        const response = await settingRequests.fetchAccountJobs()
        setJobs(response.accountJobs || [])
    }

    useEffect(() => {
        fetchJobs()
    }, [])

    const handleDelete = (job) => {
        const deleteMessage = `Are you sure you want to delete this job "${job.jobType} / ${job.subType}"? This will remove it from the jobs list and cannot be undone.`
        func.showConfirmationModal(
            deleteMessage,
            "Delete",
            async () => {
                await settingRequests.deleteAccountJob(job.id)
                fetchJobs()
            }
        )
    }

    const getActions = (row) => [
        {
            items: [{
                content: "Delete",
                icon: DeleteMajor,
                destructive: true,
                onAction: () => handleDelete(row),
            }]
        }
    ]

    const tableData = jobs.map(job => {
        const status = job.jobStatus || "-"
        const badgeClass = STATUS_BADGE_CLASS[status] || "LOW"
        return {
            ...job,
            jobTypeComp: (
                <Text variant="bodySm" fontWeight="medium">{job.jobType || "-"}</Text>
            ),
            statusComp: (
                <div className={`badge-wrapper-${badgeClass}`}><Badge>{status}</Badge></div>
            ),
            scheduledAtStr: job.scheduledAt ? func.epochToDateTime(job.scheduledAt) : "-",
            startedAtStr: job.startedAt ? func.epochToDateTime(job.startedAt) : "-",
            finishedAtStr: job.finishedAt ? func.epochToDateTime(job.finishedAt) : "-",
            errorComp: job.error ? (
                <Tooltip content={job.error} preferredPosition="above">
                    <Box maxWidth="180px">
                        <Text variant="bodySm" tone="critical" truncate>{job.error}</Text>
                    </Box>
                </Tooltip>
            ) : "-",
        }
    })

    const filteredData = tableData.filter(job => {
        const q = filterText.toLowerCase()
        return (
            (job.jobType || "").toLowerCase().includes(q) ||
            (job.subType || "").toLowerCase().includes(q) ||
            (job.jobStatus || "").toLowerCase().includes(q)
        )
    })

    const status = selectedJob?.jobStatus || "-"
    const badgeClass = STATUS_BADGE_CLASS[status] || "LOW"
    const configEntries = selectedJob?.config ? Object.entries(selectedJob.config) : []

    return (
        <LegacyCard sectioned title="Account Jobs">
            <LegacyCard.Section>
                <TextField
                    label=""
                    placeholder="Filter by job type, sub type, or status..."
                    value={filterText}
                    onChange={setFilterText}
                    clearButton
                    onClearButtonClick={() => setFilterText("")}
                    autoComplete="off"
                />
            </LegacyCard.Section>

            {filteredData.length > 0 ? (
                <GithubSimpleTable
                    resourceName={resourceName}
                    headers={headings}
                    headings={headings}
                    data={filteredData}
                    rowClickable={true}
                    onRowClick={(row) => setSelectedJob(row)}
                    hideQueryField={true}
                    useNewRow={true}
                    hasRowActions={true}
                    getActions={getActions}
                    preventRowClickOnActions={true}
                />
            ) : (
                <EmptyCard
                    title="No jobs found"
                    subTitleComponent={
                        <Text variant="bodyMd" alignment="center" tone="subdued">
                            {filterText ? "No jobs match your filter." : "No account jobs found."}
                        </Text>
                    }
                />
            )}

            <ConfirmationModal
                modalContent=""
                primaryActionContent="Delete"
                primaryAction={() => {}}
            />

            {selectedJob && (
                <Modal
                    open={!!selectedJob}
                    onClose={() => setSelectedJob(null)}
                    title={`${selectedJob.jobType} / ${selectedJob.subType}`}
                >
                    <Modal.Section>
                        <VerticalStack gap="3">
                            <Text variant="headingSm">Job Details</Text>
                            <KeyValueRow label="Job Type" value={selectedJob.jobType} />
                            <KeyValueRow label="Sub Type" value={selectedJob.subType} />
                            <KeyValueRow label="Schedule Type" value={selectedJob.scheduleType} />
                            <KeyValueRow label="Recurring Interval" value={selectedJob.recurringIntervalSeconds ? `${selectedJob.recurringIntervalSeconds}s` : null} />
                            <HorizontalStack align="space-between" blockAlign="center" wrap={false} gap="4">
                                <Box minWidth="200px">
                                    <Text variant="bodySm" tone="subdued" fontWeight="medium">Status</Text>
                                </Box>
                                <Box maxWidth="340px">
                                    <div className={`badge-wrapper-${badgeClass}`}><Badge>{status}</Badge></div>
                                </Box>
                            </HorizontalStack>
                            <KeyValueRow label="Scheduled At" value={selectedJob.scheduledAt ? func.epochToDateTime(selectedJob.scheduledAt) : null} />
                            <KeyValueRow label="Started At" value={selectedJob.startedAt ? func.epochToDateTime(selectedJob.startedAt) : null} />
                            <KeyValueRow label="Finished At" value={selectedJob.finishedAt ? func.epochToDateTime(selectedJob.finishedAt) : null} />
                            <KeyValueRow label="Created At" value={selectedJob.createdAt ? func.epochToDateTime(selectedJob.createdAt) : null} />
                            <KeyValueRow label="Last Updated At" value={selectedJob.lastUpdatedAt ? func.epochToDateTime(selectedJob.lastUpdatedAt) : null} />
                            {selectedJob.error && <ErrorBlock error={selectedJob.error} />}
                        </VerticalStack>
                    </Modal.Section>

                    {configEntries.length > 0 && (
                        <Modal.Section>
                            <VerticalStack gap="3">
                                <Text variant="headingSm">Configuration</Text>
                                <Divider />
                                {configEntries.map(([key, value]) => (
                                    <KeyValueRow key={key} label={key} value={String(value)} />
                                ))}
                            </VerticalStack>
                        </Modal.Section>
                    )}
                </Modal>
            )}
        </LegacyCard>
    )
}

export default JobInfo
