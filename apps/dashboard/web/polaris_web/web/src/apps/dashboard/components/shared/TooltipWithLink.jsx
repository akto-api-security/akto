import { Popover } from '@shopify/polaris'
import React, { useState, useRef } from 'react'

function TooltipWithLink({ children, content, preferredPosition = "above" }) {
    const [active, setActive] = useState(false)
    const [contentActive, setContentActive] = useState(false)
    const enterTimeoutRef = useRef(null)
    const leaveTimeoutRef = useRef(null)

    const handleMouseEnter = () => {
        if (leaveTimeoutRef.current) {
            clearTimeout(leaveTimeoutRef.current)
            leaveTimeoutRef.current = null
        }
        enterTimeoutRef.current = setTimeout(() => {
            setActive(true)
        }, 300)
    }

    const handleMouseLeave = () => {
        if (enterTimeoutRef.current) {
            clearTimeout(enterTimeoutRef.current)
            enterTimeoutRef.current = null
        }
        leaveTimeoutRef.current = setTimeout(() => {
            setActive(false)
            setContentActive(false)
        }, 100)
    }

    const handleContentMouseEnter = () => {
        if (leaveTimeoutRef.current) {
            clearTimeout(leaveTimeoutRef.current)
            leaveTimeoutRef.current = null
        }
        setContentActive(true)
    }

    const handleContentMouseLeave = () => {
        setContentActive(false)
        setActive(false)
    }

    const handleClick = (e) => {
        e.stopPropagation()
    }

    return (
        <Popover
            active={active || contentActive}
            activator={
                <div
                    onMouseEnter={handleMouseEnter}
                    onMouseLeave={handleMouseLeave}
                    onClick={handleClick}
                >
                    {children}
                </div>
            }
            preferredPosition={preferredPosition}
            onClose={() => {
                setActive(false)
                setContentActive(false)
                if (enterTimeoutRef.current) {
                    clearTimeout(enterTimeoutRef.current)
                }
                if (leaveTimeoutRef.current) {
                    clearTimeout(leaveTimeoutRef.current)
                }
            }}
        >
            <div style={{ maxWidth: '350px', padding: "12px" }} onClick={handleClick}>
                <div
                    onMouseEnter={handleContentMouseEnter}
                    onMouseLeave={handleContentMouseLeave}
                >
                    {content}
                </div>
            </div>
        </Popover>
    )
}

export default TooltipWithLink
