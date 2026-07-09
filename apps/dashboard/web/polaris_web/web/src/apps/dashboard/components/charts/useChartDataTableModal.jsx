import { useState } from "react";
import { Modal, DataTable } from "@shopify/polaris";

// Highcharts' native "View data table" export renders an unstyled raw <table>.
// This swaps that one menu action for a Polaris Modal + DataTable, shared across all charts.
function useChartDataTableModal(title) {
    const [rows, setRows] = useState([]);
    const [open, setOpen] = useState(false);

    const menuItemDefinitions = {
        viewData: {
            onclick: function () {
                setRows(this.getDataRows());
                setOpen(true);
            }
        }
    };

    const [headings, ...bodyRows] = rows;

    const modal = (
        <Modal open={open} onClose={() => setOpen(false)} title={title || "Chart data"} large>
            <Modal.Section>
                <DataTable
                    columnContentTypes={(headings || []).map((_, i) => i === 0 ? 'text' : 'numeric')}
                    headings={headings || []}
                    rows={bodyRows}
                />
            </Modal.Section>
        </Modal>
    );

    return { menuItemDefinitions, modal };
}

export default useChartDataTableModal;
