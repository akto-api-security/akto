import { ActionList, Avatar, Box, Scrollable } from '@shopify/polaris';
import func from '@/util/func';

/** Same scroll pattern as DateRangePicker: Scrollable + fixed height inside Popover.Pane. */
export default function ComplianceMenu({ items, onSelect }) {
    return (
        <Scrollable style={{ height: '334px' }}>
            <ActionList
                actionRole="menuitem"
                items={items.map((compliance) => ({
                    content: compliance,
                    prefix: (
                        <Box>
                            <Avatar
                                source={func.getComplianceIcon(compliance)}
                                shape="square"
                                size="extraSmall"
                            />
                        </Box>
                    ),
                    onAction: () => onSelect(compliance),
                }))}
            />
        </Scrollable>
    );
}
