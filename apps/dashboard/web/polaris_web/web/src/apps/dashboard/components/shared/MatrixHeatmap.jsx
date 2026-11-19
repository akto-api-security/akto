import { Text } from "@shopify/polaris";
import { PURPLE_SCALE_CELLS, RED_SCALE_CELLS } from "./dataColors";
import "./HoverTip.css";
import "./MatrixHeatmap.css";

// Cell colours light to dark, and which steps the legend bar blends between.
const PURPLE_SCALE = { cells: PURPLE_SCALE_CELLS, barSteps: [0, 2, 4] };
const RED_SCALE = { cells: RED_SCALE_CELLS, barSteps: [1, 2, 4] };
const LIGHT_TEXT_FROM_STEP = 3;
const EMPTY_CELL_TEXT = "–";

// Shade by sqrt of the value, so small values stay visible next to large ones; null for an empty cell.
function shade(value, max, scale) {
    if (!value) return null;
    const step = Math.min(scale.cells.length - 1, Math.floor(Math.sqrt(value / max) * scale.cells.length));
    return { background: scale.cells[step], lightText: step >= LIGHT_TEXT_FROM_STEP };
}

const barVars = (scale) => Object.fromEntries(scale.barSteps.map((step, index) => [`--bar-stop-${index}`, scale.cells[step]]));

// Row x column grid shaded by value; max is the darkest value. onRowClick(row) makes a row label and its cells clickable; onCellClick(row, column) makes the cells clickable, and wins over onRowClick for them; empty cells are never clickable.
export default function MatrixHeatmap({
    columns, rows, max, format = (value) => value, tipUnit = "", emptyText = EMPTY_CELL_TEXT, labelWidth = 120, cellHeight = 36, minCellWidth = 52, showScale, scaleLabel, dangerScaleLabel, onRowClick, onCellClick,
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
                        <span key={columnIndex} title={column.label} className="matrix-heatmap__column">
                            <Text as="span" variant="bodySm" color={column.danger ? "critical" : "subdued"} fontWeight={column.danger ? "semibold" : undefined}>{column.label}</Text>
                        </span>
                    ))}
                    {rows.map((row, rowIndex) => (
                        <RowCells key={rowIndex} row={row} columns={columns} max={max} format={format} tipUnit={tipUnit} emptyText={emptyText} onRowClick={onRowClick} onCellClick={onCellClick} />
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
            {label && <span className="matrix-heatmap__scale-label"><Text as="span" variant="bodySm" color="subdued">{label}</Text></span>}
            <Text as="span" variant="bodySm" color="subdued">{format(0)}</Text>
            <span className="matrix-heatmap__scale-bar" style={barVars(scale)} />
            <Text as="span" variant="bodySm" color="subdued">{format(max)}</Text>
        </span>
    );
}

// The first and last columns hang their hover text inward so it stays inside the grid.
const tipEdge = (index, count) => {
    if (index === 0) return " hover-tip--start";
    return index === count - 1 ? " hover-tip--end" : "";
};

// "5 devices", "1 device"; just the formatted value when there is no unit.
const tipValue = (value, format, unit) => `${format(value)}${unit ? ` ${unit}${value === 1 ? "" : "s"}` : ""}`;

// Click props for an element that runs `action`; none when there is no action.
const clickProps = (action) => (action ? {
    role: "button",
    tabIndex: 0,
    onClick: action,
    onKeyDown: (event) => {
        if (event.key === "Enter" || event.key === " ") {
            event.preventDefault();
            action();
        }
    },
} : {});

function RowCells({ row, columns, max, format, tipUnit, emptyText, onRowClick, onCellClick }) {
    const labelClickable = clickProps(onRowClick && (() => onRowClick(row)));
    return (
        <>
            <span className={`matrix-heatmap__row hover-tip hover-tip--start${onRowClick ? " matrix-heatmap--clickable" : ""}`} aria-label={onRowClick ? `Open ${row.label}` : undefined} data-tip={row.label} {...labelClickable}><span className="matrix-heatmap__row-label"><Text as="span" variant="bodyMd">{row.label}</Text></span></span>
            {row.cells.map((value, index) => {
                const shading = shade(value, max, columns[index].danger ? RED_SCALE : PURPLE_SCALE);
                const tip = `${row.label} · ${columns[index].label}: ${tipValue(value || 0, format, tipUnit)}`;
                // An empty cell has nothing behind it, so it never opens anything.
                const cellAction = !value ? undefined : onCellClick ? () => onCellClick(row, columns[index]) : onRowClick && (() => onRowClick(row));
                return (
                    <div
                        key={index}
                        className={`matrix-heatmap__cell hover-tip${shading ? "" : " matrix-heatmap__cell--empty"}${shading?.lightText ? " matrix-heatmap__cell--light-text" : ""}${tipEdge(index, columns.length)}${cellAction ? " matrix-heatmap--clickable" : ""}`}
                        role="img"
                        aria-label={tip}
                        {...clickProps(cellAction)}
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
