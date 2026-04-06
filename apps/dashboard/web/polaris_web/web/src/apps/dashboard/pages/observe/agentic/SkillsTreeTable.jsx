import React, { useMemo, useCallback } from 'react';
import { Badge, Box, DataTable, HorizontalStack, IndexFiltersMode, Text } from '@shopify/polaris';
import { useNavigate } from 'react-router-dom';
import GithubSimpleTable from '@/apps/dashboard/components/tables/GithubSimpleTable';
import TooltipText from '@/apps/dashboard/components/shared/TooltipText';
import { CellType } from '@/apps/dashboard/components/tables/rows/GithubRow';
import { getTypeFromTags } from './mcpClientHelper';
import { INVENTORY_PATH, PAGE_LIMIT } from './constants';
import func from '@/util/func';

const COLLAPSIBLE_ICON_WIDTH = '32px';
const SKILL_NAME_MAX_WIDTH = '300px';

const CHILD_CELL = {
    COLLECTION: 'displayNameComp',
    TYPE: 'typeComp',
};

const CHILD_COL_WIDTH = {
    COLLECTION: '260px',
    TYPE: '120px',
};

const COL_WIDTH = {
    COLLECTIONS: '100px',
    ENDPOINTS: '100px',
};

const parentHeaders = [
    { title: "", text: "", value: "collapsibleIcon", type: CellType.COLLAPSIBLE, boxWidth: COLLAPSIBLE_ICON_WIDTH },
    { title: "Skill", text: "Skill", value: CHILD_CELL.COLLECTION, filterKey: "groupName", textValue: "groupName", showFilter: true },
    { title: "Collections", text: "Collections", value: "collectionsCount", isText: CellType.TEXT, boxWidth: COL_WIDTH.COLLECTIONS },
    { title: "Endpoints", text: "Endpoints", value: "endpointsCount", isText: CellType.TEXT, sortActive: true, boxWidth: COL_WIDTH.ENDPOINTS },
];

const childHeaders = [
    { value: CHILD_CELL.COLLECTION, boxWidth: CHILD_COL_WIDTH.COLLECTION },
    { value: CHILD_CELL.TYPE, boxWidth: CHILD_COL_WIDTH.TYPE },
];

const sortOptions = [
    { label: "Skill", value: "groupName asc", directionLabel: "A-Z", sortKey: "groupName", columnIndex: 2 },
    { label: "Skill", value: "groupName desc", directionLabel: "Z-A", sortKey: "groupName", columnIndex: 2 },
    { label: "Endpoints", value: "endpointsCount asc", directionLabel: "Lowest", sortKey: "endpointsCount", columnIndex: 4 },
    { label: "Endpoints", value: "endpointsCount desc", directionLabel: "Highest", sortKey: "endpointsCount", columnIndex: 4 },
];

const resourceName = { singular: "skill", plural: "skills" };

const renderChildCellContent = (headerValue, collection) => {
    if (headerValue === CHILD_CELL.COLLECTION) {
        const displayName = collection.displayName || collection.name || collection.hostName || String(collection.id);
        return (
            <Box maxWidth={CHILD_COL_WIDTH.COLLECTION}>
                <TooltipText tooltip={displayName} text={displayName} />
            </Box>
        );
    }
    return <Text variant="bodySm">{getTypeFromTags(collection.envType)}</Text>;
};

const CollectionsTable = ({ collections }) => {
    const navigate = useNavigate();

    const handleCollectionClick = useCallback((id) => {
        navigate(`${INVENTORY_PATH}/${id}`);
    }, [navigate]);

    const rows = useMemo(() => collections.map((collection) => [
        <Box key={`spacer-${collection.id}`} minWidth={COLLAPSIBLE_ICON_WIDTH} />,
        ...childHeaders.map((header) => (
            <Box
                key={`${header.value}-${collection.id}`}
                minWidth={header.boxWidth}
                onClick={() => handleCollectionClick(collection.id)}
                style={{ cursor: 'pointer' }}
            >
                {renderChildCellContent(header.value, collection)}
            </Box>
        )),
    ]), [collections, handleCollectionClick]);

    return (
        <td colSpan={parentHeaders.length} style={{ padding: '0px !important' }} className="control-row">
            <DataTable
                rows={rows}
                hasZebraStripingOnData
                headings={[]}
                columnContentTypes={childHeaders.map(() => 'text')}
            />
        </td>
    );
};

function SkillsTreeTable({ skillGroups, tableTabs, onSelect, selected }) {
    const parentRows = useMemo(() =>
        skillGroups.map((group) => ({
            ...group,
            displayNameComp: (
                <HorizontalStack gap="1" align="start" wrap={false}>
                    <Box maxWidth={SKILL_NAME_MAX_WIDTH}>
                        <TooltipText tooltip={group.groupName} text={group.groupName} textProps={{ variant: 'headingSm' }} />
                    </Box>
                    <Badge size="small" status="new">{group.collections.length}</Badge>
                </HorizontalStack>
            ),
            collectionsCount: group.collections.length,
            isTerminal: false,
            collapsibleRow: <CollectionsTable collections={group.collections} />,
        })),
    [skillGroups]);

    const disambiguateLabel = useCallback((key, value) =>
        func.convertToDisambiguateLabelObj(value, null, 2), []);

    return (
        <GithubSimpleTable
            key={`skills-tree-${parentRows.length}`}
            pageLimit={PAGE_LIMIT}
            data={parentRows}
            sortOptions={sortOptions}
            resourceName={resourceName}
            filters={[]}
            disambiguateLabel={disambiguateLabel}
            headers={parentHeaders}
            headings={parentHeaders}
            selectable={false}
            mode={IndexFiltersMode.Default}
            useNewRow={true}
            condensedHeight={true}
            tableTabs={tableTabs}
            onSelect={onSelect}
            selected={selected}
        />
    );
}

export default SkillsTreeTable;
