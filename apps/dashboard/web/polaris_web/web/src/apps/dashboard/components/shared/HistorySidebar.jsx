import React, { useState } from 'react'
import { 
    LegacyCard, 
    TextField, 
    HorizontalStack,
    VerticalStack,
    Text, 
    Button, 
    Box,
    Divider,
    Icon,
    ActionList,
    Popover
} from '@shopify/polaris'
import { SearchMinor, XSmallMinor, DeleteMinor } from '@shopify/polaris-icons'
import './HistorySidebar.css'

const HistorySidebar = ({ isOpen, onClose, onHistoryItemClick }) => {
    const [searchValue, setSearchValue] = useState('')
    const [hoveredItem, setHoveredItem] = useState(null)
    
    const historyData = [
        {
            id: 1,
            title: "Agentic security posture overview",
            date: "12 mins ago"
        },
        {
            id: 2,
            title: "Agent security status review",
            date: "14 mins ago"
        },
        {
            id: 3,
            title: "Validate agent resilience to instruction override attacks",
            date: "1 day ago"
        },
        {
            id: 4,
            title: "Prompt injection risks across agents",
            date: "1 day ago"
        },
        {
            id: 5,
            title: "Simulating prompt injection attacks", 
            date: "1 day ago"
        },
        {
            id: 6,
            title: "Agent guardrail configuration",
            date: "2 days ago"
        },
        {
            id: 7,
            title: "Shadow agents in my environment",
            date: "3 days ago"
        },
        {
            id: 8,
            title: "Investigating agent behavior",
            date: "3 days ago"
        }
    ]

    const handleHistoryClick = (item) => {
        if (onHistoryItemClick) {
            onHistoryItemClick(item)
        }
        onClose()
    }

    if (!isOpen) return null

    return (
        <>
            {/* Overlay */}
            <div className="history-overlay" onClick={onClose}></div>
            
            {/* Sidebar using Polaris LegacyCard */}
            <div className="history-sidebar-wrapper">
                <LegacyCard sectioned={false}>
                    <Box padding="4">
                        {/* Header with close button */}
                        <div className="history-header">
                            <Text variant="headingMd" fontWeight="semibold">History</Text>
                            <Button 
                                plain 
                                onClick={onClose}
                                accessibilityLabel="Close history"
                                size="large"
                                className="history-close-button"
                            >
                                <svg width="16" height="16" viewBox="0 0 16 16" fill="none">
                                    <path d="M12 4L4 12M4 4l8 8" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round"/>
                                </svg>
                            </Button>
                        </div>
                        
                        {/* Search Field */}
                        <Box paddingBlockStart="4" paddingBlockEnd="4">
                            <TextField
                                value={searchValue}
                                onChange={setSearchValue}
                                placeholder="Search history"
                                prefix={<Icon source={SearchMinor} />}
                                clearButton
                                onClearButtonClick={() => setSearchValue('')}
                            />
                        </Box>
                        
                        <Divider />
                        
                        {/* History List */}
                        <Box paddingBlockStart="4">
                            <VerticalStack gap="1">
                                {historyData.map((item) => (
                                    <div
                                        key={item.id}
                                        className="history-simple-item"
                                        onMouseEnter={() => setHoveredItem(item.id)}
                                        onMouseLeave={() => setHoveredItem(null)}
                                        onClick={() => handleHistoryClick(item)}
                                    >
                                        <div className="history-simple-content">
                                            <Text variant="bodyMd" fontWeight="medium" truncate>
                                                {item.title}
                                            </Text>
                                            <div className="history-simple-right">
                                                {hoveredItem === item.id ? (
                                                    <div 
                                                        className="delete-icon-simple"
                                                        onClick={(e) => {
                                                            e.stopPropagation()
                                                            console.log('Delete:', item.title)
                                                        }}
                                                    >
                                                        <svg xmlns="http://www.w3.org/2000/svg" width="14" height="14" viewBox="0 0 14 14" fill="none">
                                                            <path d="M5.59961 2.79562C5.59961 2.02479 6.22641 1.3999 6.99961 1.3999C7.77281 1.3999 8.39961 2.02479 8.39961 2.79562H11.1996C11.5862 2.79562 11.8996 3.10806 11.8996 3.49348C11.8996 3.87889 11.5862 4.19134 11.1996 4.19134H2.79961C2.41301 4.19134 2.09961 3.87889 2.09961 3.49348C2.09961 3.10806 2.41301 2.79562 2.79961 2.79562H5.59961Z" fill="#5C5F62"/>
                                                            <path d="M3.49961 10.1553V5.5999H4.89961V10.1553C4.89961 10.348 5.05631 10.5042 5.24961 10.5042H6.29961V5.5999H7.69961L7.69961 10.5042H8.74961C8.94291 10.5042 9.09961 10.348 9.09961 10.1553V5.5999H10.4996V10.1553C10.4996 11.1188 9.71611 11.8999 8.74961 11.8999H5.24961C4.28311 11.8999 3.49961 11.1188 3.49961 10.1553Z" fill="#5C5F62"/>
                                                        </svg>
                                                    </div>
                                                ) : (
                                                    <Text variant="bodySm" color="subdued">
                                                        {item.date}
                                                    </Text>
                                                )}
                                            </div>
                                        </div>
                                    </div>
                                ))}
                            </VerticalStack>
                        </Box>
                    </Box>
                </LegacyCard>
            </div>
        </>
    )
}

export default HistorySidebar