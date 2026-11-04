import "./MatrixHeatmap.css";

const PURPLE_SCALE = ["#F4EEFF", "#E5D7FF", "#B38CFF", "#8051FF", "#6200EA"];
const RED_SCALE = ["#FFF4F4", "#FED3D1", "#FD8A7B", "#E0442A", "#B01E00"];
const EMPTY_COLORS = { background: "#FAFAFB", color: "#C9CCCF" };
const DARK_TEXT = "#202223";
const DARK_FROM_STEP = 3;
const EMPTY_CELL_TEXT = "–";

// Shade by sqrt of the value, so small values stay visible next to large ones.
function shade(value, max, scale) {
    if (!value) return EMPTY_COLORS;
    const step = Math.min(scale.length - 1, Math.floor(Math.sqrt(value / max) * scale.length));
    return { background: scale[step], color: step >= DARK_FROM_STEP ? "#fff" : DARK_TEXT };
}

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
                    <ScaleKey label={scaleLabel} format={format} max={max} />
                    {columns.some((column) => column.danger) && <ScaleKey label={dangerScaleLabel} format={format} max={max} danger />}
                </div>
            )}
        </div>
    );
}

// A light-to-dark bar from 0 to the darkest value, with an optional label.
function ScaleKey({ label, format, max, danger }) {
    return (
        <span className={`matrix-heatmap__scale-key${label ? " matrix-heatmap__scale-key--labelled" : ""}`}>
            {label && <span className="matrix-heatmap__scale-label">{label}</span>}
            <span>{format(0)}</span>
            <span className={`matrix-heatmap__scale-bar${danger ? " matrix-heatmap__scale-bar--danger" : ""}`} />
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
                const { background, color } = shade(value, max, columns[index].danger ? RED_SCALE : PURPLE_SCALE);
                const tip = `${row.label} · ${columns[index].label}: ${tipValue(value || 0, format, tipUnit)}`;
                return (
                    <div
                        key={index}
                        className={`matrix-heatmap__cell${tipEdge(index, columns.length)}`}
                        role="img"
                        aria-label={tip}
                        data-tip={tip}
                        style={{ "--cell-bg": background, "--cell-fg": color }}
                    >
                        {value ? format(value) : emptyText}
                    </div>
                );
            })}
        </>
    );
}
