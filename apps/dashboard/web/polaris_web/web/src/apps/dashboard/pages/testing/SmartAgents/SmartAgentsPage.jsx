import { useEffect, useState } from "react"
import { Badge, Box, Button, HorizontalStack, IndexFiltersMode, Text, VerticalStack } from "@shopify/polaris"
import { useNavigate } from "react-router-dom"
import PageWithMultipleCards from "../../../components/layouts/PageWithMultipleCards"
import GithubSimpleTable from "../../../components/tables/GithubSimpleTable"
import EmptyScreensLayout from "../../../components/banners/EmptyScreensLayout"
import TitleWithInfo from "../../../components/shared/TitleWithInfo"
import TooltipText from "../../../components/shared/TooltipText"
import AllowedAction from "../../../components/shared/AllowedAction"
import { CellType } from "../../../components/tables/rows/GithubRow"
import useTable from "../../../components/tables/TableContext"
import { usePermissions } from "@/util/permissions"
import func from "@/util/func"
import api from "../api"
import { skillLabel } from "./smartAgentUtils"

const headers = [
    { title: "Smart agent", text: "Name", value: "nameComp" },
    { title: "Skills", text: "Skills", value: "skillsComp" },
    { title: "Knowledge base", text: "Knowledge base", value: "knowledgeComp" },
    { title: "Last updated", text: "Updated at", value: "lastUpdatedAt", sortKey: "lastUpdatedTs", sortActive: true },
    { title: "Updated by", text: "Updated by", value: "lastUpdatedBy", showFilter: true, filterKey: "lastUpdatedBy" },
    { title: "", type: CellType.ACTION },
]

const sortOptions = [
    { label: "Updated at", value: "updated asc", directionLabel: "Newest", sortKey: "lastUpdatedTs", columnIndex: 4 },
    { label: "Updated at", value: "updated desc", directionLabel: "Oldest", sortKey: "lastUpdatedTs", columnIndex: 4 },
]

const resourceName = { singular: "smart agent", plural: "smart agents" }

const plural = (count, word) => `${count} ${word}${count > 1 ? "s" : ""}`

function knowledgeSummary(profile) {
    const parts = []
    if (profile.instructions) parts.push("instructions")
    if ((profile.knowledgeFiles || []).length) parts.push(plural(profile.knowledgeFiles.length, "file"))
    if ((profile.knowledgeLinks || []).length) parts.push(plural(profile.knowledgeLinks.length, "link"))
    return parts.length ? parts.join(", ") : "-"
}

function SmartAgentsPage() {
    const navigate = useNavigate()
    const { canCall } = usePermissions()
    const { tabsInfo } = useTable()
    const [loading, setLoading] = useState(true)
    const [profiles, setProfiles] = useState([])
    const [selectedTab, setSelectedTab] = useState("all")
    const [selected, setSelected] = useState(0)

    async function fetchData() {
        setLoading(true)
        try {
            const res = await api.fetchSmartAgentProfiles()
            setProfiles(res?.smartAgentProfiles || [])
        } finally {
            setLoading(false)
        }
    }

    useEffect(() => { fetchData() }, [])

    const rows = profiles.map(profile => {
        const skills = [...(profile.skills || []).map(skillLabel), ...(profile.customSkills || []).map(s => s.name)]
        return {
            ...profile,
            id: profile.hexId,
            nameComp: (
                <VerticalStack gap="1">
                    <Box maxWidth="30vw"><TooltipText tooltip={profile.name} text={profile.name} textProps={{ fontWeight: "medium" }} /></Box>
                    {profile.description ? <Box maxWidth="30vw"><TooltipText tooltip={profile.description} text={profile.description} textProps={{ color: "subdued" }} /></Box> : null}
                </VerticalStack>
            ),
            skillsComp: skills.length === 0
                ? <Text color="subdued">-</Text>
                : <HorizontalStack gap="1">{skills.slice(0, 3).map(s => <Badge key={s} size="small">{s}</Badge>)}{skills.length > 3 ? <Badge size="small">{`+${skills.length - 3}`}</Badge> : null}</HorizontalStack>,
            knowledgeComp: <Text>{knowledgeSummary(profile)}</Text>,
            lastUpdatedAt: func.prettifyEpoch(profile.lastUpdatedTs),
        }
    })

    const definedTableTabs = ["All"]
    const tableTabs = func.getTableTabsContent(definedTableTabs, func.getTabsCount(definedTableTabs, { all: rows }), setSelectedTab, selectedTab, tabsInfo)

    const getActions = (item) => [{
        items: [
            { content: "Edit", onAction: () => navigate(`details?id=${item.hexId}`) },
            {
                content: "Delete",
                destructive: true,
                requires: "api/deleteSmartAgentProfile",
                onAction: async () => {
                    await api.deleteSmartAgentProfile(item.hexId)
                    func.setToast(true, false, `Smart agent ${item.name} deleted`)
                    fetchData()
                },
            },
        ],
    }]

    const createButton = (
        <AllowedAction allowed={canCall("api/saveSmartAgentProfile")}>
            <Button primary onClick={() => navigate("details")}>Create smart agent</Button>
        </AllowedAction>
    )

    const components = !loading && profiles.length === 0 ? [
        <EmptyScreensLayout key="empty"
            iconSrc={"/public/file_check.svg"}
            headingText={"Build your first smart agent"}
            description={"A smart agent is smart testing with your own skills and knowledge base. Choose it in Run test when you enable Smart Automated Testing."}
            buttonText={"Create smart agent"}
            redirectUrl={"/dashboard/testing/smart-agents/details"}
        />,
    ] : [
        <GithubSimpleTable
            key="table"
            data={rows}
            tableTabs={tableTabs}
            selected={selected}
            onSelect={setSelected}
            headers={headers}
            headings={headers}
            resourceName={resourceName}
            loading={loading}
            onRowClick={(item) => navigate(`details?id=${item.hexId}`)}
            getActions={getActions}
            hasRowActions={true}
            preventRowClickOnActions={true}
            useNewRow={true}
            sortOptions={sortOptions}
            mode={IndexFiltersMode.Default}
            disambiguateLabel={(key, value) => func.convertToDisambiguateLabelObj(value, null, 2)}
        />,
    ]

    return (
        <PageWithMultipleCards
            title={<TitleWithInfo
                titleText={"Smart agents"}
                tooltipContent={"Smart testing with your own skills and knowledge base. Choose a smart agent in Run test when you enable Smart Automated Testing."}
            />}
            primaryAction={createButton}
            isFirstPage={true}
            components={components}
        />
    )
}

export default SmartAgentsPage
