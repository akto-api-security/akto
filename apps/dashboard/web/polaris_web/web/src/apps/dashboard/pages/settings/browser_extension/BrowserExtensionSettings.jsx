import { useState, useEffect, useCallback, useMemo } from "react";
import {
    ActionList, Avatar, Box, Button, Divider, Form, HorizontalStack, Icon,
    Modal, Pagination, Popover, Text, TextField, Tooltip, VerticalStack,
} from "@shopify/polaris";
import { SearchMinor, HorizontalDotsMinor, DeleteMinor, InfoMinor } from "@shopify/polaris-icons";
import PageWithMultipleCards from "../../../components/layouts/PageWithMultipleCards";
import Dropdown from "../../../components/layouts/Dropdown";
import { sharedIconCacheService } from "../../../components/shared/CollectionIcon";
import func from "@/util/func";
import api from "../../guardrails/api";
import "./BrowserExtensionSettings.css";

const CONFIG_PAGE_SIZE = 10;   // configured hosts per page

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

// Lightweight, theme-aware toggle (Polaris ships no native switch in this version).
function Switch({ active, onChange, title }) {
    return (
        <Box
            className={`bext-switch ${active ? "on" : ""}`}
            role="switch" aria-checked={active} aria-label={title} tabIndex={0} title={title}
            onClick={onChange}
            onKeyDown={(e) => { if (e.key === "Enter" || e.key === " ") { e.preventDefault(); onChange(); } }}
        >
            <Box as="span" className="bext-switch-knob" />
        </Box>
    );
}

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
    const [cfgFilter, setCfgFilter] = useState("");
    const [cfgPage, setCfgPage] = useState(0);            // configured list pagination (10 / page)
    const [openMenuId, setOpenMenuId] = useState(null);   // which row's ⋯ menu is open

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
    useEffect(() => { setCfgPage(0); }, [cfgFilter]);   // jump back to page 1 on a new filter

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

    const visibleConfigured = useMemo(() => {
        const q = cfgFilter.trim().toLowerCase();
        if (!q) return mergedRows;
        return mergedRows.filter((r) => (r.host || "").toLowerCase().includes(q) || (r.name || "").toLowerCase().includes(q));
    }, [mergedRows, cfgFilter]);

    // paginate the configured list (10 / page)
    const cfgTotalPages = Math.max(1, Math.ceil(visibleConfigured.length / CONFIG_PAGE_SIZE));
    const cfgPageSafe = Math.min(cfgPage, cfgTotalPages - 1);
    const cfgStart = cfgPageSafe * CONFIG_PAGE_SIZE;
    const pagedConfigured = visibleConfigured.slice(cfgStart, cfgStart + CONFIG_PAGE_SIZE);

    // ── write actions ───────────────────────────────────────────────────
    const toggleConfigured = async (host, nextActive) => {
        try {
            await api.setBrowserExtensionConfigActive(host, nextActive);
            await fetchAll();
        } catch (error) {
            func.setToast(true, true, "Failed to update host");
        }
    };
    const removeConfigured = (host, hexId) => {
        func.showConfirmationModal(`Remove ${host} from configured hosts?`, "Remove", async () => {
            try {
                await api.deleteBrowserExtensionConfigs([hexId]);
                func.setToast(true, false, `${host} removed`);
                await fetchAll();
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
            await fetchAll();
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

    // ── configured section ──────────────────────────────────────────────
    const skeletonList = (
        <Box className="bext-list">
            {Array.from({ length: 5 }).map((_, i) => (
                <Box className="bext-row" key={`sk-${i}`}>
                    <Box className="bext-sk bext-sk-avatar" />
                    <Box className="bext-grow">
                        <Box className="bext-sk bext-sk-title" />
                        <Box className="bext-sk bext-sk-sub" />
                    </Box>
                    <Box className="bext-sk bext-sk-switch" />
                </Box>
            ))}
        </Box>
    );

    const configuredRow = (c) => {
        const isCustom = c.source === "custom";
        const menuItems = [
            { content: "Edit", onAction: () => { setOpenMenuId(null); openEdit(c); } },
            { content: "Remove", destructive: true, onAction: () => { setOpenMenuId(null); removeConfigured(c.host, c.hexId); } },
        ];
        return (
            <Box className={`bext-row ${c.active ? "" : "off"}`} key={c.host}>
                {hostAvatar(c.host, c.iconUrl)}
                <Box className="bext-grow">
                    <Text variant="bodyMd" fontWeight="medium" truncate>{c.name || c.host}</Text>
                    <Text variant="bodySm" color="subdued" truncate>
                        {c.name ? c.host : (isCustom ? ((c.paths || []).join(", ") || "Custom") : "Akto")}
                    </Text>
                </Box>
                <Switch active={c.active} onChange={() => toggleConfigured(c.host, !c.active)}
                    title={c.active ? "Disable for this account" : "Enable"} />
                {isCustom && (
                    <Popover
                        active={openMenuId === c.host}
                        onClose={() => setOpenMenuId(null)}
                        preferredAlignment="right"
                        activator={
                            <Button
                                plain
                                icon={HorizontalDotsMinor}
                                accessibilityLabel={`Actions for ${c.name || c.host}`}
                                onClick={() => setOpenMenuId(openMenuId === c.host ? null : c.host)}
                            />
                        }
                    >
                        <ActionList actionRole="menuitem" items={menuItems} />
                    </Popover>
                )}
            </Box>
        );
    };

    const configuredSection = (
        <Box key="ext-configured">
            <HorizontalStack align="space-between" blockAlign="start">
                <Box>
                    <span className="bext-eyebrow">Inspected hosts</span>
                    {!loading && totalCount > 0 && (
                        <Box className="bext-summary">
                            <span className="k"><b>{totalCount}</b> host{totalCount !== 1 ? "s" : ""}</span>
                            <span className="k"><span className="d on" /><b>{activeCount}</b> active</span>
                            {offCount > 0 && <span className="k"><span className="d off" /><b>{offCount}</b> off</span>}
                        </Box>
                    )}
                </Box>
                {!loading && totalCount > 0 && (
                    <Box className="bext-filter">
                        <TextField
                            labelHidden label="Filter hosts" value={cfgFilter} onChange={setCfgFilter}
                            placeholder="Filter hosts…" prefix={<Icon source={SearchMinor} color="subdued" />}
                            autoComplete="off" clearButton onClearButtonClick={() => setCfgFilter("")}
                        />
                    </Box>
                )}
            </HorizontalStack>
            <Box paddingBlockStart="3">
                {loading ? skeletonList : totalCount === 0 ? (
                    <Box background="bg-surface" borderColor="border" borderWidth="1" borderRadius="300" padding="10">
                        <VerticalStack gap="4" inlineAlign="center">
                            <Box background="bg-subdued" borderRadius="300" padding="3">
                                <Icon source={SearchMinor} color="subdued" />
                            </Box>
                            <VerticalStack gap="1" inlineAlign="center">
                                <Text variant="headingSm">No hosts yet</Text>
                                <Text alignment="center" color="subdued">
                                    Akto's supported hosts load here automatically. You can also add your own custom host.
                                </Text>
                            </VerticalStack>
                            <Button primary onClick={openCustom}>Add custom host</Button>
                        </VerticalStack>
                    </Box>
                ) : (
                    <VerticalStack gap="3">
                        <Box className="bext-list">
                            {visibleConfigured.length === 0
                                ? <Box className="bext-row"><Text color="subdued">No hosts match “{cfgFilter}”.</Text></Box>
                                : pagedConfigured.map(configuredRow)}
                        </Box>
                        {visibleConfigured.length > CONFIG_PAGE_SIZE && (
                            <Box paddingBlockStart="1">
                                <HorizontalStack align="center" blockAlign="center" gap="4">
                                    <Pagination
                                        hasPrevious={cfgPageSafe > 0}
                                        onPrevious={() => setCfgPage((p) => Math.max(0, p - 1))}
                                        hasNext={cfgPageSafe < cfgTotalPages - 1}
                                        onNext={() => setCfgPage((p) => Math.min(cfgTotalPages - 1, p + 1))}
                                        label={`${cfgStart + 1}–${Math.min(cfgStart + CONFIG_PAGE_SIZE, visibleConfigured.length)} of ${visibleConfigured.length}`}
                                    />
                                </HorizontalStack>
                            </Box>
                        )}
                    </VerticalStack>
                )}
            </Box>
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
