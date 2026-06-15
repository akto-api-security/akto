import { Modal } from "@shopify/polaris";
import SpansPanel from "./SpansPanel";

export default function SpansPanelModal({ open, onClose, traceId }) {
    if (!open) return null;
    return (
        <Modal open={open} onClose={onClose} title="Trace Spans" large>
            <Modal.Section>
                <SpansPanel traceId={traceId} />
            </Modal.Section>
        </Modal>
    );
}
