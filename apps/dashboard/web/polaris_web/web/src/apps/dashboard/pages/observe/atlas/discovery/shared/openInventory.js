import PersistStore from "@/apps/main/PersistStore";
import { INVENTORY_PATH } from "../constants";

// Where the inventory grid saves its filters; it restores them when it opens.
const INVENTORY_GRID_KEY = `${INVENTORY_PATH}/ag-grid`;

// Opens the AI inventory on the given category tabs: the grid restores its saved filters, so only the category is pre-set.
export default function openInventory(navigate, categories) {
    const { filtersMap, setFiltersMap } = PersistStore.getState();
    setFiltersMap({ ...filtersMap, [INVENTORY_GRID_KEY]: { ...(filtersMap[INVENTORY_GRID_KEY] || {}), filters: { category: categories } } });
    navigate(INVENTORY_PATH);
}
