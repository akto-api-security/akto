import { IndexTable } from '@shopify/polaris'

// A thin wrapper giving IndexTable the same flat {headings, rows} ergonomics DataTable has —
// `rows` is an array of cell arrays, same shape as DataTable's own `rows` prop — for a plain,
// non-interactive, non-selectable table. No equivalent wrapper existed anywhere else in the app
// (HighestRiskAgentsTable.jsx, ActivityLog.jsx, SummaryTable.jsx each write their own Row/Cell
// markup inline) before this one.
function SimpleIndexTable({ resourceName, headings, rows, getRowId }) {
    return (
        <IndexTable resourceName={resourceName} itemCount={rows.length} headings={headings} selectable={false}>
            {rows.map((cells, index) => {
                const id = getRowId ? getRowId(cells, index) : String(index)
                return (
                    <IndexTable.Row id={id} key={id} position={index}>
                        {cells.map((cell, cellIndex) => (
                            <IndexTable.Cell key={cellIndex}>{cell}</IndexTable.Cell>
                        ))}
                    </IndexTable.Row>
                )
            })}
        </IndexTable>
    )
}

export default SimpleIndexTable
