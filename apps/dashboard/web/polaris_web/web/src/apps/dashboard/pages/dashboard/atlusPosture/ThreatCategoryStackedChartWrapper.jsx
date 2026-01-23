import React from 'react';
import ThreatCategoryChart from './ThreatCategoryChart';

// Wrapper component for ThreatCategoryChart with dummy timestamps
function ThreatCategoryStackedChartWrapper() {
    // Set timestamps for last 7 days
    const endTimestamp = Math.floor(Date.now() / 1000);
    const startTimestamp = endTimestamp - (7 * 86400); // 7 days ago

    return (
        <ThreatCategoryChart
            startTimestamp={startTimestamp}
            endTimestamp={endTimestamp}
        />
    );
}

export default ThreatCategoryStackedChartWrapper;
