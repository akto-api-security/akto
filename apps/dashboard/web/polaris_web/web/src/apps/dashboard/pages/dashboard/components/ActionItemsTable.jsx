import GithubSimpleTable from '../../../components/tables/GithubSimpleTable';

const resourceName = {
  singular: 'action item',
  plural: 'action items'
};

const ActionItemsTable = ({ data, headers, getActions, jiraTicketUrlMap, onRowClick }) => {
  return (
    <GithubSimpleTable
      key={`table-${JSON.stringify(jiraTicketUrlMap)}`}
      data={data}
      resourceName={resourceName}
      headers={headers}
      headings={headers}
      useNewRow={true}
      condensedHeight={true}
      hideQueryField={true}
      hidePagination={true}
      hasZebraStriping={true}
      getActions={getActions}
      hasRowActions={true}
      defaultSortField="priority"
      defaultSortDirection="asc"
      emptyStateMessage="No action items found"
      onRowClick={onRowClick}
    />
  );
};

export default ActionItemsTable; 