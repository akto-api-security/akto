import React from 'react';
import TestSummaryCard from './TestSummaryCard';

function TestSummaryCardsList({ summaryItems }) {
    return (
        <div style={{ overflowX: 'auto', display: 'flex', gap: '16px', paddingBottom: '8px', maxWidth: '100%', paddingLeft: '2px' }}>
            {summaryItems.map((item, index) => (
                <div key={index} style={{ flex: '0 0 auto' }}> {/* Prevent shrinking */}
                    <TestSummaryCard summaryItem={item} />
                </div>
            ))}
        </div>
    );
}

export default TestSummaryCardsList;
