import PersistStore from "@/apps/main/PersistStore";
import LocalStore from "@/apps/main/LocalStorageStore";
import { GOVERNED_INVENTORY_CATEGORIES, INVENTORY_PATH } from "../constants";

// Where the inventory grid saves its filters; it restores them when it opens.
const INVENTORY_GRID_KEY = `${INVENTORY_PATH}/ag-grid`;

// The agentic pages send you to their legacy version while the new layout is off; the Overview is a new-layout page, so its links turn it on.
export function openNewLayoutPage(navigate, path) {
    LocalStore.getState().setAgenticNewLayout(true);
    navigate(path);
}

// Opens the AI inventory on the given category tabs, plus any other column filters ({ column: values }): the grid restores its saved filters, so they are set there.
// `query` is added to the URL, for what the grid does not filter on.
export default function openInventory(navigate, categories, columnFilters = {}, query = "") {
    const { filtersMap, setFiltersMap } = PersistStore.getState();
    setFiltersMap({ ...filtersMap, [INVENTORY_GRID_KEY]: { ...(filtersMap[INVENTORY_GRID_KEY] || {}), filters: { category: categories, ...columnFilters } } });
    openNewLayoutPage(navigate, `${INVENTORY_PATH}${query}`);
}

// Opens the inventory on the tools in one approval status; the label is the status text the inventory filters on ("Approved", "Under review", ...).
// firstSeen { start, end } (epoch seconds, end exclusive) narrows it to the tools first seen in that window.
export function openInventoryByStatus(navigate, statusLabel, firstSeen) {
    const query = firstSeen ? `?firstSeenStart=${firstSeen.start}&firstSeenEnd=${firstSeen.end}` : "";
    openInventory(navigate, GOVERNED_INVENTORY_CATEGORIES, { approvalStatus: [statusLabel] }, query);
}
