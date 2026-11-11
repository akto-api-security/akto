import PersistStore from "@/apps/main/PersistStore";
import LocalStore from "@/apps/main/LocalStorageStore";
import { INVENTORY_PATH } from "../constants";

// Where the inventory grid saves its filters; it restores them when it opens.
const INVENTORY_GRID_KEY = `${INVENTORY_PATH}/ag-grid`;

// The agentic pages send you to their legacy version while the new layout is off; the Overview is a new-layout page, so its links turn it on.
export function openNewLayoutPage(navigate, path) {
    LocalStore.getState().setAgenticNewLayout(true);
    navigate(path);
}

// Opens the AI inventory on the given category tabs: the grid restores its saved filters, so only the category is pre-set.
export default function openInventory(navigate, categories) {
    const { filtersMap, setFiltersMap } = PersistStore.getState();
    setFiltersMap({ ...filtersMap, [INVENTORY_GRID_KEY]: { ...(filtersMap[INVENTORY_GRID_KEY] || {}), filters: { category: categories } } });
    openNewLayoutPage(navigate, INVENTORY_PATH);
}
