import { useEffect, useState } from "react"
import { Box, IndexFiltersMode, Text } from "@shopify/polaris"
import { CircleCancelMajor, ReplayMinor } from "@shopify/polaris-icons"
import GithubSimpleTable from "@/apps/dashboard/components/tables/GithubSimpleTable"
import { CellType } from "@/apps/dashboard/components/tables/rows/GithubRow"
import api from "./api"
import quickStartApi from "../../quick_start/api"
import func from "@/util/func"
import SpinnerCentered from "@/apps/dashboard/components/progress/SpinnerCentered"
import TooltipText from "@/apps/dashboard/components/shared/TooltipText"
import PageWithMultipleCards from "../../../components/layouts/PageWithMultipleCards"
import TitleWithInfo from "@/apps/dashboard/components/shared/TitleWithInfo"

const headers = [
    {
        title: "Crawl ID",
        text: "Crawl ID",
        value: "crawlIdComp",
        textValue: "crawlId",
        filterKey: "crawlId",
        showFilter: true
    },
    {
        title: "Hostname",
        text: "Hostname",
        value: "hostnameComp",
        textValue: "hostname",
        filterKey: "hostname",
        showFilter: true
    },
    {
        title: "Started By",
        text: "Started By",
        value: "startedBy",
        filterKey: "startedBy",
        showFilter: true
    },
    {
        title: "Module Name",
        text: "Module Name",
        value: "moduleName",
        filterKey: "moduleName",
        showFilter: true
    },
    {
        title: "Start Time",
        text: "Start Time",
        value: "startTime",
        sortActive: true,
        sortKey: "startTimestamp"
    },
    {
        title: "Application Pages",
        text: "Application Pages",
        value: "applicationPagesComp",
        textValue: "applicationPages"
    },
    // TODO: to be added later.
    // {
    //     title: "End Time",
    //     text: "End Time",
    //     value: "endTime",
    //     sortActive: true,
    //     sortKey: "endTimestamp"
    // },
    // {
    //     title: "Duration",
    //     text: "Duration",
    //     value: "duration"
    // },
    {
        title: "URL Template Patterns",
        text: "URL Template Patterns",
        value: "urlTemplatePatternsComp",
        textValue: "urlTemplatePatterns"
    },
    {
        title: "Out of Scope URLs",
        text: "Out of Scope URLs",
        value: "outScopeUrlsComp",
        textValue: "outScopeUrls"
    },
    {
        title: "",
        type: CellType.ACTION
    }
]

const sortOptions = [
    { label: 'Start Time', value: 'startTimestamp asc', directionLabel: 'Recent first', sortKey: 'startTimestamp', columnIndex: 3 },
    { label: 'Start Time', value: 'startTimestamp desc', directionLabel: 'Oldest first', sortKey: 'startTimestamp', columnIndex: 3 },
    { label: 'End Time', value: 'endTimestamp asc', directionLabel: 'Recent first', sortKey: 'endTimestamp', columnIndex: 4 },
    { label: 'End Time', value: 'endTimestamp desc', directionLabel: 'Oldest first', sortKey: 'endTimestamp', columnIndex: 4 },
]

const resourceName = {
    singular: 'DAST scan',
    plural: 'DAST scans',
}

function getActions(item, fetchAllDastScans) {
    const isStopDisabled = item.status === "STOPPED" || item.status === "STOP_REQUESTED"
    const run = item.runData
    const canDuplicate = run && !(run.cookies && !run.testRoleHexId)
    return [
        {
            title: "Actions",
            items: [
                {
                    content: "Duplicate scan",
                    icon: ReplayMinor,
                    disabled: !canDuplicate,
                    onAction: async () => {
                        if (!run) return
                        try {
                            await quickStartApi.initiateCrawler(
                                run.hostname,
                                run.username ?? '',
                                run.password ?? '',
                                run.apiKey ?? '',
                                run.dashboardUrl ?? window.location.origin,
                                run.testRoleHexId ?? '',
                                run.outScopeUrls ?? '',
                                run.crawlingTime ?? 600,
                                (run.moduleName && run.moduleName !== "Internal DAST (Akto)") ? run.moduleName : '',
                                run.customHeaders ?? {},
                                run.runTestAfterCrawling ?? false,
                                run.selectedMiniTestingService ?? '',
                                run.urlTemplatePatterns ?? '',
                                run.applicationPages ?? ''
                            )
                            func.setToast(true, false, "Duplicate scan started")
                            fetchAllDastScans()
                        } catch {
                            func.setToast(true, true, "Failed to duplicate scan")
                        }
                    }
                },
                {
                    content: "Stop scan",
                    icon: CircleCancelMajor,
                    destructive: true,
                    disabled: isStopDisabled,
                    onAction: async () => {
                        try {
                            await api.stopCrawler(item.crawlId)
                            func.setToast(true, false, "Crawler stop requested")
                            fetchAllDastScans()
                        } catch {
                            func.setToast(true, true, "Failed to stop crawler")
                        }
                    }
                }
            ]
        }
    ]
}

function DastProgress() {
    const [loading, setLoading] = useState(false)
    const [data, setData] = useState([])

    const fetchAllDastScans = async () => {
        try {
            setLoading(true)
            const resp = await api.fetchAllDastScans()

            const crawlerRuns = resp || []
            const formattedData = crawlerRuns.map((run) => {
                const duration = run.endTimestamp > 0 && run.startTimestamp > 0
                    ? func.prettifyEpochDuration(run.endTimestamp - run.startTimestamp)
                    : run.endTimestamp === 0 ? "In Progress" : "-"

                return {
                    id: run.crawlId,
                    crawlId: run.crawlId,
                    crawlIdComp: (
                        <Box maxWidth="30vw">
                            <TooltipText
                                tooltip={run.crawlId}
                                text={run.crawlId}
                                textProps={{ fontWeight: 'medium' }}
                            />
                        </Box>
                    ),
                    hostname: run.hostname || "-",
                    hostnameComp: (
                        <Box maxWidth="30vw">
                            <Text truncate>{run.hostname || "-"}</Text>
                        </Box>
                    ),
                    startedBy: run.startedBy || "-",
                    moduleName: run.moduleName || "Internal DAST (Akto)",
                    applicationPages: run.applicationPages || "-",
                    applicationPagesComp: (
                        <Box maxWidth="20vw">
                            <Text truncate>{run.applicationPages || "-"}</Text>
                        </Box>
                    ),
                    startTime: func.prettifyEpoch(run.startTimestamp),
                    startTimestamp: run.startTimestamp,
                    endTime: run.endTimestamp > 0 ? func.prettifyEpoch(run.endTimestamp) : "In Progress",
                    endTimestamp: run.endTimestamp,
                    duration: duration,
                    urlTemplatePatterns: run.urlTemplatePatterns || "-",
                    urlTemplatePatternsComp: (
                        <Box maxWidth="30vw">
                            <TooltipText
                                tooltip={run.urlTemplatePatterns || "-"}
                                text={run.urlTemplatePatterns || "-"}
                                textProps={{ truncate: true }}
                            />
                        </Box>
                    ),
                    outScopeUrls: run.outScopeUrls || "-",
                    outScopeUrlsComp: (
                        <Box maxWidth="30vw">
                            <TooltipText
                                tooltip={run.outScopeUrls || "-"}
                                text={run.outScopeUrls || "-"}
                                textProps={{ truncate: true }}
                            />
                        </Box>
                    ),
                    nextUrl: `/dashboard/observe/dast-progress/${run.crawlId}`,
                    status: run.status || null,
                    runData: run
                }
            })

            setData(formattedData)
            setLoading(false)
        } catch (error) {
            func.setToast(true, true, "Failed to fetch DAST scans")
            setLoading(false)
        }
    }

    useEffect(() => {
        fetchAllDastScans()
    }, [])

    if (loading) {
        return <SpinnerCentered />
    }

    return (
        <PageWithMultipleCards
            title={
                <TitleWithInfo
                    tooltipContent={"DAST scans history"}
                    titleText={"DAST scans"}
                    docsUrl={"https://docs.akto.io/dast/akto-dast"}
                />
            }
            isFirstPage={true}
            components={[
                <GithubSimpleTable
                    data={data}
                    sortOptions={sortOptions}
                    resourceName={resourceName}
                    headers={headers}
                    headings={headers}
                    loading={loading}
                    mode={IndexFiltersMode.Default}
                    useNewRow={true}
                    condensedHeight={true}
                    disambiguateLabel={(_, value) => func.convertToDisambiguateLabelObj(value, null, 2)}
                    getActions={(item) => getActions(item, fetchAllDastScans)}
                    hasRowActions={true}
                    lastColumnSticky={true}
                    preventRowClickOnActions={true}
                />
            ]}
        />
    )
}

export default DastProgress;