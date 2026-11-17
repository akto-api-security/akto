import { useEffect, useMemo, useState } from "react"
import { Banner, Modal, Select, Text, VerticalStack } from "@shopify/polaris"
import { v4 as uuidv4 } from "uuid"
import DropdownSearch from "@/apps/dashboard/components/shared/DropdownSearch"
import PersistStore from "@/apps/main/PersistStore"
import api from "../../../testing/api"
import observeApi from "../../../observe/api"
import { SCRIPT_TYPES, scriptTypeLabel } from "../../../testing/scripts/scriptTypes"
import ScriptTypePicker from "./ScriptTypePicker"
import ValidationResultView from "./ValidationResultView"
import useMiniTestingModules from "./useMiniTestingModules"
import useValidationPolling from "./useValidationPolling"
import { collectionLabel, collectionOptions as toCollectionOptions } from "./collectionOptions"

const NO_ROLE = ""
const DASHBOARD_MODULE = ""
const PAIR_NONE = "none"
const PAIR_CONVERSATION = "conversation"
const LIBRARY_PREFIX = "library:"

const otherType = (type) => (type === SCRIPT_TYPES.PRE_REQUEST ? SCRIPT_TYPES.POST_REQUEST : SCRIPT_TYPES.PRE_REQUEST)

// Validate an Ask Akto script: replay one captured request of the collection with it, optionally with a test
// role (service/scripts/validation/CLAUDE.md). In the dashboard module the replay runs inline, like the test
// editor's Run test; in a mini-testing module it is scheduled and polled until the module reports. Either way
// the replay hits the real API.
export default function ValidateScriptModal({ block, type, onTypeChange, conversationScripts, onAskToFix, onClose }) {
    const allCollections = PersistStore((state) => state.allCollections)
    const [apiCollectionId, setApiCollectionId] = useState(block.apiCollectionId)
    const [endpoints, setEndpoints] = useState(null)
    const [endpointsError, setEndpointsError] = useState(false)
    const [endpoint, setEndpoint] = useState(null)
    const [pair, setPair] = useState(PAIR_NONE)
    const [libraryScripts, setLibraryScripts] = useState([])
    const [libraryError, setLibraryError] = useState(false)
    const [roles, setRoles] = useState([])
    const [rolesError, setRolesError] = useState(false)
    const [role, setRole] = useState(NO_ROLE)
    const [running, setRunning] = useState(false)
    const [runError, setRunError] = useState(null)
    const [result, setResult] = useState(null)
    // The scripts the shown result ran with, for "Ask Akto to fix".
    const [ranWith, setRanWith] = useState(null)
    const modules = useMiniTestingModules()
    const polling = useValidationPolling()
    const [moduleName, setModuleName] = useState(DASHBOARD_MODULE)
    const [playgroundId, setPlaygroundId] = useState(null)

    const collectionOptions = useMemo(() => toCollectionOptions(allCollections), [allCollections])
    const knownCollection = (allCollections || []).find((c) => c.id === apiCollectionId)

    useEffect(() => {
        if (apiCollectionId === null || apiCollectionId === undefined) return
        let cancelled = false
        setEndpoints(null)
        setEndpointsError(false)
        setEndpoint(null)
        observeApi.fetchApiInfosForCollection(apiCollectionId)
            .then((resp) => {
                if (cancelled) return
                const list = (resp?.apiInfoList || [])
                    .map((info) => ({ url: info?.id?.url, method: info?.id?.method }))
                    .filter((e) => e.url && e.method)
                    .sort((a, b) => (a.url === b.url ? a.method.localeCompare(b.method) : a.url.localeCompare(b.url)))
                setEndpoints(list)
            })
            .catch(() => { if (!cancelled) setEndpointsError(true) })
        return () => { cancelled = true }
    }, [apiCollectionId])

    // Library scripts and test roles load on their own; a failure is shown on its field.
    useEffect(() => {
        let cancelled = false
        api.fetchAllScripts()
            .then((resp) => { if (!cancelled) setLibraryScripts(resp?.scripts || []) })
            .catch(() => { if (!cancelled) setLibraryError(true) })
        api.fetchTestRoles()
            .then((resp) => { if (!cancelled) setRoles((resp?.testRoles || []).map((r) => ({ label: r.name, value: r.hexId }))) })
            .catch(() => { if (!cancelled) setRolesError(true) })
        return () => { cancelled = true }
    }, [])

    const counterpartType = type ? otherType(type) : null
    const conversationCounterpart = counterpartType ? conversationScripts?.[counterpartType] : null
    const pairOptions = useMemo(() => {
        if (!counterpartType) return []
        const label = scriptTypeLabel(counterpartType).toLowerCase()
        const options = [{ label: `No ${label} script`, value: PAIR_NONE }]
        if (conversationCounterpart) {
            options.push({ label: `Latest ${label} script from this conversation`, value: PAIR_CONVERSATION })
        }
        for (const s of libraryScripts) {
            if (s.type === counterpartType) options.push({ label: `Library: ${s.name}`, value: `${LIBRARY_PREFIX}${s.id}` })
        }
        return options
    }, [counterpartType, conversationCounterpart, libraryScripts])

    const endpointOptions = useMemo(() => (endpoints || []).map((e, i) => ({ label: `${e.method} ${e.url}`, value: String(i) })), [endpoints])
    const roleOptions = [{ label: "No role (captured auth)", value: NO_ROLE }, ...roles]
    const moduleOptions = useMemo(
        () => [{ label: "Default (dashboard)", value: DASHBOARD_MODULE }, ...modules.names.map((name) => ({ label: name, value: name }))],
        [modules.names]
    )
    // A mini-testing result arrives through polling; a dashboard result comes back with the request.
    const shownResult = polling.phase === "done" ? polling.result : result
    const waiting = polling.phase === "waiting"
    const busy = running || waiting
    const canRun = Boolean(type && endpoint && !busy)

    // { preJavascript | preScriptId, postJavascript | postScriptId } for this script and the chosen counterpart.
    const phases = () => {
        const mine = { javascript: block.javascript }
        let other = {}
        if (pair === PAIR_CONVERSATION && conversationCounterpart) other = { javascript: conversationCounterpart.javascript }
        else if (pair.startsWith(LIBRARY_PREFIX)) other = { scriptId: pair.slice(LIBRARY_PREFIX.length) }
        const pre = type === SCRIPT_TYPES.PRE_REQUEST ? mine : other
        const post = type === SCRIPT_TYPES.POST_REQUEST ? mine : other
        return { preJavascript: pre.javascript, preScriptId: pre.scriptId, postJavascript: post.javascript, postScriptId: post.scriptId }
    }

    const run = async () => {
        const scripts = phases()
        setRunning(true)
        setRunError(null)
        setResult(null)
        polling.stop()
        setPlaygroundId(null)
        setRanWith({ ...scripts, endpoint })
        try {
            const resp = await api.validateTestScript({
                validationId: uuidv4(),
                apiCollectionId,
                url: endpoint.url,
                method: endpoint.method,
                ...scripts,
                testRoleId: role || undefined,
                miniTestingName: moduleName || undefined,
            })
            if (resp?.scriptValidationPlaygroundId) {
                setPlaygroundId(resp.scriptValidationPlaygroundId)
                polling.start(resp.scriptValidationPlaygroundId)
            } else {
                setResult(resp?.scriptValidationResult || null)
            }
        } catch (e) {
            setRunError(e?.response?.data?.actionErrors?.[0] || "The validation could not be run. Nothing was saved.")
        }
        setRunning(false)
    }

    // Library scripts are sent by id, so their bodies are not in the feedback; the agent sees only what it wrote.
    const askToFix = () => {
        onAskToFix(
            `I validated script ${block.index + 1} on ${ranWith.endpoint.method} ${ranWith.endpoint.url}. Check the result and fix the script if needed.`,
            {
                type: "test_script_validation",
                data: {
                    ...shownResult,
                    method: ranWith.endpoint.method,
                    url: ranWith.endpoint.url,
                    preJavascript: ranWith.preJavascript,
                    postJavascript: ranWith.postJavascript,
                },
            }
        )
        onClose()
    }

    return (
        <Modal
            open
            large
            onClose={onClose}
            title={`Validate script ${block.index + 1}`}
            primaryAction={shownResult
                ? { content: "Ask Akto to fix", onAction: askToFix }
                : { content: "Run replay", onAction: run, loading: busy, disabled: !canRun }}
            secondaryActions={shownResult
                ? [{ content: "Run again", onAction: run, disabled: !canRun }, { content: "Close", onAction: onClose }]
                : [{ content: "Close", onAction: onClose }]}
        >
            <Modal.Section>
                <VerticalStack gap="4">
                    <Text as="p" color="subdued">
                        Akto replays the newest captured request of the endpoint with this script in {moduleName ? `the mini-testing module ${moduleName}` : "the dashboard module"}. The request goes to the real API, so anything it creates there is created for real. Scripts that poll can take a few minutes.
                    </Text>
                    {!block.type ? <ScriptTypePicker type={type} onChange={onTypeChange} /> : null}

                    {knownCollection && block.apiCollectionId === apiCollectionId ? (
                        <Text as="p">Collection: <Text as="span" fontWeight="semibold">{collectionLabel(knownCollection)}</Text></Text>
                    ) : (
                        <DropdownSearch
                            id="validate-script-collection"
                            label="Collection"
                            placeholder="Select collection"
                            optionsList={collectionOptions}
                            setSelected={setApiCollectionId}
                            value={knownCollection ? collectionLabel(knownCollection) : ""}
                            preSelected={apiCollectionId !== null && apiCollectionId !== undefined ? [apiCollectionId] : []}
                        />
                    )}

                    {endpointsError ? (
                        <Banner status="critical">The collection's endpoints could not be loaded. Close and try again.</Banner>
                    ) : (
                        <DropdownSearch
                            id="validate-script-endpoint"
                            label="Endpoint to replay"
                            placeholder={apiCollectionId === null || apiCollectionId === undefined ? "Select a collection first" : endpoints === null ? "Loading endpoints…" : "Select endpoint"}
                            disabled={!endpoints || endpoints.length === 0}
                            optionsList={endpointOptions}
                            setSelected={(v) => setEndpoint(endpoints?.[Number(v)] || null)}
                            value={endpoint ? `${endpoint.method} ${endpoint.url}` : ""}
                        />
                    )}
                    {endpoints && endpoints.length === 0 ? <Text as="p" color="subdued">This collection has no endpoints.</Text> : null}

                    {counterpartType ? (
                        <VerticalStack gap="1">
                            <Select label={`${scriptTypeLabel(counterpartType)} script to run with it`} options={pairOptions} value={pair} onChange={setPair} />
                            {libraryError ? <Text as="p" color="critical">Library scripts could not be loaded; only scripts from this conversation are listed.</Text> : null}
                        </VerticalStack>
                    ) : null}

                    {modules.hybridEnabled ? (
                        <DropdownSearch
                            id="validate-script-module"
                            label="Run on"
                            placeholder="Select module"
                            optionsList={moduleOptions}
                            setSelected={(v) => setModuleName(v ?? DASHBOARD_MODULE)}
                            value={moduleOptions.find((o) => o.value === moduleName)?.label ?? ""}
                            preSelected={[moduleName]}
                        />
                    ) : null}
                    {modules.loadFailed ? <Text as="p" color="subdued">Mini-testing modules could not be loaded, so the replay will run in the dashboard module.</Text> : null}
                    {moduleName ? (
                        <Banner status="warning" title="This replay runs in a mini-testing module">
                            <Text as="p">The script runs on {moduleName} without the dashboard's sandbox, and older module builds do not report script errors or logs. Read the script before you run it.</Text>
                        </Banner>
                    ) : null}

                    <VerticalStack gap="1">
                        <DropdownSearch
                            id="validate-script-role"
                            label="Test role"
                            placeholder="Select role"
                            optionsList={roleOptions}
                            setSelected={(v) => setRole(v ?? NO_ROLE)}
                            value={roleOptions.find((r) => r.value === role)?.label ?? ""}
                            preSelected={[role]}
                        />
                        {rolesError ? <Text as="p" color="critical">Test roles could not be loaded; the replay can still run with the captured auth.</Text> : null}
                    </VerticalStack>

                    {runError ? <Banner status="critical">{runError}</Banner> : null}
                    {polling.phase === "failed" ? <Banner status="critical">{polling.error}</Banner> : null}
                    {polling.phase === "timedOut" ? (
                        <Banner
                            status="warning"
                            title="No result yet"
                            action={{ content: "Check again", onAction: () => polling.start(playgroundId) }}
                        >
                            <Text as="p">{moduleName} has not reported in 12 minutes. It may be offline, busy, or still running the script. This is not a failure of the script.</Text>
                        </Banner>
                    ) : null}
                    {polling.transientError ? <Text as="p" color="subdued">The dashboard could not be reached just now. Still trying.</Text> : null}
                    <ValidationResultView
                        result={shownResult}
                        running={busy}
                        runningText={waiting ? `Waiting for ${moduleName} to pick up the replay and run it. This can take a few minutes.` : undefined}
                    />
                </VerticalStack>
            </Modal.Section>
        </Modal>
    )
}
