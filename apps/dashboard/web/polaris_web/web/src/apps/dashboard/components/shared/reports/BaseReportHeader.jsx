import { Box, HorizontalStack, Text, VerticalStack } from '@shopify/polaris'
import React, { useEffect, useState } from 'react'

/**
 * Base component for report headers with editable organization name and date/user fields
 *
 * @param {Object} props - Component props
 * @param {string} props.reportTitle - Title displayed above the main heading (e.g., "Vulnerability Report", "Threat Detection Report")
 * @param {string} props.subtitleText - Text after organization name (e.g., "API Security Findings", "Threat Detection Report")
 * @param {string} props.organizationName - Organization name to display
 * @param {Function} props.setOrganizationName - Function to update organization name
 * @param {string} props.userName - User name to display
 * @param {Function} props.setUserName - Function to update user name
 * @param {string} props.currentDate - Current date to display
 * @param {boolean} props.showDateInfo - Whether to show date in the second line (default: true)
 */
const BaseReportHeader = ({
    reportTitle,
    subtitleText,
    userName,
    organizationName,
    currentDate,
    setUserName,
    setOrganizationName,
    showDateInfo = true
}) => {
    const [isEditingOrgName, setIsEditingOrgName] = useState(false)
    const [isEditingSecondLine, setIsEditingSecondLine] = useState(false)

    const [tempOrgName, setTempOrgName] = useState(organizationName)

    // Determine initial second line text based on showDateInfo
    const getDefaultSecondLine = () => {
        const isIssuesPage = window.location.pathname.includes('issues')
        if (!showDateInfo || isIssuesPage) {
            return `by @${userName}`
        }
        return `On ${currentDate} by @${userName}`
    }

    const [tempSecondLine, setTempSecondLine] = useState(getDefaultSecondLine())

    useEffect(() => {
        const defaultLine = getDefaultSecondLine()

        // Only update if not editing and temp line still has "-"
        if (!isEditingSecondLine && tempSecondLine.includes('-')) {
            setTempSecondLine(defaultLine)
        }
    }, [currentDate])

    const handleOnClick = (type) => {
        if (type === 'orgname') {
            setIsEditingOrgName(true)
        } else if (type === 'secondline') {
            setIsEditingSecondLine(true)
        }
    }

    const handleSave = (type) => {
        if (type === 'orgname') {
            setOrganizationName(tempOrgName)
            setIsEditingOrgName(false)
        } else if (type === 'secondline') {
            const match = tempSecondLine.match(/@(\w+)/)
            if (match) {
                setUserName(match[1])
            }
            setIsEditingSecondLine(false)
        }
    }

    const handleBlurOrEnter = (e, type) => {
        if (e.type === 'blur' || e.key === 'Enter') {
            handleSave(type)
        }
    }

    return (
        <div className='report-cover-page-container'>
            <img src='/public/vul_report_bg.svg' alt="Header Image" className='report-cover-page' />
            <img src='/public/white_logo.svg' alt='akto-logo' className='report-akto-logo'></img>
            <div className='report-cover-page-content'>
                <Box width="100%">
                    <HorizontalStack align="space-between">
                        <VerticalStack gap={'5'}>
                            <div className='report-default-heading'>{reportTitle}</div>
                            <div className="heading-text">
                                <Text variant="heading4xl">
                                    {isEditingOrgName ? (
                                        <input
                                            style={{ fontSize: '64px' }}
                                            type="text"
                                            value={tempOrgName}
                                            onChange={(e) => setTempOrgName(e.target.value)}
                                            onBlur={(e) => handleBlurOrEnter(e, 'orgname')}
                                            onKeyDown={(e) => handleBlurOrEnter(e, 'orgname')}
                                            autoFocus
                                            className="editable-input"
                                        />
                                    ) : (
                                        <span
                                            onClick={() => handleOnClick('orgname')}
                                            id='organization-name'
                                        >
                                            {organizationName}
                                        </span>
                                    )}{' '}
                                    {subtitleText}
                                </Text>
                            </div>

                            <Text variant="bodyMd">
                                {isEditingSecondLine ? (
                                    <input
                                        type="text"
                                        value={tempSecondLine}
                                        onChange={(e) => setTempSecondLine(e.target.value)}
                                        onBlur={(e) => handleBlurOrEnter(e, 'secondline')}
                                        onKeyDown={(e) => handleBlurOrEnter(e, 'secondline')}
                                        autoFocus
                                        className="editable-input"
                                        style={{ width: '100%' }}
                                    />
                                ) : (
                                    <span
                                        onClick={() => handleOnClick('secondline')}
                                        id='second-line-wrapper'
                                        className='semibold-subtext-info'
                                    >
                                        {tempSecondLine}
                                    </span>
                                )}
                            </Text>

                        </VerticalStack>
                    </HorizontalStack>
                </Box>
            </div>
        </div>
    )
}

export default BaseReportHeader
