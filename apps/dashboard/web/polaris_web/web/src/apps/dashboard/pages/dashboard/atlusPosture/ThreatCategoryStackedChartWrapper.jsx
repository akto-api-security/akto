import React from 'react';
import ThreatCategoryChart from './ThreatCategoryChart';

// Wrapper component for ThreatCategoryChart
function ThreatCategoryStackedChartWrapper({ startTimestamp, endTimestamp, itemId, onRemoveComponent, onCategoryClick }) {
    return (
        <ThreatCategoryChart
            startTimestamp={startTimestamp}
            endTimestamp={endTimestamp}
            itemId={itemId}
            onRemoveComponent={onRemoveComponent}
            onCategoryClick={onCategoryClick}
        />
    );
}

export default ThreatCategoryStackedChartWrapper;
