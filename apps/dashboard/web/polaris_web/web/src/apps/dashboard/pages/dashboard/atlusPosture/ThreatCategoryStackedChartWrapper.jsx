import React from 'react';
import ThreatCategoryChart from './ThreatCategoryChart';

// Wrapper component for ThreatCategoryChart
function ThreatCategoryStackedChartWrapper({ startTimestamp, endTimestamp, itemId, onRemoveComponent }) {
    return (
        <ThreatCategoryChart
            startTimestamp={startTimestamp}
            endTimestamp={endTimestamp}
            itemId={itemId}
            onRemoveComponent={onRemoveComponent}
        />
    );
}

export default ThreatCategoryStackedChartWrapper;
