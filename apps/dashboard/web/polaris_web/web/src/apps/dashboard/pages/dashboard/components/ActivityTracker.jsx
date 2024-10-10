import { Avatar, Box, Button, HorizontalStack, Text, VerticalStack } from '@shopify/polaris'
import React from 'react'
import './ActivityTracker.css'

function ActivityTracker({ issueDetails }) {
  const formatDate = (epochTime) => {
      const date = new Date(epochTime * 1000)
      const formattedDate = date.toLocaleDateString('en-US', { 
          year: 'numeric', month: 'short', day: 'numeric' 
      })
      const formattedTime = date.toLocaleTimeString('en-US', { 
          hour: 'numeric', minute: '2-digit', hour12: true 
      })
      return [formattedDate, formattedTime]
  }

  const generateActivityEvents = (issue) => {
      const activityEvents = []

      const createdEvent = {
          description: 'Found the issue',
          timestamp: issue.creationTime,
      }
      activityEvents.push(createdEvent)

      if (issue.testRunIssueStatus === 'IGNORED') {
          const ignoredEvent = {
              description: <Text>Issue marked as <b>IGNORED</b> - {issue.ignoreReason || 'No reason provided'}</Text>,
              timestamp: issue.lastUpdated,
          }
          activityEvents.push(ignoredEvent)
      }

      if (issue.testRunIssueStatus === 'FIXED') {
          const fixedEvent = {
              description: <Text>Issue marked as <b>FIXED</b></Text>,
              timestamp: issue.lastUpdated,
          }
          activityEvents.push(fixedEvent)
      }

      return activityEvents
  }

  // Group events by date
  const groupEventsByDate = (events) => {
      return events.reduce((groupedEvents, event) => {
          const eventDate = formatDate(event.timestamp)[0]
          if (!groupedEvents[eventDate]) {
              groupedEvents[eventDate] = []
          }
          groupedEvents[eventDate].push(event)
          return groupedEvents
      }, {})
  }

  const latestActivity = generateActivityEvents(issueDetails).reverse()
  const groupedActivity = groupEventsByDate(latestActivity)

  return (
      <Box padding={5}>
          <VerticalStack>
              {Object.keys(groupedActivity).map((date, dateIndex) => (
                  <Box key={dateIndex}>
                      <HorizontalStack gap={5}>
                        <div style={{ background: '#C9CCCF', width: '2px', marginLeft: '12px', height: '44px' }} />
                        <Text variant="bodySm" color="subdued" style={{ marginBottom: '10px' }}>
                            {date}
                        </Text>
                      </HorizontalStack>
                      {groupedActivity[date].map((event, eventIndex) => (
                          <HorizontalStack key={eventIndex} align='space-between'>
                              <div style={{ display: 'flex', gap: '12px' }}>
                                  <Box>
                                      <div className='issues-event-item-gap'><Avatar shape="round" size="extraSmall" source="/public/issues-event-icon.svg" /></div>
                                      {eventIndex < (groupedActivity[date].length - 1) ? (
                                          <div style={{ background: '#C9CCCF', width: '2px', margin: 'auto', height: '12px' }} />
                                      ) : null}
                                  </Box>
                                  <Box>
                                    <div className='issues-event-item-gap'><Text variant="bodyMd">{event.description}</Text></div>
                                  </Box>
                              </div>
                              <div className='issues-event-item-gap'>
                                <Text id="event-time-text" variant="bodySm" color="subdued">
                                    {formatDate(event.timestamp)[1]}
                                </Text>
                              </div>
                          </HorizontalStack>
                      ))}
                  </Box>
              ))}
          </VerticalStack>
      </Box>
  )
}

export default ActivityTracker;
