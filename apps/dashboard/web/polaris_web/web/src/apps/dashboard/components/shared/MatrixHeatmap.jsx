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
    columns, rows, max, format = (value) => value, emptyText = EMPTY_CELL_TEXT, labelWidth = 120, cellHeight = 36, minCellWidth = 52, showScale,
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
                        <RowCells key={rowIndex} row={row} columns={columns} max={max} format={format} emptyText={emptyText} />
                    ))}
                </div>
            </div>
            {showScale && (
                <div className="matrix-heatmap__scale">
                    {format(0)}<span className="matrix-heatmap__scale-bar" />{format(max)}
                </div>
            )}
        </div>
    );
}

function RowCells({ row, columns, max, format, emptyText }) {
    return (
        <>
            <span className="matrix-heatmap__row">{row.label}</span>
            {row.cells.map((value, index) => {
                const { background, color } = shade(value, max, columns[index].danger ? RED_SCALE : PURPLE_SCALE);
                return (
                    <div
                        key={index}
                        className="matrix-heatmap__cell"
                        title={`${row.label} · ${columns[index].label}: ${value || 0}`}
                        style={{ "--cell-bg": background, "--cell-fg": color }}
                    >
                        {value ? format(value) : emptyText}
                    </div>
                );
            })}
        </>
    );
}
