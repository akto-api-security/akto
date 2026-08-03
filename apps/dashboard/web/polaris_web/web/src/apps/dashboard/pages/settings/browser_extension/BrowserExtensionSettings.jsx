import { useState, useEffect, useCallback, useMemo } from "react";
import {
    ActionList, Avatar, Box, Button, Divider, Form, HorizontalStack, Icon,
    Modal, Pagination, Popover, Text, TextField, VerticalStack,
} from "@shopify/polaris";
import { SearchMinor, HorizontalDotsMinor, DeleteMinor } from "@shopify/polaris-icons";
import PageWithMultipleCards from "../../../components/layouts/PageWithMultipleCards";
import Dropdown from "../../../components/layouts/Dropdown";
import { sharedIconCacheService } from "../../../components/shared/CollectionIcon";
import func from "@/util/func";
import api from "../../guardrails/api";

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

// Scoped styles for behaviours inline styles can't express (hover-reveal, row hover, status dot).
// Uses Polaris tokens so light/dark both track the theme.
const CSS = `
.bext-list { border:1px solid var(--p-color-border,#e9ebf1); border-radius:14px; overflow:hidden;
  background:var(--p-color-bg-surface,#fff); box-shadow:0 1px 2px rgba(17,20,28,.04),0 4px 18px rgba(17,20,28,.05); }
.bext-row { display:flex; align-items:center; gap:14px; padding:13px 16px;
  border-top:1px solid var(--p-color-border-subdued,#eef0f4); transition:background .1s; }
.bext-row:first-child { border-top:0; }
.bext-row:hover { background:var(--p-color-bg-surface-hover,#f6f7fa); }
.bext-grow { flex:1; min-width:0; }
.bext-switch:focus-visible { outline:2px solid var(--p-color-border-focus,#5b5bd6); outline-offset:2px; border-radius:999px; }
.bext-summary { display:flex; align-items:center; gap:16px; margin-top:7px; }
.bext-summary .k { display:inline-flex; align-items:center; gap:7px; font-size:13px; color:var(--p-color-text-secondary,#5b6472); }
.bext-summary .k b { color:var(--p-color-text,#14161c); font-weight:640; font-variant-numeric:tabular-nums; }
.bext-summary .d { width:7px; height:7px; border-radius:50%; }
/* unified favicon tile — every logo sits on the same white rounded surface */
.bext-tile { width:38px; height:38px; border-radius:10px; flex:0 0 auto; background:var(--p-color-bg-surface,#fff);
  border:1px solid var(--p-color-border,#e9ebf1); display:grid; place-items:center; box-shadow:0 1px 2px rgba(17,20,28,.06); overflow:hidden; }
.bext-tile.sm { width:30px; height:30px; border-radius:8px; }
.bext-sk { height:16px; border-radius:6px; background:linear-gradient(90deg,var(--p-color-bg-surface-secondary,#f3f5f8) 25%,var(--p-color-bg-surface-hover,#f6f7fa) 37%,var(--p-color-bg-surface-secondary,#f3f5f8) 63%); background-size:400% 100%; animation:bextsh 1.3s ease infinite; }
@keyframes bextsh { 0%{background-position:100% 0} 100%{background-position:-100% 0} }
@media (prefers-reduced-motion:reduce){ .bext-sk{ animation:none } }
.bext-eyebrow { font-size:11px; font-weight:660; letter-spacing:.09em; text-transform:uppercase;
  color:var(--p-color-text-secondary,#9aa2b1); }
.bext-pick { display:flex; align-items:center; gap:13px; padding:10px 10px; border-radius:12px; cursor:pointer; border:1px solid transparent; transition:background .1s; }
.bext-pick:hover { background:var(--p-color-bg-surface-hover,#f6f7fa); }
.bext-pick.sel { background:var(--p-color-bg-surface-selected,#f1f1fd); border-color:var(--p-color-border-emphasis,#cfcef6); }
.bext-chk { width:22px; height:22px; border-radius:7px; border:1.5px solid var(--p-color-border-strong,#dfe2ea); flex:0 0 auto; display:grid; place-items:center; color:#fff; font-size:12px; transition:.12s; }
.bext-pick.sel .bext-chk { background:var(--p-color-bg-fill-brand,#5b5bd6); border-color:var(--p-color-bg-fill-brand,#5b5bd6); }
/* custom-host form: force the product's default text color (base Polaris Text/labels inherit a themed
   green in this modal); keep subdued/critical variants intact. */
.bext-form .Polaris-Choice__Label,
.bext-form .Polaris-Text--root:not(.Polaris-Text--subdued):not(.Polaris-Text--critical) { color: var(--text-default, #202223); }
/* premium light segmented control (replaces the heavy dark block) */
.bext-seg { display:inline-flex; background:var(--p-color-bg-surface-secondary,#f3f5f8); border:1px solid var(--p-color-border-subdued,#eef0f4); border-radius:11px; padding:4px; gap:2px; }
.bext-seg button { border:0; background:none; font:inherit; font-size:13px; font-weight:560; padding:7px 18px; border-radius:8px; color:var(--p-color-text-secondary,#5b6472); cursor:pointer; transition:.13s; }
.bext-seg button.on { background:var(--p-color-bg-surface,#fff); color:var(--p-color-text,#14161c); box-shadow:0 1px 2px rgba(17,20,28,.10); }
`;

// Lightweight, theme-aware toggle (Polaris ships no native switch in this version).
function Switch({ active, onChange, title }) {
    return (
        <div
            className="bext-switch"
            role="switch" aria-checked={active} aria-label={title} tabIndex={0} title={title}
            onClick={onChange}
            onKeyDown={(e) => { if (e.key === "Enter" || e.key === " ") { e.preventDefault(); onChange(); } }}
            style={{
                width: 30, height: 18, borderRadius: 999, cursor: "pointer", position: "relative", flex: "0 0 auto",
                transition: "background .18s ease",
                background: active ? "var(--p-color-bg-fill-brand, #5a5ad6)" : "var(--p-color-bg-fill-tertiary, #e6e8ee)",
                boxShadow: active ? "none" : "inset 0 0 0 1px var(--p-color-border, #e0e2e8)",
            }}
        >
            <div style={{
                position: "absolute", top: 3, left: active ? 15 : 3, width: 12, height: 12, borderRadius: "50%",
                background: "#fff", boxShadow: "0 1px 2px rgba(17,20,28,.28)", transition: "left .18s ease",
            }} />
        </div>
    );
}

// Prefer a stored icon_url; otherwise derive the site's real favicon from its host (host == domain).
// Avatar falls back to the host's initials if the favicon fails to load.
function faviconFor(host, iconUrl) {
    if (iconUrl) return iconUrl;
    return host ? sharedIconCacheService.getFaviconUrl(host) : undefined;
}

// Every favicon sits inside a unified white rounded tile so light/dark logos read evenly.
function hostAvatar(host, iconUrl, size = "small") {
    const sm = size === "extraSmall";
    return (
        <span className={`bext-tile ${sm ? "sm" : ""}`}>
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

    // unified "Add hosts" picker
    const [pickerOpen, setPickerOpen] = useState(false);
    const [mode, setMode] = useState("akto");          // "akto" | "custom"
    const [pickSearch, setPickSearch] = useState("");
    const [pickVisible, setPickVisible] = useState(40);   // how many catalogue rows are rendered (grows on scroll)
    const [selected, setSelected] = useState(() => new Set());   // hosts checked for bulk add
    const [adding, setAdding] = useState(false);
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
    useEffect(() => { setPickVisible(40); }, [pickSearch]);   // reset picker window on a new search

    const catalogueByHost = useMemo(() => {
        const map = {};
        catalogue.forEach((c) => { map[(c.host || "").toLowerCase()] = c; });
        return map;
    }, [catalogue]);

    const configuredHostSet = useMemo(
        () => new Set(configured.map((c) => (c.host || "").toLowerCase())),
        [configured]
    );

    const activeCount = useMemo(() => configured.filter((c) => c.active).length, [configured]);
    const offCount = configured.length - activeCount;

    // sort by the Mongo id (hexId) ascending — top brands were given the oldest ids, so they lead
    const idOf = (c) => c?.hexId || "￿";
    const byId = (a, b) => idOf(a).localeCompare(idOf(b));

    const visibleConfigured = useMemo(() => {
        const q = cfgFilter.trim().toLowerCase();
        const list = q ? configured.filter((c) => (c.host || "").toLowerCase().includes(q)) : configured.slice();
        // top brands follow the defined catalogue rank (ChatGPT, Claude, DeepSeek, Gemini, …)
        return list.sort((a, b) => {
            const ca = catalogueByHost[(a.host || "").toLowerCase()];
            const cb = catalogueByHost[(b.host || "").toLowerCase()];
            return idOf(ca).localeCompare(idOf(cb));
        });
    }, [configured, cfgFilter, catalogueByHost]);

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

    // ── picker (add / edit) ─────────────────────────────────────────────
    const openPicker = (m = "akto") => {
        setMode(m); setPickSearch(""); setPickVisible(40); setSelected(new Set()); setForm(EMPTY_FORM);
        setEditingHexId(null); setFormErrors({}); setShowAdv(false); setShowExample(false); setPickerOpen(true);
    };
    const toggleSelected = (host) => {
        setSelected((prev) => {
            const next = new Set(prev);
            next.has(host) ? next.delete(host) : next.add(host);
            return next;
        });
    };
    const addSelected = async () => {
        const hosts = [...selected];
        if (hosts.length === 0) return;
        setAdding(true);
        try {
            for (const host of hosts) {
                await api.setBrowserExtensionConfigActive(host, true);
            }
            func.setToast(true, false, `${hosts.length} host${hosts.length !== 1 ? "s" : ""} added`);
            setSelected(new Set());
            await fetchAll();
        } catch (error) {
            func.setToast(true, true, "Failed to add hosts");
        } finally {
            setAdding(false);
        }
    };
    const openEdit = (config) => {
        setMode("custom");
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
        setPickerOpen(false); setForm(EMPTY_FORM); setEditingHexId(null); setFormErrors({}); setShowAdv(false); setShowExample(false);
    };
    // form helpers for the repeatable array fields
    const setField = (k, v) => setForm((f) => ({ ...f, [k]: v }));
    const setArrItem = (k, i, v) => setForm((f) => ({ ...f, [k]: f[k].map((x, j) => (j === i ? v : x)) }));
    const addArrItem = (k, blank = "") => setForm((f) => ({ ...f, [k]: [...f[k], blank] }));
    const rmArrItem = (k, i) => setForm((f) => ({ ...f, [k]: f[k].length > 1 ? f[k].filter((_, j) => j !== i) : f[k] }));
    const setFmItem = (i, key, val) => setForm((f) => ({ ...f, frameMatch: f.frameMatch.map((x, j) => (j === i ? { ...x, [key]: val } : x)) }));

    // renders a labelled list of monospaced text inputs with add/remove (paths, prompt paths, operations)
    const renderRepeatable = (key, { label, sub, placeholder, helpText, addLabel, error }) => (
        <VerticalStack gap="1">
            <Text variant="bodyMd" fontWeight="semibold">
                {label}{sub && <Text as="span" color="subdued"> {sub}</Text>}
            </Text>
            <VerticalStack gap="2">
                {form[key].map((val, i) => (
                    <HorizontalStack key={i} gap="2" wrap={false} blockAlign="center">
                        <div style={{ flex: 1, minWidth: 0 }}>
                            <TextField labelHidden label={`${label} ${i + 1}`} value={val}
                                onChange={(v) => setArrItem(key, i, v)} placeholder={placeholder} autoComplete="off" monospaced />
                        </div>
                        {form[key].length > 1 && (
                            <Button plain icon={DeleteMinor} accessibilityLabel="Remove" onClick={() => rmArrItem(key, i)} />
                        )}
                    </HorizontalStack>
                ))}
            </VerticalStack>
            <div><Button plain onClick={() => addArrItem(key)}>{addLabel}</Button></div>
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
        if (configured.length === 0) {
            func.setToast(true, true, "No configured hosts to download");
            return;
        }
        const rows = configured.map((c) => ({
            Host: c.host || "-",
            Source: catalogueByHost[(c.host || "").toLowerCase()] ? "Akto" : "Custom",
            Status: c.active ? "Active" : "Inactive",
            Paths: (c.paths || []).join(" & ") || "-",
        }));
        func.downloadAsCSV(rows, { name: "browser_extension_configs" });
    };

    // ── configured section ──────────────────────────────────────────────
    const skeletonList = (
        <div className="bext-list">
            {Array.from({ length: 5 }).map((_, i) => (
                <div className="bext-row" key={`sk-${i}`}>
                    <div className="bext-sk" style={{ width: 36, height: 36, borderRadius: 9 }} />
                    <div className="bext-grow">
                        <div className="bext-sk" style={{ width: 130 }} />
                        <div className="bext-sk" style={{ width: 80, height: 11, marginTop: 7 }} />
                    </div>
                    <div className="bext-sk" style={{ width: 70, height: 14 }} />
                </div>
            ))}
        </div>
    );

    const configuredRow = (c) => {
        const common = catalogueByHost[(c.host || "").toLowerCase()];
        const isAkto = !!common;
        const menuItems = [
            ...(!isAkto ? [{ content: "Edit", onAction: () => { setOpenMenuId(null); openEdit(c); } }] : []),
            { content: "Remove", destructive: true, onAction: () => { setOpenMenuId(null); removeConfigured(c.host, c.hexId); } },
        ];
        return (
            <div className="bext-row" key={c.hexId} style={{ opacity: c.active ? 1 : 0.6 }}>
                {hostAvatar(c.host, common?.iconUrl)}
                <div className="bext-grow">
                    <Text variant="bodyMd" fontWeight="medium" truncate>{common?.name || c.host}</Text>
                    <Text variant="bodySm" color="subdued" truncate>
                        {common?.name ? c.host : (isAkto ? "Akto" : ((c.paths || []).join(", ") || "Custom"))}
                    </Text>
                </div>
                <Switch active={c.active} onChange={() => toggleConfigured(c.host, !c.active)}
                    title={c.active ? "Disable" : "Enable"} />
                <Popover
                    active={openMenuId === c.hexId}
                    onClose={() => setOpenMenuId(null)}
                    preferredAlignment="right"
                    activator={
                        <Button
                            plain
                            icon={HorizontalDotsMinor}
                            accessibilityLabel={`Actions for ${common?.name || c.host}`}
                            onClick={() => setOpenMenuId(openMenuId === c.hexId ? null : c.hexId)}
                        />
                    }
                >
                    <ActionList actionRole="menuitem" items={menuItems} />
                </Popover>
            </div>
        );
    };

    const configuredSection = (
        <div key="ext-configured">
            <HorizontalStack align="space-between" blockAlign="start">
                <div>
                    <span className="bext-eyebrow">Configured</span>
                    {!loading && configured.length > 0 && (
                        <div className="bext-summary">
                            <span className="k"><b>{configured.length}</b> host{configured.length !== 1 ? "s" : ""}</span>
                            <span className="k"><span className="d" style={{ background: "var(--p-color-bg-fill-success, #1f9d55)" }} /><b>{activeCount}</b> active</span>
                            {offCount > 0 && <span className="k"><span className="d" style={{ background: "var(--p-color-icon-disabled, #98a1b0)" }} /><b>{offCount}</b> off</span>}
                        </div>
                    )}
                </div>
                {!loading && configured.length > 0 && (
                    <div style={{ width: 230 }}>
                        <TextField
                            labelHidden label="Filter configured" value={cfgFilter} onChange={setCfgFilter}
                            placeholder="Filter configured…" prefix={<Icon source={SearchMinor} color="subdued" />}
                            autoComplete="off" clearButton onClearButtonClick={() => setCfgFilter("")}
                        />
                    </div>
                )}
            </HorizontalStack>
            <Box paddingBlockStart="3">
                {loading ? skeletonList : configured.length === 0 ? (
                    <Box background="bg-surface" borderColor="border" borderWidth="1" borderRadius="300" padding="10">
                        <VerticalStack gap="4" inlineAlign="center">
                            <Box background="bg-subdued" borderRadius="300" padding="3">
                                <Icon source={SearchMinor} color="subdued" />
                            </Box>
                            <VerticalStack gap="1" inlineAlign="center">
                                <Text variant="headingSm">No hosts configured yet</Text>
                                <Text alignment="center" color="subdued">
                                    Add from Akto's supported catalogue below, or add your own custom host.
                                </Text>
                            </VerticalStack>
                            <Button primary onClick={() => openPicker("akto")}>Add hosts</Button>
                        </VerticalStack>
                    </Box>
                ) : (
                    <VerticalStack gap="3">
                        <div className="bext-list">
                            {visibleConfigured.length === 0
                                ? <div className="bext-row"><Text color="subdued">No configured hosts match “{cfgFilter}”.</Text></div>
                                : pagedConfigured.map(configuredRow)}
                        </div>
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
        </div>
    );

    // ── picker modal ────────────────────────────────────────────────────
    // Catalogue minus already-configured hosts (added ones aren't shown), sorted top-first.
    const availableCatalogue = catalogue.filter((c) => !configuredHostSet.has((c.host || "").toLowerCase()));
    const pickQuery = pickSearch.trim().toLowerCase();
    const pickFilteredAll = availableCatalogue
        .filter((c) => !pickQuery || (c.host || "").toLowerCase().includes(pickQuery) || (c.name || "").toLowerCase().includes(pickQuery))
        .slice()
        .sort(byId);
    // render a growing window so the list scrolls smoothly through everything
    const pickFiltered = pickFilteredAll.slice(0, pickVisible);

    const pickRow = (c) => {
        const isSel = selected.has(c.host);
        return (
            <div
                className={`bext-pick ${isSel ? "sel" : ""}`} key={c.host}
                role="checkbox" aria-checked={isSel} tabIndex={0}
                onClick={() => toggleSelected(c.host)}
                onKeyDown={(e) => { if (e.key === "Enter" || e.key === " ") { e.preventDefault(); toggleSelected(c.host); } }}
            >
                <div className="bext-chk">{isSel ? "✓" : ""}</div>
                {hostAvatar(c.host, c.iconUrl, "small")}
                <div style={{ flex: 1, minWidth: 0 }}>
                    <Text variant="bodyMd" fontWeight="medium" truncate>{c.name || c.host}</Text>
                    {c.name && <Text variant="bodySm" color="subdued" truncate>{c.host}</Text>}
                </div>
            </div>
        );
    };

    const pickerModal = (
        <Modal
            key="ext-picker" open={pickerOpen} onClose={closePicker}
            title={editingHexId ? "Edit host" : "Add hosts"}
            primaryAction={mode === "custom"
                ? { content: editingHexId ? "Save" : "Add config", onAction: saveCustom, loading: saving }
                : { content: selected.size ? `Add ${selected.size}` : "Add", onAction: addSelected, disabled: selected.size === 0, loading: adding }}
            secondaryActions={[{ content: mode === "custom" ? "Cancel" : "Done", onAction: closePicker }]}
        >
            {!editingHexId && (
                <Modal.Section>
                    <div className="bext-seg">
                        <button className={mode === "akto" ? "on" : ""} onClick={() => setMode("akto")}>Akto catalogue</button>
                        <button className={mode === "custom" ? "on" : ""} onClick={() => setMode("custom")}>Custom host</button>
                    </div>
                </Modal.Section>
            )}
            <Modal.Section>
                {mode === "akto" ? (
                    <VerticalStack gap="3">
                        <TextField
                            labelHidden label="Search supported hosts" value={pickSearch} onChange={setPickSearch}
                            placeholder="Search supported hosts…" prefix={<Icon source={SearchMinor} color="subdued" />}
                            autoComplete="off" clearButton onClearButtonClick={() => setPickSearch("")}
                        />
                        <HorizontalStack align="end" blockAlign="center">
                            <Text variant="bodySm" color="subdued">
                                {pickQuery
                                    ? `${pickFilteredAll.length} ${pickFilteredAll.length === 1 ? "result" : "results"}`
                                    : `${pickFilteredAll.length.toLocaleString()} supported hosts`}
                            </Text>
                        </HorizontalStack>
                        {pickFiltered.length === 0 ? (
                            <Box padding="6">
                                <VerticalStack gap="3" inlineAlign="center">
                                    <Text alignment="center" color="subdued">No supported host matches “{pickSearch.trim()}”.</Text>
                                    {pickQuery && (
                                        <Button onClick={() => { setForm({ ...EMPTY_FORM, host: pickSearch.trim() }); setFormErrors({}); setMode("custom"); }}>
                                            {`＋ Add “${pickSearch.trim()}” as a custom host`}
                                        </Button>
                                    )}
                                </VerticalStack>
                            </Box>
                        ) : (
                            <div
                                style={{ maxHeight: "44vh", overflowY: "auto", margin: "0 -4px" }}
                                onScroll={(e) => {
                                    const el = e.currentTarget;
                                    if (el.scrollHeight - el.scrollTop - el.clientHeight < 160) {
                                        setPickVisible((v) => (v < pickFilteredAll.length ? v + 40 : v));
                                    }
                                }}
                            >
                                {pickFiltered.map(pickRow)}
                            </div>
                        )}
                    </VerticalStack>
                ) : (
                    <div className="form-class bext-form">
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
                                placeholder="chat.example.com" error={formErrors.host} autoComplete="off" monospaced
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
                                    <div style={{ flex: 1 }}>
                                        <Dropdown id="bext-method" label="Method"
                                            menuItems={["POST", "GET", "PUT", "PATCH"].map((m) => ({ label: m, value: m }))}
                                            initial={form.method} selected={(v) => setField("method", v)} />
                                    </div>
                                    <div style={{ flex: 1 }}>
                                        <Dropdown id="bext-format-http" label="Body format"
                                            menuItems={FORMAT_OPTIONS.http.map(([v, l]) => ({ label: l, value: v }))}
                                            initial={form.format} selected={(v) => setField("format", v)} />
                                    </div>
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
                                                    <div style={{ flex: 1, minWidth: 0 }}>
                                                        <TextField labelHidden label={`key ${i}`} value={row.k} onChange={(v) => setFmItem(i, "k", v)} placeholder="event" autoComplete="off" monospaced />
                                                    </div>
                                                    <Text>=</Text>
                                                    <div style={{ flex: 1, minWidth: 0 }}>
                                                        <TextField labelHidden label={`value ${i}`} value={row.v} onChange={(v) => setFmItem(i, "v", v)} placeholder="send" autoComplete="off" monospaced />
                                                    </div>
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
                                        placeholder="choices[*].delta.content" autoComplete="off" monospaced helpText="Where the AI answer is in the response." />
                                    <TextField label="Model path" value={form.modelPath} onChange={(v) => setField("modelPath", v)}
                                        placeholder="model" autoComplete="off" monospaced helpText="Where the model name is." />
                                </VerticalStack>
                            )}
                        </VerticalStack>
                    </Form>
                    </div>
                )}
            </Modal.Section>
        </Modal>
    );

    return (
        <>
            <style>{CSS}</style>
            <PageWithMultipleCards
                title={"Browser Extension"}
                subtitle={"Choose which hosts the Akto extension inspects. Available in v1.0.61 and later."}
                isFirstPage={true}
                fullWidth={false}
                primaryAction={<Button primary onClick={() => openPicker("akto")}>Add hosts</Button>}
                secondaryActions={<Button onClick={handleDownload} disabled={loading || configured.length === 0}>Download</Button>}
                components={[pickerModal, configuredSection]}
            />
        </>
    );
}

export default BrowserExtensionSettings;
