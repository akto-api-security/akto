import { Card, VerticalStack, Box, HorizontalStack, Text, DataTable, Tooltip } from '@shopify/polaris'
import ComponentHeader from './ComponentHeader'
import { flags } from '../../threat_detection/components/flags/index.mjs'

const CustomDataTable = ({ title = "", data = [], showSignalIcon = true, iconType = 'signal', itemId = "", onRemoveComponent, tooltipContent = "", columnHeaders = [], onRowClick }) => {
    const rows = data.map(item => {
        // Render country icon if country is present
        const countryIcon = item.country ? (
            <Tooltip content={item.country || "Unknown"}>
                <img
                    src={item.country && item.country in flags ? flags[item.country] : flags["earth"]}
                    alt={item.country}
                    style={{ width: '20px', height: '20px', marginRight: '8px', flexShrink: 0 }}
                />
            </Tooltip>
        ) : null;

        // Determine which icon to show based on iconType
        let iconElement = null;
        if (showSignalIcon) {
            if (iconType === 'globe') {
                iconElement = <img src='/public/Globe_icon.svg' alt='globe-icon' style={{ width: '20px', height: '20px', flexShrink: 0 }} />;
            } else {
                iconElement = <img src='/public/menu-graph.svg' alt='growth-icon' style={{ width: '16px', height: '16px', flexShrink: 0 }} />;
            }
        }

        const rowContent = [
            <HorizontalStack gap={3} blockAlign='center'>
                {iconElement}
                {countryIcon}
                <div style={{ flex: 1, minWidth: 0, wordBreak: 'break-word', overflowWrap: 'break-word' }}>
                    <Text variant='bodyMd' fontWeight='medium'>{item.name}</Text>
                </div>
            </HorizontalStack>,
            <div style={{ textAlign: 'right', whiteSpace: 'nowrap' }}>
                <Text variant='bodyMd' fontWeight='medium'>{item.value}</Text>
            </div>
        ];
        if (onRowClick) {
            const clickableProps = {
                style: { cursor: 'pointer' },
                onClick: () => onRowClick(item),
                onKeyDown: (e) => { if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); onRowClick(item); } },
                role: 'button',
                tabIndex: 0
            };
            return [
                <div key="cell-0" {...clickableProps}>{rowContent[0]}</div>,
                <div key="cell-1" {...clickableProps}>{rowContent[1]}</div>
            ];
        }
        return rowContent;
    })

    // Use provided headers or default empty array, wrap in Text components with bold font weight and subdued color
    const headings = columnHeaders.length > 0 
        ? columnHeaders.map(header => 
            typeof header === 'string' 
                ? <Text as="span" fontWeight="semibold" color="subdued">{header}</Text>
                : header
          )
        : []

    return (
        <Card>
            <VerticalStack gap="4">
                <ComponentHeader title={title} itemId={itemId} onRemove={onRemoveComponent} tooltipContent={tooltipContent} />

                <Box width='100%'>
                    <DataTable
                        columnContentTypes={['text', 'numeric']}
                        headings={headings}
                        rows={rows}
                        hideScrollIndicator
                    />
                </Box>
            </VerticalStack>
        </Card>
    )
}

export default CustomDataTable
