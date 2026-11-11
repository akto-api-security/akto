import { useState, useCallback } from "react";

/*
 * Drives one AG Grid Set Filter column from controls outside the grid (tabs, toggle buttons, chips)
 * and keeps those controls in step when the filter changes from the grid itself (header menu,
 * Filters panel, persisted state).
 *
 * const status = useGridColumnFilter(gridRef, "approvalStatus");
 * status.values             // currently selected values
 * status.apply(["Blocked"]) // set the column filter ([] clears it)
 * status.sync(api)          // call from onFirstDataRendered / a "filterChanged" listener
 */
export default function useGridColumnFilter(gridRef, colId) {
    const [values, setValues] = useState([]);

    const apply = useCallback((next) => {
        setValues(next);
        const api = gridRef.current?.api;
        if (!api) return;
        api.setColumnFilterModel(colId, next.length ? { filterType: "set", values: next } : null)
            .then(() => api.onFilterChanged());
    }, [gridRef, colId]);

    const sync = useCallback((api) => {
        setValues(api.getColumnFilterModel(colId)?.values || []);
    }, [colId]);

    return { values, apply, sync };
}
