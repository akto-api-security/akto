import { useEffect, useState } from "react";
import discoveryApi from "../discoveryApi";
import { LOAD_STATE } from "../constants";

const PAGE_SIZE_SETTLE_MS = 300;

// The tools that need a decision, a page at a time; measuredPageSize is how many rows fit the card (0 until known).
export default function useDecisionsPage({ deviceScope, refreshKey, measuredPageSize }) {
    const [pageSize, setPageSize] = useState(0);
    // A new scope or page size starts again from the first page, in the same render (no fetch at the old page).
    const filterKey = `${deviceScope}|${pageSize}`;
    const [pageState, setPageState] = useState({ filterKey, page: 0 });
    const page = pageState.filterKey === filterKey ? pageState.page : 0;
    const setPage = (next) => setPageState({ filterKey, page: next });
    // Forget the old filter's page, so returning to an earlier filter does not restore it.
    useEffect(() => { setPageState({ filterKey, page: 0 }); }, [filterKey]);
    const [data, setData] = useState(null);
    // The request (filters and page) that failed, so a new one never shows the old error.
    const requestKey = `${filterKey}|${page}`;
    const [failedKey, setFailedKey] = useState("");
    const failed = failedKey === requestKey;

    // The measured size (0 until known) settles as the neighbouring cards load; use it once it stops changing.
    useEffect(() => {
        if (!measuredPageSize) return undefined;
        const timer = setTimeout(() => setPageSize(measuredPageSize), PAGE_SIZE_SETTLE_MS);
        return () => clearTimeout(timer);
    }, [measuredPageSize]);

    useEffect(() => {
        // Fetch only once the size is known and has settled, so a changed scope costs one request.
        if (!pageSize || pageSize !== measuredPageSize) return undefined;
        let ignore = false;
        setFailedKey("");
        discoveryApi.fetchAtlasDecisions({ deviceScope, skip: page * pageSize, limit: pageSize })
            .then((response) => {
                if (ignore) return;
                // The last row of the last page was just decided: go back to the page that still has rows.
                if (page > 0 && (response?.rows || []).length === 0) setPage(Math.max(0, Math.ceil((response?.total || 0) / pageSize) - 1));
                else setData({ ...(response || { rows: [], total: 0 }), filterKey, page });
            })
            .catch(() => { if (!ignore) setFailedKey(requestKey); });
        return () => { ignore = true; };
    }, [deviceScope, refreshKey, page, pageSize, measuredPageSize]);

    // Rows fetched for another scope or size are not shown.
    const current = data?.filterKey === filterKey ? data : null;
    const rows = current?.rows || [];
    const total = current?.total || 0;
    // The pager follows the rows on screen, so it only moves once the next page has arrived.
    const shownPage = current ? current.page : page;
    // A failed page turn must not look like the previous page loaded fine.
    const pageFailed = failed && current?.page !== page;
    const status = current && !pageFailed ? LOAD_STATE.READY : failed ? LOAD_STATE.ERROR : LOAD_STATE.LOADING;
    return { rows, total, pageSize, shownPage, status, setPage };
}
