import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { Box, Button, Collapsible, HorizontalStack, Icon, Spinner, Text, VerticalStack } from "@shopify/polaris";
import { ChevronDownMinor, ChevronRightMinor } from "@shopify/polaris-icons";
import api from "../api";
import FlyLayout from "../../../components/layouts/FlyLayout";
import GetPrettifyEndpoint from "../../observe/GetPrettifyEndpoint";
import AgenticUserMessage from "../../agentic/components/AgenticUserMessage";
import LocalStore from "../../../../main/LocalStorageStore";
import {
    IDLE_POLL_MS, LIVE_POLL_MS, attemptResult, conversation, isRunning, mergeEvents, nextCursor, repeatsHeader,
    responseSummary, shortRequest, statusBadge, testName, totals,
} from "./smartTestingLiveUtils";

const MONO = { fontFamily: "SFMono-Regular, Consolas, 'Liberation Mono', Menlo, monospace", fontSize: "12.5px", lineHeight: 1.55, whiteSpace: "pre-wrap", overflowWrap: "anywhere", margin: 0 };
const PROSE = { fontSize: "14px", lineHeight: 1.6, margin: 0, overflowWrap: "anywhere" };
const NEAR_BOTTOM_PX = 120;
const TONE_COLOR = { critical: "#d72c0d", success: "#008060", info: "#2c6ecb", subdued: "#6d7175", warning: "#b98900" };
const SMALL = { fontSize: "12.5px", lineHeight: 1.4, whiteSpace: "nowrap" };

// Text that arrived while watching is typed in quickly, as a chat reply would be; text already there shows at once.
function useReveal(text, animate) {
    const [shown, setShown] = useState(animate ? "" : text);
    useEffect(() => {
        if (!animate) {
            setShown(text);
            return undefined;
        }
        let length = 0;
        let frame = null;
        const step = Math.max(2, Math.ceil(text.length / 45)); // about 0.75 s, whatever the length
        const tick = () => {
            length = Math.min(text.length, length + step);
            setShown(text.slice(0, length));
            if (length < text.length) frame = requestAnimationFrame(tick);
        };
        frame = requestAnimationFrame(tick);
        return () => cancelAnimationFrame(frame);
    }, [text, animate]);
    return shown;
}

function Prose({ text, animate, subdued, small }) {
    const shown = useReveal(text || "", animate);
    return <Text as="p" color={subdued ? "subdued" : undefined}><span style={small ? { ...PROSE, fontSize: "13px" } : PROSE}>{shown}</span></Text>;
}

function Working({ label }) {
    return (
        <HorizontalStack gap="2" blockAlign="center">
            <Spinner size="small" />
            <Text color="subdued">{label}</Text>
        </HorizontalStack>
    );
}

function Note({ block, animate }) {
    return (
        <HorizontalStack gap="2" blockAlign="start" wrap={false}>
            <Box minWidth="6px" paddingBlockStart="2"><div style={{ width: 6, height: 6, borderRadius: 3, background: "#8c9196" }} /></Box>
            <Box width="100%"><Prose text={block.text} animate={animate} subdued={!block.final} small={!block.final} /></Box>
        </HorizontalStack>
    );
}

function RepairBlock({ block, animate, active }) {
    const [open, setOpen] = useState(active);
    useEffect(() => { if (active) setOpen(true); }, [active]);
    return (
        <Box padding="2" paddingInlineStart="3" borderRadius="2" background="bg-subdued">
            <VerticalStack gap="2">
                <Button plain monochrome removeUnderline size="slim" onClick={() => setOpen(!open)}
                    icon={open ? ChevronDownMinor : ChevronRightMinor}>
                    {`Getting the request to work · ${block.steps.length} step${block.steps.length === 1 ? "" : "s"}`}
                </Button>
                <Collapsible open={open} id={`repair-${block.key}`}>
                    <VerticalStack gap="2">
                        {block.steps.map((step, index) => (
                            <Prose key={step.key} text={step.text} subdued small animate={animate && index === block.steps.length - 1} />
                        ))}
                    </VerticalStack>
                </Collapsible>
            </VerticalStack>
        </Box>
    );
}

// A small coloured dot and label: the result of an attempt, or the state of an API.
function Status({ tone, label }) {
    const color = TONE_COLOR[tone] || TONE_COLOR.subdued;
    return (
        <span style={{ ...SMALL, display: "inline-flex", alignItems: "center", gap: 6, color, fontWeight: tone === "critical" ? 600 : 500 }}>
            <span style={{ width: 7, height: 7, borderRadius: "50%", background: color, flexShrink: 0 }} />
            {label}
        </span>
    );
}

function Mono({ children, subdued }) {
    return (
        <Box padding="2" paddingInlineStart="3" paddingInlineEnd="3" borderRadius="2" background="bg-subdued">
            <p style={{ ...MONO, color: subdued ? "#6d7175" : undefined }}>{children}</p>
        </Box>
    );
}

// One attempt: a single line (number, what it tried, result); open it for the request, the response and the checks.
function Attempt({ attempt, animate, startOpen }) {
    const [open, setOpen] = useState(startOpen);
    const [raw, setRaw] = useState(false);
    useEffect(() => { if (startOpen) setOpen(true); }, [startOpen]);
    const result = attemptResult(attempt);
    const { status, message } = responseSummary(attempt.response);
    const vulnerable = attempt.verdict === "vulnerable";
    return (
        <div style={{ borderLeft: `2px solid ${vulnerable ? TONE_COLOR.critical : "#e1e3e5"}`, paddingLeft: 12 }}>
            <div role="button" tabIndex={0} onClick={() => setOpen(!open)} onKeyDown={(e) => (e.key === "Enter" || e.key === " ") && setOpen(!open)}
                style={{ cursor: "pointer", display: "flex", alignItems: "baseline", gap: 10, minWidth: 0 }}>
                <span style={{ ...SMALL, color: TONE_COLOR.subdued, fontWeight: 600 }}>{`#${attempt.number}`}</span>
                <span style={{ ...PROSE, flex: 1, minWidth: 0, overflow: "hidden", textOverflow: "ellipsis", whiteSpace: open ? "normal" : "nowrap" }}>
                    {open ? null : attempt.plan || "Planning the next request…"}
                </span>
                <Status tone={result.tone} label={result.label} />
            </div>
            <Collapsible open={open} id={`attempt-${attempt.key}`}>
                <Box paddingBlockStart="2">
                    <VerticalStack gap="2">
                        {attempt.plan ? <Prose text={attempt.plan} animate={animate} /> : null}
                        {attempt.request ? <Mono>{shortRequest(attempt.request)}</Mono> : null}
                        {attempt.response ? (
                            <VerticalStack gap="1">
                                <Text as="p">
                                    <span style={{ ...PROSE, color: TONE_COLOR.subdued }}>
                                        {status ? <b style={{ color: "#202223" }}>{`HTTP ${status}`}</b> : null}
                                        {status && message ? " · " : null}
                                        {message || (status ? "" : attempt.response.slice(0, 200))}
                                    </span>
                                </Text>
                                <Box><Button plain size="slim" onClick={() => setRaw(!raw)}>{raw ? "Hide raw response" : "Raw response"}</Button></Box>
                                {raw ? <Mono subdued>{attempt.response}</Mono> : null}
                            </VerticalStack>
                        ) : null}
                        {attempt.checks.map((check, index) => <Prose key={index} text={check} subdued />)}
                        {attempt.verdictText ? (
                            <Text as="p"><span style={{ ...PROSE, color: vulnerable ? TONE_COLOR.critical : TONE_COLOR.subdued }}>{attempt.verdictText}</span></Text>
                        ) : null}
                    </VerticalStack>
                </Box>
            </Collapsible>
        </div>
    );
}

function ApiSection({ item, subCategoryMap, open, onToggle, isNew }) {
    const { run } = item;
    const blocks = item.blocks.filter((block) => !repeatsHeader(block));
    const running = isRunning(run);
    const badge = statusBadge(run.status);
    const last = blocks[blocks.length - 1];
    const lastAttempt = [...blocks].reverse().find((block) => block.type === "attempt");
    const counts = [item.attempts ? `${item.attempts} attempt${item.attempts === 1 ? "" : "s"}` : null].filter(Boolean).join("");
    return (
        <Box borderRadius="3" borderWidth="1" borderColor="border-subdued" background="bg">
            <div onClick={onToggle} style={{ cursor: "pointer" }} role="button" tabIndex={0}
                onKeyDown={(e) => (e.key === "Enter" || e.key === " ") && onToggle()}>
                <Box padding="3" paddingInlineStart="3" paddingInlineEnd="4">
                    <div style={{ display: "flex", alignItems: "center", gap: 10, minWidth: 0 }}>
                        <Box><Icon source={open ? ChevronDownMinor : ChevronRightMinor} color="subdued" /></Box>
                        <div style={{ flex: 1, minWidth: 0 }}>
                            <GetPrettifyEndpoint method={run.method} url={run.url} maxWidth="34vw" methodBoxWidth="auto" />
                            <Text variant="bodySm" color="subdued" truncate>{testName(run.testSubType, subCategoryMap)}</Text>
                        </div>
                        <div style={{ display: "flex", alignItems: "center", gap: 14, flexShrink: 0 }}>
                            {counts ? <span style={{ ...SMALL, color: TONE_COLOR.subdued }}>{counts}</span> : null}
                            {item.vulnerable > 0 ? <Status tone="critical" label={`${item.vulnerable} vulnerable`} /> : null}
                            {running ? <Spinner size="small" /> : <Status tone={badge.tone || "subdued"} label={badge.label} />}
                        </div>
                    </div>
                </Box>
            </div>
            <Collapsible open={open} id={`api-${item.key}`}>
                <Box padding="4" paddingBlockStart="1">
                    <VerticalStack gap="3">
                        {blocks.map((block) => {
                            const animate = isNew(block.key);
                            if (block.type === "attempt") {
                                const startOpen = block.verdict === "vulnerable" || (running && block === lastAttempt);
                                return <Attempt key={block.key} attempt={block} animate={animate} startOpen={startOpen} />;
                            }
                            if (block.type === "repair") return <RepairBlock key={block.key} block={block} animate={animate} active={running && block === last} />;
                            if (block.type === "read") return <Note key={block.key} block={{ text: "Read your message" }} animate={false} />;
                            return <Note key={block.key} block={block} animate={animate} />;
                        })}
                        {running ? <Working label={blocks.length ? "Working…" : "Starting…"} /> : null}
                    </VerticalStack>
                </Box>
            </Collapsible>
        </Box>
    );
}

function LivePanel({ summaryHexId }) {
    const [runs, setRuns] = useState([]);
    const [events, setEvents] = useState([]);
    const [messages, setMessages] = useState([]);
    const [loaded, setLoaded] = useState(false);
    const [toggled, setToggled] = useState({}); // API sections opened or closed by hand
    const firstKeys = useRef(null); // blocks already there when the view opened: shown at once, not typed in
    const scroller = useRef(null);
    const nearBottom = useRef(true);
    const subCategoryMap = LocalStore.getState().subCategoryMap;

    const poll = useRef({ cursor: 0, timer: null, cancelled: false });
    const fetchLive = useCallback(async () => {
        const state = poll.current;
        let resp = null;
        try {
            resp = await api.fetchSmartTestingLive(summaryHexId, state.cursor);
        } catch {
            resp = null;
        }
        if (state.cancelled) return;
        if (resp) {
            const fresh = resp.smartTestingEvents || [];
            state.cursor = nextCursor(fresh, state.cursor);
            setRuns(resp.smartTestingRuns || []);
            setEvents((known) => mergeEvents(known, fresh));
            setMessages(resp.smartTestingMessages || []);
            setLoaded(true);
        }
        const anyRunning = (resp?.smartTestingRuns || []).some(isRunning);
        state.timer = setTimeout(fetchLive, anyRunning ? LIVE_POLL_MS : IDLE_POLL_MS);
    }, [summaryHexId]);

    useEffect(() => {
        const state = poll.current;
        state.cancelled = false;
        fetchLive();
        return () => {
            state.cancelled = true;
            clearTimeout(state.timer);
        };
    }, [fetchLive]);

    const items = useMemo(() => conversation(runs, events, messages), [runs, events, messages]);
    const sum = totals(items);

    useEffect(() => {
        if (loaded && firstKeys.current === null) {
            firstKeys.current = new Set(items.flatMap((item) => (item.type === "api" ? item.blocks.map((b) => b.key) : [item.key])));
        }
    }, [loaded, items]);
    const isNew = (key) => firstKeys.current !== null && !firstKeys.current.has(key);

    // follow the conversation, unless the watcher scrolled up to read
    useEffect(() => {
        const el = scroller.current;
        if (el && nearBottom.current) el.scrollTo({ top: el.scrollHeight, behavior: "smooth" });
    }, [items]);
    const onScroll = () => {
        const el = scroller.current;
        if (el) nearBottom.current = el.scrollHeight - el.scrollTop - el.clientHeight < NEAR_BOTTOM_PX;
    };

    const lastApiKey = [...items].reverse().find((item) => item.type === "api")?.key;
    const isOpen = (item) => (item.key in toggled ? toggled[item.key] : isRunning(item.run) || item.key === lastApiKey);

    const summaryLine = sum.apis === 0
        ? "Waiting for the first API to start"
        : [`${sum.apis} API${sum.apis === 1 ? "" : "s"}`, sum.running ? `${sum.running} running` : "none running",
            sum.vulnerable ? `${sum.vulnerable} vulnerable` : null].filter(Boolean).join(" · ");

    return (
        <div style={{ display: "flex", flexDirection: "column", height: "calc(100vh - 120px)" }}>
            <Box paddingBlockEnd="3">
                <HorizontalStack gap="2" blockAlign="center">
                    {sum.running ? <Spinner size="small" /> : null}
                    <Text color="subdued">{summaryLine}</Text>
                </HorizontalStack>
            </Box>
            <div ref={scroller} onScroll={onScroll} style={{ flex: 1, overflowY: "auto", paddingRight: 4 }}>
                <VerticalStack gap="3">
                    {!loaded ? <Working label="Connecting…" /> : null}
                    {loaded && items.length === 0 ? (
                        <Text color="subdued">The agents' steps appear here as soon as smart testing starts on this run's APIs.</Text>
                    ) : null}
                    {items.map((item) => (item.type === "user" ? (
                        <AgenticUserMessage key={item.key} content={item.text} />
                    ) : (
                        <ApiSection key={item.key} item={item} subCategoryMap={subCategoryMap} open={isOpen(item)}
                            onToggle={() => setToggled((known) => ({ ...known, [item.key]: !isOpen(item) }))} isNew={isNew} />
                    )))}
                </VerticalStack>
            </div>
        </div>
    );
}

// The "Live" flyout of a test run: every API's smart-testing agent as one conversation. (Messages to the agents are
// supported end to end — sendSmartTestingRunMessage — and shown here; the input to send them is left out for now.)
function SmartTestingLive({ show, setShow, summaryHexId }) {
    return (
        <FlyLayout
            title="Smart testing · live"
            width="min(1000px, 72vw)"
            show={show}
            setShow={setShow}
            components={show ? [<LivePanel key={summaryHexId} summaryHexId={summaryHexId} />] : []}
        />
    );
}

export default SmartTestingLive;
