import { useEffect, useState } from "react"
import { ActionList, Badge, Banner, Box, Button, IndexFiltersMode, Popover, Text } from "@shopify/polaris"
import PageWithMultipleCards from "../../../components/layouts/PageWithMultipleCards"
import GithubSimpleTable from "../../../components/tables/GithubSimpleTable"
import TitleWithInfo from "../../../components/shared/TitleWithInfo"
import TooltipText from "../../../components/shared/TooltipText"
import AllowedAction from "../../../components/shared/AllowedAction"
import useTable from "../../../components/tables/TableContext"
import { CellType } from "../../../components/tables/rows/GithubRow"
import { usePermissions } from "@/util/permissions"
import func from "@/util/func"
import api from "../api"
import ScriptEditor from "./ScriptEditor"
import { SCRIPT_TYPES, isDefaultScript, scriptTypeLabel } from "./scriptTypes"

const headers = [
    { title: "Script name", text: "Name", value: "nameComp" },
    { title: "Type", text: "Type", value: "typeComp" },
    { title: "Created by", text: "Created by", value: "createdBy", showFilter: true, filterKey: "createdBy" },
    { title: "Last updated by", text: "Last updated by", value: "lastUpdatedBy", showFilter: true, filterKey: "lastUpdatedBy" },
    { title: "Last updated", text: "Last updated", value: "lastUpdatedComp", sortKey: "lastUpdatedAt", sortActive: true },
    { title: "", type: CellType.ACTION },
]

const sortOptions = [
    { label: "Last updated", value: "updated asc", directionLabel: "Newest", sortKey: "lastUpdatedAt", columnIndex: 5 },
    { label: "Last updated", value: "updated desc", directionLabel: "Oldest", sortKey: "lastUpdatedAt", columnIndex: 5 },
]

const resourceName = { singular: "script", plural: "scripts" }
const definedTableTabs = ["All", "Pre-request", "Post-request"]

function toRow(script) {
    return {
        ...script,
        nameComp: (
            <Box maxWidth="30vw">
                <TooltipText tooltip={script.name} text={script.name} textProps={{ fontWeight: "medium" }} />
            </Box>
        ),
        typeComp: <Badge>{scriptTypeLabel(script.type)}</Badge>,
        createdBy: script.createdBy || "-",
        lastUpdatedBy: script.lastUpdatedBy || "-",
        lastUpdatedComp: func.prettifyEpoch(script.lastUpdatedAt),
    }
}

function ScriptsPage() {
    const { canCall } = usePermissions()
    const canCreate = canCall("api/addScript")
    const canUpdate = canCall("api/updateScript")
    const featureGranted = func.checkForFeatureSaas("TEST_PRE_SCRIPT")

    const [loading, setLoading] = useState(true)
    const [loadFailed, setLoadFailed] = useState(false)
    const [data, setData] = useState({ "all": [], "pre-request": [], "post-request": [] })
    // { scriptId } to edit an existing script, { createType } to create one, null for no editor
    const [editorState, setEditorState] = useState(null)
    const [createMenuOpen, setCreateMenuOpen] = useState(false)

    const [selected, setSelected] = useState(0)
    const [selectedTab, setSelectedTab] = useState("all")
    const { tabsInfo } = useTable()
    const tableCountObj = func.getTabsCount(definedTableTabs, data)
    const tableTabs = func.getTableTabsContent(definedTableTabs, tableCountObj, setSelectedTab, selectedTab, tabsInfo)

    async function fetchScripts() {
        setLoading(true)
        setLoadFailed(false)
        try {
            const resp = await api.fetchAllScripts()
            const rows = (resp.scripts || []).map(toRow)
            setData({
                "all": rows,
                "pre-request": rows.filter((row) => row.type === SCRIPT_TYPES.PRE_REQUEST),
                "post-request": rows.filter((row) => row.type === SCRIPT_TYPES.POST_REQUEST),
            })
        } catch (e) {
            setLoadFailed(true)
        }
        setLoading(false)
    }

    useEffect(() => {
        fetchScripts()
    }, [])

    async function deleteScript(script) {
        try {
            await api.deleteScript(script.id)
            func.setToast(true, false, "Script deleted")
            if (editorState?.scriptId === script.id) setEditorState(null)
            fetchScripts()
        } catch (e) {
            // the request layer shows why (e.g. still selected in a test configuration)
        }
    }

    const getActions = (script) => {
        if (isDefaultScript(script)) return []
        return [{
            items: [{
                content: "Delete",
                destructive: true,
                requires: "api/deleteScript",
                onAction: () => func.showConfirmationModal(
                    `Delete script "${script.name}"? Test configurations that select it must choose another script first.`,
                    "Delete",
                    () => deleteScript(script)
                ),
            }],
        }]
    }

    function openCreate(type) {
        setCreateMenuOpen(false)
        setEditorState({ createType: type })
    }

    function handleSaved(script) {
        if (script?.id) setEditorState({ scriptId: script.id })
        fetchScripts()
    }

    const createAction = (
        <Popover
            active={createMenuOpen}
            onClose={() => setCreateMenuOpen(false)}
            activator={
                <AllowedAction allowed={canCreate}>
                    <Button primary disclosure onClick={() => setCreateMenuOpen((open) => !open)}>Create new</Button>
                </AllowedAction>
            }
        >
            <ActionList
                actionRole="menuitem"
                items={[
                    { content: "Pre-request script", helpText: "Runs before each test request", onAction: () => openCreate(SCRIPT_TYPES.PRE_REQUEST) },
                    { content: "Post-request script", helpText: "Runs on each test response", onAction: () => openCreate(SCRIPT_TYPES.POST_REQUEST) },
                ]}
            />
        </Popover>
    )

    const tableComponent = loadFailed ? (
        <Banner key="scripts-error" status="critical" title="Scripts unavailable" action={{ content: "Retry", onAction: fetchScripts }}>
            <Text as="p">The script library could not be loaded. Nothing has changed; try again.</Text>
        </Banner>
    ) : (
        <GithubSimpleTable
            key="scripts-table"
            data={data[selectedTab]}
            headers={headers}
            headings={headers}
            resourceName={resourceName}
            sortOptions={sortOptions}
            tableTabs={tableTabs}
            selected={selected}
            onSelect={setSelected}
            mode={IndexFiltersMode.Default}
            useNewRow={true}
            loading={loading}
            onRowClick={(script) => setEditorState({ scriptId: script.id })}
            getActions={getActions}
            hasRowActions={true}
            disambiguateLabel={(key, value) => func.convertToDisambiguateLabelObj(value, null, 2)}
        />
    )

    const editorComponent = editorState ? (
        <ScriptEditor
            key={editorState.scriptId || `new-${editorState.createType}`}
            scriptId={editorState.scriptId}
            createType={editorState.createType}
            canWrite={editorState.scriptId ? canUpdate : canCreate}
            onSaved={handleSaved}
            onClose={() => setEditorState(null)}
        />
    ) : null

    const components = featureGranted
        ? [tableComponent, editorComponent].filter(Boolean)
        : [
            <Banner key="scripts-not-enabled" status="info" title="Scripts are not enabled for this account">
                <Text as="p">Pre- and post-request scripts are not part of your current plan.</Text>
            </Banner>,
        ]

    return (
        <PageWithMultipleCards
            title={
                <TitleWithInfo
                    titleText="Scripts"
                    tooltipContent="Reusable pre-request and post-request scripts. Choose them per test run in the Run Test dialog; runs without a choice use default-test-pre / default-test-post."
                />
            }
            isFirstPage={true}
            primaryAction={featureGranted ? createAction : null}
            components={components}
        />
    )
}

export default ScriptsPage
