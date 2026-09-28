import { Icon, Text } from "@shopify/polaris"
import { CircleAlertMajor, CircleTickMajor, ExternalMinor } from "@shopify/polaris-icons"
import MarkdownViewer from "@/apps/dashboard/components/shared/MarkdownViewer"

// One "Suggested actions" row — design_handoff_ask_akto_overlay/README.md, "5. Suggested
// actions". Two kinds:
//
// - `nav`: a plain destination chip. Clicking it closes the popup and navigates — no preview,
//   per the design ("Navigation actions close the popup and route to the page, with no
//   preview").
// - `write`: the two-phase MCP write-tool contract, made visible. There is no structured
//   "the model wants to write something" signal on the wire (/api/chatAndStore returns plain
//   text — see ChatMessage.jsx's own header comment) — so the row's preview IS the assistant's
//   own dry-run message, shown verbatim, and Confirm re-sends a fixed confirmation phrase over
//   the SAME conversation. The model still never confirms on its own: only this button click
//   sends it. `doneText` is the model's real reply to that confirmation, which — per the
//   write-tool prompt contract — is required to state the actual re-read count, never a generic
//   "success".
export default function ActionRow({ kind, label, desc, state, previewText, doneText, onReview, onCancel, onConfirm, onUndo, onOpenNav }) {
    const iconTile = kind === "write" ? "ask-action-icon-write" : "ask-action-icon-nav"

    return (
        <div className="ask-action-row">
            <div className="ask-action-row-head">
                <span className={`ask-action-icon ${iconTile}`}>
                    <Icon source={kind === "write" ? CircleAlertMajor : ExternalMinor} />
                </span>
                <span className="ask-result-label">
                    <Text variant="bodyMd" fontWeight="medium" as="span">{label}</Text>
                    <Text variant="bodySm" color="subdued" as="span">{desc}</Text>
                </span>

                {kind === "nav" ? (
                    <button type="button" className="ask-action-btn" onClick={onOpenNav}>Open</button>
                ) : state === "idle" ? (
                    <button type="button" className="ask-action-btn" onClick={onReview}>Review</button>
                ) : state === "running" ? (
                    <span className="ask-action-running"><span className="ask-spinner" />Applying</span>
                ) : state === "done" ? (
                    <span className="ask-action-done"><Icon source={CircleTickMajor} />Done</span>
                ) : null}
            </div>

            {kind === "write" && state === "confirm" ? (
                <div className="ask-action-preview">
                    <Text variant="bodySm" fontWeight="semibold" color="subdued" as="p">Preview — nothing has changed yet</Text>
                    <div className="ask-action-preview-body"><MarkdownViewer markdown={previewText || ""} noPadding /></div>
                    <div className="ask-action-preview-buttons">
                        <button type="button" className="ask-action-btn" onClick={onCancel}>Cancel</button>
                        <button type="button" className="ask-action-confirm-btn" onClick={onConfirm}>Confirm</button>
                    </div>
                </div>
            ) : null}

            {kind === "write" && state === "done" ? (
                <div className="ask-action-done-panel">
                    <span className="ask-spacer"><MarkdownViewer markdown={doneText || ""} noPadding /></span>
                    <button type="button" className="ask-action-btn" onClick={onUndo}>Undo</button>
                </div>
            ) : null}
        </div>
    )
}
