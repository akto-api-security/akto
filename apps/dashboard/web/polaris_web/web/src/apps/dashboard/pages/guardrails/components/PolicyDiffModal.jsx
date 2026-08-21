import { useMemo, useRef } from "react";

import {
    Modal,
    Box,
    Text,
    Badge,
    Button,
    ButtonGroup,
    HorizontalStack,
    VerticalStack
} from "@shopify/polaris";

import AgGridTable from "../../../components/tables/AgGridTable";
import { REPLAY_SOURCE_TABS } from "../utils";

/** Verdict of the saved policy on one replayed prompt. */
const SavedVerdictCellRenderer = ({ data }) => (
    <Text variant="bodySm" color={data.wasDetected ? "success" : "subdued"} fontWeight="medium">
        {data.wasDetected ? "Detected" : "Not detected"}
    </Text>
);

/** Verdict of the unsaved draft, so a lost detection reads as critical rather than merely absent. */
const DraftVerdictCellRenderer = ({ data }) => {
    const lost = data.wasDetected && !data.nowDetected;
    return (
        <Text variant="bodySm" color={data.nowDetected ? "success" : lost ? "critical" : "subdued"}
            fontWeight="medium">
            {data.nowDetected ? "Detected" : lost ? "Missed" : "Not detected"}
        </Text>
    );
};

/*
 * Rule attribution is not available yet: replayWithPolicy returns only
 * {id, detected, baselineDetected, skipReason}, with no matched rule ids. Restore this renderer and
 * its column below once the guardrails service sends them.
 *
 * const RuleCellRenderer = ({ data }) => (
 *     <VerticalStack gap="0">
 *         <Text variant="bodySm">{data.ruleName}</Text>
 *         <Text variant="bodySm" color="subdued">{data.ruleKind}</Text>
 *     </VerticalStack>
 * );
 */

// autoHeight rows take no line-height from the theme, so wrapped text sits flush against the row
// border. Set both explicitly, and use the same padding on every column so rows line up.
const CELL_STYLE = { lineHeight: "18px", paddingTop: "10px", paddingBottom: "10px" };

const PROMPT_COL = {
    field: "prompt", headerName: "Prompt", flex: 1, minWidth: 320,
    // Prompts run long; wrap rather than truncate, since recognising the prompt is the point.
    wrapText: true, autoHeight: true, sortable: false, filter: false,
    cellStyle: { ...CELL_STYLE, whiteSpace: "normal" }
};

const SAVED_COL = {
    field: "wasDetected", headerName: "Saved", width: 130, filter: false,
    cellRenderer: SavedVerdictCellRenderer, cellStyle: CELL_STYLE
};

const DRAFT_COL = {
    field: "nowDetected", headerName: "Draft", width: 130, filter: false,
    cellRenderer: DraftVerdictCellRenderer, cellStyle: CELL_STYLE
};

// A new policy has no saved verdict to sit beside, so that column is dropped entirely.
const COL_DEFS = [PROMPT_COL, SAVED_COL, DRAFT_COL];
const NEW_POLICY_COL_DEFS = [PROMPT_COL, { ...DRAFT_COL, headerName: "This policy" }];
// {
//     field: "ruleName", headerName: "Rule that changed", width: 210,
//     cellRenderer: RuleCellRenderer, cellStyle: CELL_STYLE
// }

const DEFAULT_COL_DEF = { resizable: true, sortable: true };

// Fixed so the table scrolls inside itself rather than growing the modal.
const TABLE_HEIGHT = 380;

// Tint the whole row by direction of the change. Uses Polaris' own custom properties rather than
// new hex, so the colours track the theme; AG Grid owns the row element, so a style object it is.
const getRowStyle = ({ data }) => {
    if (!data) return undefined;
    return data.wasDetected
        ? { background: "var(--p-color-bg-critical-subdued)" }
        : { background: "var(--p-color-bg-success-subdued)" };
};

/**
 * Full-width view of a policy-edit replay: every prompt whose verdict moved, with the saved
 * policy's verdict beside the draft's.
 *
 * Only changed rows appear: unchanged ones are the bulk of any sample and say nothing about the
 * edit. The two totals above still count the whole sample.
 */
const PolicyDiffModal = ({ open, onClose, source, onSourceChange, result, rows, isNewPolicy = false }) => {
    const gridRef = useRef(null);

    const current = result?.currentDetected || 0;
    const modified = result?.modifiedDetected || 0;
    const delta = modified - current;
    const compared = result?.compared || 0;
    const unit = source === "TRACES" ? "requests" : "violations";
    // Names the sample, so the totals do not read as a benchmark against someone else's traffic.
    const sampleNote = `Measured against ${compared} of your own recent agent ${unit} from the last 30 days.`;

    const changedRows = useMemo(() => rows.filter(r => r.wasDetected !== r.nowDetected), [rows]);
    // const gained = changedRows.filter(r => r.nowDetected).length;

    const exportCsv = () => {
        gridRef.current?.api?.exportDataAsCsv({
            fileName: `${isNewPolicy ? "impact" : "change-impact"}-analysis-${source.toLowerCase()}.csv`
        });
    };

    return (
        <Modal
            large
            open={open}
            onClose={onClose}
            title={isNewPolicy ? "Impact analysis" : "Change impact analysis"}
            primaryAction={{ content: "Export CSV", onAction: exportCsv, disabled: changedRows.length === 0 }}
            secondaryActions={[{ content: "Close", onAction: onClose }]}
        >
            <Modal.Section>
                <VerticalStack gap="4">
                    {!isNewPolicy && <ButtonGroup segmented>
                        {REPLAY_SOURCE_TABS.map(tab => (
                            <Button key={tab.id} size="slim" pressed={source === tab.id}
                                onClick={() => onSourceChange(tab.id)}>
                                {tab.content}
                            </Button>
                        ))}
                    </ButtonGroup>}

                    {/* Deltas sit with the draft number they describe, not adrift at the row's end. */}
                    <HorizontalStack gap="8" blockAlign="start">
                        {!isNewPolicy && (
                            <VerticalStack gap="0">
                                <Text variant="bodySm" color="subdued">Saved policy</Text>
                                <Text variant="headingLg" as="p">{current}</Text>
                            </VerticalStack>
                        )}
                        <VerticalStack gap="1">
                            <Text variant="bodySm" color="subdued">
                                {isNewPolicy ? "Would be caught" : "Your changes"}
                            </Text>
                            <HorizontalStack gap="2" blockAlign="center">
                                <Text variant="headingLg" as="p">{modified}</Text>
                                {isNewPolicy && compared > 0 && (
                                    <Text variant="bodySm" color="subdued">{`of ${compared} compared`}</Text>
                                )}
                                {!isNewPolicy && (
                                    <Badge status={delta > 0 ? "success" : delta < 0 ? "critical" : "info"}>
                                        {delta === 0 ? "no change" : `${delta > 0 ? "+" : ""}${delta}`}
                                    </Badge>
                                )}
                                {/* Needs the full per-item verdict list from the replay action,
                                    which today returns counters plus missedByDraft only.
                                {gained > 0 && <Badge status="success">{`+${gained} newly caught`}</Badge>} */}
                            </HorizontalStack>
                        </VerticalStack>
                    </HorizontalStack>

                    {isNewPolicy && compared > 0 && (
                        <Text variant="bodySm" color="subdued">{sampleNote}</Text>
                    )}

                    <VerticalStack gap="2">
                        {/* Box owns the border and radius; AgGridTable draws neither, so the two
                            don't stack into a doubled corner. overflow clips the header to it. */}
                        <Box borderRadius="2" borderWidth="1" borderColor="border"
                            overflowX="hidden" overflowY="hidden">
                            <AgGridTable
                                gridRef={gridRef}
                                rowData={changedRows}
                                columnDefs={isNewPolicy ? NEW_POLICY_COL_DEFS : COL_DEFS}
                                defaultColDef={DEFAULT_COL_DEF}
                                getRowStyle={getRowStyle}
                                rowSelection="single"
                                sideBar={false}
                                noOuterBorder
                                domLayout="normal"
                                height={TABLE_HEIGHT}
                                pagination
                                paginationPageSize={10}
                                paginationPageSizeSelector={[10, 20, 50]}
                                filterStateUrl="/guardrails/policy-diff"
                                suppressRowHoverHighlight
                            />
                        </Box>

                        {changedRows.length === 0 && (
                            <Text variant="bodySm" color="subdued">
                                {isNewPolicy
                                    ? `No ${unit} would be caught in this sample.`
                                    : `No ${unit} changed verdict in this sample.`}
                            </Text>
                        )}
                    </VerticalStack>
                </VerticalStack>
            </Modal.Section>
        </Modal>
    );
};

export default PolicyDiffModal;
