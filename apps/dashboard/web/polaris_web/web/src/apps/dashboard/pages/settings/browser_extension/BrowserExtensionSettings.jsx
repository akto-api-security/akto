import { useState, useEffect, useCallback, useMemo } from "react";
import {
    Avatar, Badge, Box, Button, Divider, EmptySearchResult, Form, HorizontalStack, Icon,
    Modal, Text, TextField, Tooltip, VerticalStack,
} from "@shopify/polaris";
import { DeleteMinor, InfoMinor } from "@shopify/polaris-icons";
import PageWithMultipleCards from "../../../components/layouts/PageWithMultipleCards";
import Dropdown from "../../../components/layouts/Dropdown";
import GithubSimpleTable from "../../../components/tables/GithubSimpleTable";
import { CellType } from "@/apps/dashboard/components/tables/rows/GithubRow";
import { sharedIconCacheService } from "../../../components/shared/CollectionIcon";
import func from "@/util/func";
import api from "../../guardrails/api";
import "./BrowserExtensionSettings.css";

// full config-driven custom-host form (mirrors the extension's monitoring-config schema)
const EMPTY_FORM = {
    host: "", active: true, transport: "http",
    method: "POST", format: "json",
    paths: [""],            // request URL paths to intercept
    promptPaths: [""],      // candidate paths to the prompt in the body (first match wins)
    operations: [""],       // graphql only
    frameMatch: [{ k: "", v: "" }],   // websocket only
    responseFormat: "none", responsePath: "", modelPath: "",
};
// body-format options per transport
const FORMAT_OPTIONS = {
    http: [["json", "JSON"], ["form", "Form-encoded"], ["sse", "SSE stream"], ["connect-rpc", "Connect-RPC"], ["socket.io", "Socket.IO"], ["nested-envelope", "Nested-envelope"]],
    websocket: [["ws-frame", "WS frame (JSON)"], ["dgw", "DGW (protobuf)"], ["socket.io", "Socket.IO"]],
    graphql: [["json", "JSON"]],
};

const HOST_RESOURCE_NAME = { singular: "host", plural: "hosts" };

// Prefer a stored icon_url; otherwise derive the site's real favicon from its host (host == domain).
// Avatar falls back to the host's initials if the favicon fails to load.
function faviconFor(host, iconUrl) {
    if (iconUrl) return iconUrl;
    return host ? sharedIconCacheService.getFaviconUrl(host) : undefined;
}

// Every favicon sits inside a unified white rounded tile so light/dark logos read evenly.
function hostAvatar(host, iconUrl) {
    return (
        <span className="bext-tile">
            <Avatar size="extraSmall" shape="square" source={faviconFor(host, iconUrl) || undefined} name={host} />
        </span>
    );
}

function BrowserExtensionSettings() {
    const [configured, setConfigured] = useState([]);
    const [catalogue, setCatalogue] = useState([]);
    const [loading, setLoading] = useState(false);
    // GithubSimpleTable caches its rows/filters internally and only re-derives them when its
    // own key changes — bump this after every mutation so Activate/Deactivate/Edit/Remove
    // (which don't change row count) still show fresh data.
    const [refreshCounter, setRefreshCounter] = useState(0);

    // custom-host add/edit modal
    const [pickerOpen, setPickerOpen] = useState(false);
    const [showAdv, setShowAdv] = useState(false);   // custom-form advanced section
    const [showExample, setShowExample] = useState(false);   // custom-form worked example
    const [form, setForm] = useState(EMPTY_FORM);
    const [editingHexId, setEditingHexId] = useState(null);
    const [formErrors, setFormErrors] = useState({});
    const [saving, setSaving] = useState(false);

    const fetchAll = useCallback(async () => {
        setLoading(true);
        try {
            const [accountResp, commonResp] = await Promise.all([
                api.fetchBrowserExtensionConfigs(),
                api.fetchBrowserExtensionConfigsCommon(),
            ]);
            setConfigured(accountResp?.browserExtensionConfigs || []);
            setCatalogue(commonResp?.browserExtensionConfigsCommon || []);
        } catch (error) {
            func.setToast(true, true, "Failed to load browser extension configs");
        } finally {
            setLoading(false);
        }
    }, []);

    useEffect(() => { fetchAll(); }, [fetchAll]);

    // refetch and force the table to re-derive its rows/filters/selection
    const refresh = async () => {
        setRefreshCounter((v) => v + 1);
        await fetchAll();
    };

    // Applies an active-state change to local state instead of re-fetching the whole catalogue —
    // toggling/bulk-toggling doesn't change which hosts exist, so there's nothing new to fetch. At
    // catalogue sizes in the thousands, re-pulling everything on every click doesn't scale; this
    // keeps the round trip to just the write. Still bumps refreshCounter to remount the table so its
    // internal filter-choice cache (keyed off row count/identity, not content) doesn't go stale.
    const patchActive = (hosts, active) => {
        const hostSet = new Set(hosts.map((h) => h.toLowerCase()));
        setConfigured((prev) => {
            const byHost = new Map(prev.map((c) => [(c.host || "").toLowerCase(), c]));
            hostSet.forEach((key) => {
                const existing = byHost.get(key);
                byHost.set(key, existing ? { ...existing, active } : { host: hosts.find((h) => h.toLowerCase() === key), active, hexId: null });
            });
            return Array.from(byHost.values());
        });
        setRefreshCounter((v) => v + 1);
    };

    // sort by the Mongo id (hexId) ascending — top brands were given the oldest ids, so they lead
    const idOf = (c) => c?.hexId || "￿";
    const byId = (a, b) => idOf(a).localeCompare(idOf(b));

    // Every catalogue host is ON by default; the account collection only stores what the user changed
    // (an opt-out with active:false, or a custom host). The displayed list is common ⊕ account overlaid:
    // each host appears exactly once, and the account row's `active` always wins over the default.
    const mergedRows = useMemo(() => {
        const overrideByHost = {};
        configured.forEach((c) => { overrideByHost[(c.host || "").toLowerCase()] = c; });
        const seen = new Set();
        const rows = [];
        // catalogue hosts, in rank order — on unless the account opted this one out
        catalogue.slice().sort(byId).forEach((cat) => {
            const key = (cat.host || "").toLowerCase();
            if (!key || seen.has(key)) return;   // never list the same host twice
            seen.add(key);
            const ov = overrideByHost[key];
            rows.push({ ...cat, active: ov ? ov.active !== false : true, hexId: ov?.hexId || null, source: "catalogue" });
        });
        // account-only custom hosts (any host not already shown from the catalogue)
        configured.forEach((c) => {
            const key = (c.host || "").toLowerCase();
            if (!key || seen.has(key)) return;
            seen.add(key);
            rows.push({ ...c, active: c.active !== false, source: "custom" });
        });
        return rows;
    }, [catalogue, configured]);

    const totalCount = mergedRows.length;
    const activeCount = useMemo(() => mergedRows.filter((r) => r.active).length, [mergedRows]);
    const offCount = totalCount - activeCount;

    // ── write actions ───────────────────────────────────────────────────
    const toggleConfigured = async (host, nextActive) => {
        try {
            await api.setBrowserExtensionConfigActive(host, nextActive);
            func.setToast(true, false, `${host} ${nextActive ? "activated" : "deactivated"}`);
            patchActive([host], nextActive);
        } catch (error) {
            func.setToast(true, true, "Failed to update host");
        }
    };
    const bulkSetActive = async (hosts, active) => {
        if (hosts.length === 0) return;
        try {
            await api.setBrowserExtensionConfigsActive(hosts, active);
            func.setToast(true, false, `${hosts.length} host${hosts.length !== 1 ? "s" : ""} ${active ? "activated" : "deactivated"}`);
            patchActive(hosts, active);
        } catch (error) {
            func.setToast(true, true, "Failed to update selected hosts");
        }
    };
    const removeConfigured = (host, hexId) => {
        func.showConfirmationModal(`Remove ${host} from configured hosts?`, "Remove", async () => {
            try {
                await api.deleteBrowserExtensionConfigs([hexId]);
                func.setToast(true, false, `${host} removed`);
                setConfigured((prev) => prev.filter((c) => c.hexId !== hexId));
                setRefreshCounter((v) => v + 1);
            } catch (error) {
                func.setToast(true, true, "Failed to remove host");
            }
        });
    };

    // ── custom-host add / edit modal ────────────────────────────────────
    const openCustom = () => {
        setForm(EMPTY_FORM); setEditingHexId(null); setFormErrors({});
        setShowAdv(false); setShowExample(false); setPickerOpen(true);
    };
    const openEdit = (config) => {
        const asArr = (v) => (Array.isArray(v) ? v : v ? [v] : []);
        const promptPaths = asArr(config.path);
        const fmEntries = config.frameMatch ? Object.entries(config.frameMatch).map(([k, v]) => ({ k, v: String(v) })) : [];
        setForm({
            host: config.host || "", active: config.active !== false,
            transport: config.transport || "http",
            method: config.method || "POST",
            format: config.format || (config.transport === "websocket" ? "ws-frame" : "json"),
            paths: (config.paths && config.paths.length) ? [...config.paths] : [""],
            promptPaths: promptPaths.length ? promptPaths : [""],
            operations: (config.operations && config.operations.length) ? [...config.operations] : [""],
            frameMatch: fmEntries.length ? fmEntries : [{ k: "", v: "" }],
            responseFormat: config.responseFormat || "none",
            responsePath: asArr(config.responsePath)[0] || "",
            modelPath: asArr(config.modelPath)[0] || "",
        });
        setEditingHexId(config.hexId); setFormErrors({}); setShowAdv(!!config.responseFormat); setPickerOpen(true);
    };
    const closePicker = () => {
        setPickerOpen(false); setForm(EMPTY_FORM); setEditingHexId(null); setFormErrors({});
        setShowAdv(false); setShowExample(false);
    };
    // form helpers for the repeatable array fields
    const setField = (k, v) => setForm((f) => ({ ...f, [k]: v }));
    const setArrItem = (k, i, v) => setForm((f) => ({ ...f, [k]: f[k].map((x, j) => (j === i ? v : x)) }));
    const addArrItem = (k, blank = "") => setForm((f) => ({ ...f, [k]: [...f[k], blank] }));
    const rmArrItem = (k, i) => setForm((f) => ({ ...f, [k]: f[k].length > 1 ? f[k].filter((_, j) => j !== i) : f[k] }));
    const setFmItem = (i, key, val) => setForm((f) => ({ ...f, frameMatch: f.frameMatch.map((x, j) => (j === i ? { ...x, [key]: val } : x)) }));

    // renders a labelled list of text inputs with add/remove (paths, prompt paths, operations)
    const renderRepeatable = (key, { label, sub, placeholder, helpText, addLabel, error }) => (
        <VerticalStack gap="1">
            <Text variant="bodyMd" fontWeight="semibold">
                {label}{sub && <Text as="span" color="subdued"> {sub}</Text>}
            </Text>
            <VerticalStack gap="2">
                {form[key].map((val, i) => (
                    <HorizontalStack key={i} gap="2" wrap={false} blockAlign="center">
                        <Box className="bext-grow">
                            <TextField labelHidden label={`${label} ${i + 1}`} value={val}
                                onChange={(v) => setArrItem(key, i, v)} placeholder={placeholder} autoComplete="off" />
                        </Box>
                        {form[key].length > 1 && (
                            <Button plain icon={DeleteMinor} accessibilityLabel="Remove" onClick={() => rmArrItem(key, i)} />
                        )}
                    </HorizontalStack>
                ))}
            </VerticalStack>
            <Box><Button plain onClick={() => addArrItem(key)}>{addLabel}</Button></Box>
            {error && <Text variant="bodySm" color="critical">{error}</Text>}
            {helpText && <Text variant="bodySm" color="subdued">{helpText}</Text>}
        </VerticalStack>
    );

    const saveCustom = async () => {
        const host = form.host.trim();
        const paths = form.paths.map((p) => p.trim()).filter(Boolean);
        const promptPaths = form.promptPaths.map((p) => p.trim()).filter(Boolean);
        const errors = {};
        if (!host) errors.host = "Host is required";
        if (paths.length === 0) errors.paths = "At least one request path is required";
        if (Object.keys(errors).length > 0) { setFormErrors(errors); return; }

        const payload = { host, active: form.active, paths, transport: form.transport };
        if (form.transport === "http") { payload.method = form.method; payload.format = form.format; }
        else if (form.transport === "websocket") {
            payload.format = form.format;
            const fm = {};
            form.frameMatch.forEach(({ k, v }) => { if (k.trim() && v.trim()) fm[k.trim()] = v.trim(); });
            if (Object.keys(fm).length) payload.frameMatch = fm;
        } else if (form.transport === "graphql") {
            payload.format = "json";
            const ops = form.operations.map((o) => o.trim()).filter(Boolean);
            if (ops.length) payload.operations = ops;
        }
        if (promptPaths.length) payload.path = promptPaths;
        if (form.responseFormat && form.responseFormat !== "none") {
            payload.responseFormat = form.responseFormat;
            if (form.responsePath.trim()) payload.responsePath = [form.responsePath.trim()];
            if (form.modelPath.trim()) payload.modelPath = [form.modelPath.trim()];
        }

        setSaving(true);
        try {
            await api.saveBrowserExtensionConfig(payload, editingHexId || undefined);
            func.setToast(true, false, `Config ${editingHexId ? "updated" : "added"} successfully`);
            closePicker();
            await refresh();
        } catch (error) {
            func.setToast(true, true, "Failed to save config");
        } finally {
            setSaving(false);
        }
    };

    const handleDownload = () => {
        if (mergedRows.length === 0) {
            func.setToast(true, true, "No hosts to download");
            return;
        }
        const rows = mergedRows.map((c) => ({
            Host: c.host || "-",
            Source: c.source === "custom" ? "Custom" : "Akto",
            Status: c.active ? "Active" : "Inactive",
            Paths: (c.paths || []).join(" & ") || "-",
        }));
        func.downloadAsCSV(rows, { name: "browser_extension_configs" });
    };

    // ── table rows — GithubSimpleTable gives us select-all/deselect-all and an
    // active/inactive filter for free, matching the table design used elsewhere
    // (e.g. Misconfigurations, Webhooks) instead of a bespoke list. ────────
    const tableRows = useMemo(() => mergedRows.map((c) => {
        const isCustom = c.source === "custom";
        return {
            id: (c.host || "").toLowerCase(),
            host: c.host,
            name: c.name || c.host,
            hexId: c.hexId,
            isCustom,
            status: c.active ? "Active" : "Inactive",
            hostCell: (
                <HorizontalStack gap="2" blockAlign="center" wrap={false}>
                    {hostAvatar(c.host, c.iconUrl)}
                    <Box>
                        <Text variant="bodyMd" fontWeight="medium" truncate>{c.name || c.host}</Text>
                        <Text variant="bodySm" color="subdued" truncate>
                            {c.name ? c.host : (isCustom ? ((c.paths || []).join(", ") || "Custom") : "Akto")}
                        </Text>
                    </Box>
                </HorizontalStack>
            ),
            source: isCustom ? "Custom" : "Akto",
            statusComp: (
                <Badge status={c.active ? "success" : "subdued"}>{c.active ? "Active" : "Inactive"}</Badge>
            ),
        };
    }), [mergedRows]);

    const hostHeaders = [
        { text: "Host", value: "hostCell", title: "Host" },
        { text: "Source", value: "source", showFilter: true, singleSelect: true },
        { text: "Status", value: "statusComp", filterKey: "status", filterLabel: "Status", showFilter: true, singleSelect: true },
        { text: "Actions", type: CellType.ACTION },
    ];

    const getActions = (item) => {
        const items = [];
        const isActive = item.status === "Active";
        items.push({
            content: isActive ? "Deactivate" : "Activate",
            onAction: () => toggleConfigured(item.host, !isActive),
        });
        if (item.isCustom) {
            items.push({
                content: "Edit",
                onAction: () => {
                    const raw = mergedRows.find((c) => (c.host || "").toLowerCase() === item.id);
                    if (raw) openEdit(raw);
                },
            });
            items.push({
                content: "Remove",
                destructive: true,
                onAction: () => removeConfigured(item.host, item.hexId),
            });
        }
        return [{ items }];
    };

    const promotedBulkActions = (selectedIds) => {
        const hosts = tableRows.filter((r) => selectedIds.includes(r.id)).map((r) => r.host);
        return [
            { content: "Activate", onAction: () => bulkSetActive(hosts, true) },
            { content: "Deactivate", onAction: () => bulkSetActive(hosts, false) },
        ];
    };

    const emptyHostsMarkup = (
        <EmptySearchResult
            title="No hosts yet"
            description="Akto's supported hosts load here automatically. You can also add your own custom host."
            withIllustration
        />
    );

    // Builds the label shown on an applied filter chip (e.g. "Status: Active"). Required by
    // GithubServerTable's changeAppliedFilters — without it, selecting a filter choice throws
    // before the selection is ever applied, so both showFilter columns (Source, Status) just
    // sit there inert. Values here are plain strings, so the generic join is all that's needed.
    const disambiguateLabel = (key, value) => func.convertToDisambiguateLabelObj(value, null, 2);

    // ── configured section ──────────────────────────────────────────────
    const configuredSection = (
        <Box key="ext-configured">
            <Box paddingBlockEnd="3">
                <span className="bext-eyebrow">Inspected hosts</span>
                {!loading && totalCount > 0 && (
                    <Box className="bext-summary">
                        <span className="k"><b>{totalCount}</b> host{totalCount !== 1 ? "s" : ""}</span>
                        <span className="k"><span className="d on" /><b>{activeCount}</b> Active</span>
                        {offCount > 0 && <span className="k"><span className="d off" /><b>{offCount}</b> Inactive</span>}
                    </Box>
                )}
            </Box>
            <GithubSimpleTable
                key={`bext-table-${refreshCounter}`}
                resourceName={HOST_RESOURCE_NAME}
                useNewRow={true}
                headers={hostHeaders}
                headings={hostHeaders}
                data={tableRows}
                loading={loading}
                loadingText="Loading inspected hosts..."
                selectable={true}
                promotedBulkActions={promotedBulkActions}
                hasRowActions={true}
                getActions={getActions}
                emptyStateMarkup={emptyHostsMarkup}
                searchKeys={["host", "name"]}
                disambiguateLabel={disambiguateLabel}
                filterStateUrl="/dashboard/settings/browser-extension/"
            />
        </Box>
    );

    // ── custom-host modal ───────────────────────────────────────────────
    const pickerModal = (
        <Modal
            key="ext-picker" open={pickerOpen} onClose={closePicker}
            title={editingHexId ? "Edit custom host" : "Add custom host"}
            primaryAction={{ content: editingHexId ? "Save" : "Add config", onAction: saveCustom, loading: saving }}
            secondaryActions={[{ content: "Cancel", onAction: closePicker }]}
        >
            <Modal.Section>
                    <Box className="form-class bext-form">
                    <Form onSubmit={saveCustom}>
                        <VerticalStack gap="4">
                            {!editingHexId && (
                                <Box background="bg-surface-secondary" borderRadius="200" padding="3">
                                    <VerticalStack gap="2">
                                        <HorizontalStack align="space-between" blockAlign="center" gap="2">
                                            <Text variant="bodySm" color="subdued">Not sure what to enter? See a worked example.</Text>
                                            <Button plain disclosure={showExample ? "up" : "down"} onClick={() => setShowExample((s) => !s)}>
                                                {showExample ? "Hide example" : "See an example"}
                                            </Button>
                                        </HorizontalStack>
                                        {showExample && (
                                            <VerticalStack gap="2">
                                                <Text variant="bodySm" fontWeight="semibold">Example — ChatGPT (chatgpt.com)</Text>
                                                <Text variant="bodySm" color="subdued">
                                                    When you send a message on ChatGPT, the site makes this request — each field below maps to a part of it:
                                                </Text>
                                                <VerticalStack gap="1">
                                                    <Text variant="bodySm"><b>Host</b> → <code>chatgpt.com</code> — the domain the chat runs on.</Text>
                                                    <Text variant="bodySm"><b>Transport</b> → HTTP · <b>Method</b> → POST · <b>Body format</b> → JSON.</Text>
                                                    <Text variant="bodySm"><b>Request path</b> → <code>/backend-api/conversation</code> — the network call fired when you hit send.</Text>
                                                    <Text variant="bodySm"><b>Prompt location</b> → <code>messages[-1].content.parts</code> — where your typed text sits inside that request's JSON body.</Text>
                                                </VerticalStack>
                                                <Text variant="bodySm" color="subdued">
                                                    Tip: open DevTools → Network, send one message on the site, find the request, and read its payload to fill these in.
                                                </Text>
                                            </VerticalStack>
                                        )}
                                    </VerticalStack>
                                </Box>
                            )}
                            <TextField
                                label="Host" value={form.host} onChange={(v) => setField("host", v)}
                                placeholder="chat.example.com" error={formErrors.host} autoComplete="off"
                                helpText="The domain the chat UI runs on — no https:// or path."
                                disabled={!!editingHexId}
                            />
                            <Dropdown
                                id="bext-transport" label="Transport"
                                menuItems={[{ label: "HTTP", value: "http" }, { label: "WebSocket", value: "websocket" }, { label: "GraphQL", value: "graphql" }]}
                                initial={form.transport}
                                selected={(v) => setForm((f) => ({ ...f, transport: v, format: FORMAT_OPTIONS[v][0][0] }))}
                            />

                            {renderRepeatable("paths", {
                                label: "Request paths", placeholder: "/api/chat",
                                helpText: "Request URLs to intercept. Wildcards ok: /api/*, /threads/*/messages.",
                                addLabel: "＋ Add path", error: formErrors.paths,
                            })}

                            {form.transport === "http" && (
                                <HorizontalStack gap="4" wrap={false}>
                                    <Box className="bext-grow">
                                        <Dropdown id="bext-method" label="Method"
                                            menuItems={["POST", "GET", "PUT", "PATCH"].map((m) => ({ label: m, value: m }))}
                                            initial={form.method} selected={(v) => setField("method", v)} />
                                    </Box>
                                    <Box className="bext-grow">
                                        <Dropdown id="bext-format-http" label="Body format"
                                            menuItems={FORMAT_OPTIONS.http.map(([v, l]) => ({ label: l, value: v }))}
                                            initial={form.format} selected={(v) => setField("format", v)} />
                                    </Box>
                                </HorizontalStack>
                            )}

                            {form.transport === "websocket" && (
                                <>
                                    <Dropdown id="bext-format-ws" label="Frame format"
                                        menuItems={FORMAT_OPTIONS.websocket.map(([v, l]) => ({ label: l, value: v }))}
                                        initial={form.format} selected={(v) => setField("format", v)} />
                                    <VerticalStack gap="1">
                                        <Text variant="bodyMd" fontWeight="semibold">
                                            Frame match <Text as="span" color="subdued">— which frame carries the prompt</Text>
                                        </Text>
                                        <VerticalStack gap="2">
                                            {form.frameMatch.map((row, i) => (
                                                <HorizontalStack key={i} gap="2" wrap={false} blockAlign="center">
                                                    <Box className="bext-grow">
                                                        <TextField labelHidden label={`key ${i}`} value={row.k} onChange={(v) => setFmItem(i, "k", v)} placeholder="event" autoComplete="off" />
                                                    </Box>
                                                    <Text>=</Text>
                                                    <Box className="bext-grow">
                                                        <TextField labelHidden label={`value ${i}`} value={row.v} onChange={(v) => setFmItem(i, "v", v)} placeholder="send" autoComplete="off" />
                                                    </Box>
                                                    {form.frameMatch.length > 1 && (
                                                        <Button plain icon={DeleteMinor} accessibilityLabel="Remove" onClick={() => rmArrItem("frameMatch", i)} />
                                                    )}
                                                </HorizontalStack>
                                            ))}
                                        </VerticalStack>
                                        <Box paddingBlockStart="2"><Button plain onClick={() => addArrItem("frameMatch", { k: "", v: "" })}>＋ Add condition</Button></Box>
                                    </VerticalStack>
                                </>
                            )}

                            {form.transport === "graphql" && renderRepeatable("operations", {
                                label: "Operations", sub: "— which GraphQL ops to gate",
                                placeholder: "sendMessageMutation", addLabel: "＋ Add operation",
                            })}

                            {renderRepeatable("promptPaths", {
                                label: "Prompt location", placeholder: "messages[-1].content",
                                helpText: "Where the user's prompt sits in the body (JSONPath-ish). First matching path wins.",
                                addLabel: "＋ Add fallback path",
                            })}

                            <Divider />
                            <Button plain disclosure={showAdv ? "up" : "down"} onClick={() => setShowAdv((s) => !s)}>
                                Advanced — response &amp; model
                            </Button>
                            {showAdv && (
                                <VerticalStack gap="4">
                                    <Dropdown id="bext-response-format" label="Response format"
                                        menuItems={[{ label: "None", value: "none" }, { label: "SSE", value: "sse" }, { label: "JSON", value: "json" }, { label: "Nested-envelope", value: "nested-envelope" }]}
                                        initial={form.responseFormat} selected={(v) => setField("responseFormat", v)} />
                                    <TextField label="Response path" value={form.responsePath} onChange={(v) => setField("responsePath", v)}
                                        placeholder="choices[*].delta.content" autoComplete="off" helpText="Where the AI answer is in the response." />
                                    <TextField label="Model path" value={form.modelPath} onChange={(v) => setField("modelPath", v)}
                                        placeholder="model" autoComplete="off" helpText="Where the model name is." />
                                </VerticalStack>
                            )}
                        </VerticalStack>
                    </Form>
                    </Box>
            </Modal.Section>
        </Modal>
    );

    return (
        <PageWithMultipleCards
                title={"Browser Extension"}
                titleMetadata={
                    <Tooltip
                        content="Before v1.0.61 the extension supported only ChatGPT, Grok, Claude, Gemini, Copilot and DeepSeek. From v1.0.61 every configured host is inspected — the top 5 are generally available, and every other supported host is in beta and may change."
                        dismissOnMouseOut
                    >
                        <span className="bext-info"><Icon source={InfoMinor} color="subdued" /></span>
                    </Tooltip>
                }
                subtitle={"Choose which hosts the Akto extension inspects."}
                isFirstPage={true}
                fullWidth={false}
                primaryAction={<Button primary onClick={openCustom}>Add custom host</Button>}
                secondaryActions={<Button onClick={handleDownload} disabled={loading || mergedRows.length === 0}>Download</Button>}
                components={[pickerModal, configuredSection]}
        />
    );
}

export default BrowserExtensionSettings;
