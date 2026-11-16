import inventory from './inventory.mock.json'

// Real rows from the local database, saved as JSON. It has the shape the backend will return,
// so this is the only function to change when the page is connected to it.
export async function fetchInventory() {
    return inventory
}
