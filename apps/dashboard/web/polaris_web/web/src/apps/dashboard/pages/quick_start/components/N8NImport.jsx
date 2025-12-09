import React from 'react';
import AIAgentConnectorImport from './AIAgentConnectorImport';
import {
    CONNECTOR_TYPE_N8N,
    CONNECTOR_NAME_N8N,
    DESCRIPTION_N8N,
    DOCS_URL_N8N,
    INTERVAL_N8N,
    N8N_FIELDS
} from '../constants/aiAgentConnectorConstants';

const N8NImport = () => {
    return (
        <AIAgentConnectorImport
            connectorType={CONNECTOR_TYPE_N8N}
            connectorName={CONNECTOR_NAME_N8N}
            description={DESCRIPTION_N8N}
            fields={N8N_FIELDS}
            docsUrl={DOCS_URL_N8N}
            recurringIntervalSeconds={INTERVAL_N8N}
        />
    );
};

export default N8NImport;
