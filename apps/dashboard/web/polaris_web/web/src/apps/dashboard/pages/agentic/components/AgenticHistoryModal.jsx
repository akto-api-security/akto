import { useState, useEffect, useCallback, useMemo } from 'react';
import { Box } from '@shopify/polaris';
import { clearConversationFromLocal, getConversationsList, getConversationUsers } from '../services/agenticService';
import FlyLayout from '../../../components/layouts/FlyLayout';
import GithubServerTable from '../../../components/tables/GithubServerTable';
import TooltipText from '../../../components/shared/TooltipText';
import { CellType } from '../../../components/tables/rows/GithubRow';
import func from '@/util/func';

const PAGE_SIZE = 50;

const resourceName = {
    singular: 'conversation',
    plural: 'conversations',
};

const headers = [
    { text: 'Conversation', value: 'titleComp', title: 'Conversation', boxWidth: '260px' },
    { text: 'User', value: 'userEmail', title: 'User', boxWidth: '170px' },
    { text: 'Type', value: 'type', title: 'Type' },
    { text: 'Messages', value: 'turns', title: 'Messages' },
    { text: 'Last updated', value: 'lastUpdated', title: 'Last updated' },
    { text: 'Actions', type: CellType.ACTION },
];

const typeLabels = {
    ASK_AKTO: 'Ask Akto',
    TEST_EXECUTION_RESULT: 'Test result',
    PROMPT_PLAYGROUND: 'Prompt playground',
    ANALYZE_REQUESTS: 'Analyze requests',
    DOCS_AGENT: 'Docs agent',
    ANALYZE_DASHBOARD_DATA: 'Dashboard data',
    AGENTIC_OBSERVE: 'Agentic observe',
    INSIGHTS: 'Insights',
};

function AgenticHistoryModal({ isOpen, onClose, onHistoryClick, onDelete }) {
    // Remounts the table after a delete so the current page reloads
    const [refreshKey, setRefreshKey] = useState(0);
    const [users, setUsers] = useState([]);

    useEffect(() => {
        if (isOpen) {
            getConversationUsers().then(setUsers).catch(() => setUsers([]));
        }
    }, [isOpen]);

    const userFilters = useMemo(() => [{
        key: 'userId',
        label: 'User',
        type: 'select',
        choices: users.map(user => ({ label: user.email, value: user.id })),
        multiple: true,
    }], [users]);
    const emailById = useMemo(() => Object.fromEntries(users.map(user => [user.id, user.email])), [users]);

    const fetchData = useCallback(async (sortKey, sortOrder, skip, limit, filters, filterOperators, queryValue) => {
        try {
            const res = await getConversationsList(limit, queryValue || '', skip, (filters.userId || []).map(Number));
            const value = (res?.history || []).map(item => {
                const id = item._id?._id || item._id;
                const title = item.firstPrompt || item.title || 'Untitled conversation';
                return {
                    id,
                    // The stored title is cut short; the opening prompt is the full text. The cell shows one line
                    // with an ellipsis, and the full text in a tooltip.
                    title,
                    titleComp: <Box maxWidth="260px"><TooltipText text={title} tooltip={title} textProps={{ variant: 'bodyMd' }} /></Box>,
                    userEmail: item.userEmail || '-',
                    type: typeLabels[item.conversationType] || item.conversationType || '-',
                    turns: item.turns ?? '-',
                    lastUpdated: func.prettifyEpoch(item.lastUpdatedAt),
                };
            });
            return { value, total: res?.total ?? value.length };
        } catch (error) {
            console.error('Error loading conversation history:', error);
            return { value: [], total: 0 };
        }
    }, []);

    const handleRowClick = (row) => {
        onHistoryClick(row.id, row.title);
        onClose();
    };

    const handleDelete = async (row) => {
        await clearConversationFromLocal(row.id);
        if (onDelete) onDelete(row.id);
        setRefreshKey(k => k + 1);
    };

    const getActions = (row) => [{
        items: [{
            content: 'Delete',
            destructive: true,
            onAction: () => handleDelete(row),
            requires: 'api/deleteConversationHistory',
        }],
    }];

    const renderHistory = (
        <Box padding="3">
            <GithubServerTable
                key={refreshKey}
                resourceName={resourceName}
                headers={headers}
                headings={headers}
                fetchData={fetchData}
                pageLimit={PAGE_SIZE}
                hidePageSizeSelector
                sortOptions={[]}
                filters={userFilters}
                disambiguateLabel={(key, value) => func.convertToDisambiguateLabelObj(value, emailById, 2)}
                selectable={false}
                useNewRow={true}
                condensedHeight={true}
                hasRowActions={true}
                getActions={getActions}
                onRowClick={handleRowClick}
                skipUrlFilters
            />
        </Box>
    );

    return (
        <FlyLayout
            title="History"
            show={isOpen}
            setShow={onClose}
            components={[renderHistory]}
            loading={false}
            showDivider={true}
            newComp={true}
            isHandleClose={false}
            width="60vw"
        />
    );
}

export default AgenticHistoryModal;
