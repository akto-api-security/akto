import FilterLegend from "./FilterLegend";
import { STATUS_META, STATUS_ORDER } from "../constants";

// Status colour key; colors overrides a status's swatch.
export default function StatusLegend({ statuses = STATUS_ORDER, colors, selected, onSelect }) {
    const items = statuses.map((status) => ({ key: status, label: STATUS_META[status].label, color: colors ? colors[status] : STATUS_META[status].color }));
    return <FilterLegend items={items} selected={selected} onSelect={onSelect} />;
}
