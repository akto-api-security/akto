import "./MatrixHeatmap.css";

// Cell colours light to dark, and which steps the legend bar blends between.
const PURPLE_SCALE = { cells: ["#F4EEFF", "#E5D7FF", "#B38CFF", "#8051FF", "#6200EA"], barSteps: [0, 2, 4] };
const RED_SCALE = { cells: ["#FFF4F4", "#FED3D1", "#FD8A7B", "#E0442A", "#B01E00"], barSteps: [1, 2, 4] };
const LIGHT_TEXT_FROM_STEP = 3;
const EMPTY_CELL_TEXT = "–";

// Shade by sqrt of the value, so small values stay visible next to large ones; null for an empty cell.
function shade(value, max, scale) {
    if (!value) return null;
    const step = Math.min(scale.cells.length - 1, Math.floor(Math.sqrt(value / max) * scale.cells.length));
    return { background: scale.cells[step], lightText: step >= LIGHT_TEXT_FROM_STEP };
}

const barVars = (scale) => Object.fromEntries(scale.barSteps.map((step, index) => [`--bar-stop-${index}`, scale.cells[step]]));

// Row x column grid shaded by value; max is the darkest value.
export default function MatrixHeatmap({
    columns, rows, max, format = (value) => value, tipUnit = "", emptyText = EMPTY_CELL_TEXT, labelWidth = 120, cellHeight = 36, minCellWidth = 52, showScale, scaleLabel, dangerScaleLabel,
}) {
    const gridVars = {
        "--label-width": `${labelWidth}px`,
        "--columns": columns.length,
        "--min-cell-width": `${minCellWidth}px`,
        "--grid-min-width": `${labelWidth + columns.length * minCellWidth}px`,
        "--cell-height": `${cellHeight}px`,
    };
    return (
        <div>
            <div className="matrix-heatmap">
                <div className="matrix-heatmap__grid" style={gridVars}>
                    <span />
                    {columns.map((column, columnIndex) => (
                        <span key={columnIndex} title={column.label} className={`matrix-heatmap__column${column.danger ? " matrix-heatmap__column--danger" : ""}`}>{column.label}</span>
                    ))}
                    {rows.map((row, rowIndex) => (
                        <RowCells key={rowIndex} row={row} columns={columns} max={max} format={format} tipUnit={tipUnit} emptyText={emptyText} />
                    ))}
                </div>
            </div>
            {showScale && (
                <div className="matrix-heatmap__scale">
                    <ScaleKey label={scaleLabel} format={format} max={max} scale={PURPLE_SCALE} />
                    {columns.some((column) => column.danger) && <ScaleKey label={dangerScaleLabel} format={format} max={max} scale={RED_SCALE} />}
                </div>
            )}
        </div>
    );
}

// A light-to-dark bar from 0 to the darkest value, with an optional label.
function ScaleKey({ label, format, max, scale }) {
    return (
        <span className={`matrix-heatmap__scale-key${label ? " matrix-heatmap__scale-key--labelled" : ""}`}>
            {label && <span className="matrix-heatmap__scale-label">{label}</span>}
            <span>{format(0)}</span>
            <span className="matrix-heatmap__scale-bar" style={barVars(scale)} />
            <span>{format(max)}</span>
        </span>
    );
}

// The first and last columns hang their hover text inward so it stays inside the grid.
const tipEdge = (index, count) => {
    if (index === 0) return " matrix-heatmap__cell--tip-start";
    return index === count - 1 ? " matrix-heatmap__cell--tip-end" : "";
};

// "5 devices", "1 device"; just the formatted value when there is no unit.
const tipValue = (value, format, unit) => `${format(value)}${unit ? ` ${unit}${value === 1 ? "" : "s"}` : ""}`;

function RowCells({ row, columns, max, format, tipUnit, emptyText }) {
    return (
        <>
            <span className="matrix-heatmap__row">{row.label}</span>
            {row.cells.map((value, index) => {
                const shading = shade(value, max, columns[index].danger ? RED_SCALE : PURPLE_SCALE);
                const tip = `${row.label} · ${columns[index].label}: ${tipValue(value || 0, format, tipUnit)}`;
                return (
                    <div
                        key={index}
                        className={`matrix-heatmap__cell${shading ? "" : " matrix-heatmap__cell--empty"}${shading?.lightText ? " matrix-heatmap__cell--light-text" : ""}${tipEdge(index, columns.length)}`}
                        role="img"
                        aria-label={tip}
                        data-tip={tip}
                        style={shading ? { "--cell-bg": shading.background } : undefined}
                    >
                        {value ? format(value) : emptyText}
                    </div>
                );
            })}
        </>
    );
}
