import { HorizontalStack, Text } from '@shopify/polaris'
import React from 'react'

function LegendLabel() {

  const LegendItem = ({ label, color, borderColor }) => {
    return (
      <HorizontalStack gap="2" align="start">
        <div style={{ width: '14px', height: '14px', borderRadius: '50%', backgroundColor: color, border: `1px solid ${borderColor}` }} />
        <Text variant="bodySm" color="subdued">{label}</Text>
      </HorizontalStack>
    )
  }

    const legendItems = [
        {
            label: 'Added',
            color: 'rgba(0, 255, 0, 0.1)',
            borderColor: '#238636',
        },
        {
            label: 'Removed',
            color: 'rgba(255, 0, 0, 0.1)',
            borderColor: '#D72C0D',
        },
        {
            label: 'Updated',
            color: '#FFEBD3',
            borderColor: '#916A00',
        }
    ]
  
    return (
      <HorizontalStack gap="4" align="start" wrap>
        {legendItems.map((item) => (
          <LegendItem key={item.label} {...item} />
        ))}
      </HorizontalStack>
    )
}

export default LegendLabel