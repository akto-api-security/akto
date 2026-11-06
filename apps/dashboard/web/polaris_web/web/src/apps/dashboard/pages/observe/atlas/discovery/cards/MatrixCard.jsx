import MatrixHeatmap from "@/apps/dashboard/components/shared/MatrixHeatmap";
import DiscoveryCard from "../shared/DiscoveryCard";

// A titled heatmap card: matrix is { columns, rows, max }; emptyCellText and the other props go to MatrixHeatmap.
export default function MatrixCard({ title, subtitle, status, matrix, emptyText, emptyCellText, ...heatmapProps }) {
    const empty = !matrix || matrix.rows.length === 0;
    return (
        <DiscoveryCard center title={title} subtitle={subtitle} status={status} empty={empty} emptyText={emptyText}>
            {!empty && <MatrixHeatmap columns={matrix.columns} rows={matrix.rows} max={matrix.max} emptyText={emptyCellText} {...heatmapProps} />}
        </DiscoveryCard>
    );
}
