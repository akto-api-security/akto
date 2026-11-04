import MatrixHeatmap from "@/apps/dashboard/components/shared/MatrixHeatmap";
import DiscoveryCard from "../shared/DiscoveryCard";

// A titled heatmap card: matrix is { columns, rows, max }; empty when there are no rows.
export default function MatrixCard({ title, subtitle, status, matrix, emptyText, format, tipUnit, showScale, scaleLabel, dangerScaleLabel, emptyCellText, labelWidth, cellHeight, minCellWidth }) {
    return (
        <DiscoveryCard center title={title} subtitle={subtitle} status={status} empty={!matrix || matrix.rows.length === 0} emptyText={emptyText}>
            {matrix && matrix.rows.length > 0 && (
                <MatrixHeatmap
                    columns={matrix.columns}
                    rows={matrix.rows}
                    max={matrix.max}
                    format={format}
                    tipUnit={tipUnit}
                    showScale={showScale}
                    scaleLabel={scaleLabel}
                    dangerScaleLabel={dangerScaleLabel}
                    emptyText={emptyCellText}
                    labelWidth={labelWidth}
                    cellHeight={cellHeight}
                    minCellWidth={minCellWidth}
                />
            )}
        </DiscoveryCard>
    );
}
