import React from 'react';
import AIAgentConnectorImport from './AIAgentConnectorImport';
import {
    CONNECTOR_TYPE_COPILOT_STUDIO,
    CONNECTOR_NAME_COPILOT_STUDIO,
    DESCRIPTION_COPILOT_STUDIO,
    DOCS_URL_COPILOT_STUDIO,
    INTERVAL_COPILOT_STUDIO,
    COPILOT_STUDIO_FIELDS
} from '../constants/aiAgentConnectorConstants';

const CopilotStudioImport = () => {
    return (
        <AIAgentConnectorImport
            connectorType={CONNECTOR_TYPE_COPILOT_STUDIO}
            connectorName={CONNECTOR_NAME_COPILOT_STUDIO}
            description={DESCRIPTION_COPILOT_STUDIO}
            fields={COPILOT_STUDIO_FIELDS}
            docsUrl={DOCS_URL_COPILOT_STUDIO}
            recurringIntervalSeconds={INTERVAL_COPILOT_STUDIO}
        />
    );
};

export default CopilotStudioImport;
