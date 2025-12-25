import React, { useState } from 'react'
import './HistorySidebar.css'

const HistorySidebar = ({ isOpen, onClose, onHistoryItemClick }) => {
    const [searchValue, setSearchValue] = useState('')
    
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
            <div className="history-overlay" onClick={onClose}></div>
            <div className="history-sidebar">
                <div className="history-header">
                    <span className="history-title">History</span>
                    <button className="close-btn" onClick={onClose}>×</button>
                </div>
                
                <div className="search-container">
                    <div className="search-input-wrapper">
                        <div className="search-icon">
                            <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 16 16" fill="none">
                                <path fillRule="evenodd" clipRule="evenodd" d="M6 10C3.791 10 2 8.209 2 6C2 3.791 3.791 2 6 2C8.209 2 10 3.791 10 6C10 8.209 8.209 10 6 10ZM15.707 14.293L10.887 9.473C11.585 8.492 12 7.296 12 6C12 2.687 9.313 0 6 0C2.687 0 0 2.687 0 6C0 9.313 2.687 12 6 12C7.296 12 8.492 11.585 9.473 10.887L14.293 15.707C14.488 15.902 14.744 16 15 16C15.256 16 15.512 15.902 15.707 15.707C16.098 15.316 16.098 14.684 15.707 14.293Z" fill="#5C5F62"/>
                            </svg>
                        </div>
                        <input
                            type="text"
                            value={searchValue}
                            onChange={(e) => setSearchValue(e.target.value)}
                            placeholder="Search history"
                            className="search-input"
                        />
                    </div>
                </div>
                
                <div className="history-list">
                    {historyData.map((item) => (
                        <div key={item.id} className="history-item" onClick={() => handleHistoryClick(item)}>
                            <div className="history-item-content">
                                <div className="history-item-title">{item.title}</div>
                                <div className="history-item-date">{item.date}</div>
                            </div>
                            <button className="history-delete-btn">
                                <svg xmlns="http://www.w3.org/2000/svg" width="14" height="14" viewBox="0 0 14 14" fill="none">
                                    <path d="M5.59961 2.79562C5.59961 2.02479 6.22641 1.3999 6.99961 1.3999C7.77281 1.3999 8.39961 2.02479 8.39961 2.79562H11.1996C11.5862 2.79562 11.8996 3.10806 11.8996 3.49348C11.8996 3.87889 11.5862 4.19134 11.1996 4.19134H2.79961C2.41301 4.19134 2.09961 3.87889 2.09961 3.49348C2.09961 3.10806 2.41301 2.79562 2.79961 2.79562H5.59961Z" fill="#5C5F62"/>
                                    <path d="M3.49961 10.1553V5.5999H4.89961V10.1553C4.89961 10.348 5.05631 10.5042 5.24961 10.5042H6.29961V5.5999H7.69961L7.69961 10.5042H8.74961C8.94291 10.5042 9.09961 10.348 9.09961 10.1553V5.5999H10.4996V10.1553C10.4996 11.1188 9.71611 11.8999 8.74961 11.8999H5.24961C4.28311 11.8999 3.49961 11.1188 3.49961 10.1553Z" fill="#5C5F62"/>
                                </svg>
                            </button>
                        </div>
                    ))}
                </div>
            </div>
        </>
    )
}

export default HistorySidebar