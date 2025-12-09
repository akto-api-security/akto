import React from 'react';
import AIAgentConnectorImport from './AIAgentConnectorImport';
import {
    CONNECTOR_TYPE_LANGCHAIN,
    CONNECTOR_NAME_LANGCHAIN,
    DESCRIPTION_LANGCHAIN,
    DOCS_URL_LANGCHAIN,
    INTERVAL_LANGCHAIN,
    LANGCHAIN_FIELDS
} from '../constants/aiAgentConnectorConstants';

const LangchainImport = () => {
    return (
        <AIAgentConnectorImport
            connectorType={CONNECTOR_TYPE_LANGCHAIN}
            connectorName={CONNECTOR_NAME_LANGCHAIN}
            description={DESCRIPTION_LANGCHAIN}
            fields={LANGCHAIN_FIELDS}
            docsUrl={DOCS_URL_LANGCHAIN}
            recurringIntervalSeconds={INTERVAL_LANGCHAIN}
        />
    );
};

export default LangchainImport;
