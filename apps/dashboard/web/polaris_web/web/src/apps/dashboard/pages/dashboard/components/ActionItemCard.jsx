import { Avatar, Badge, Box, Button, Card, Divider, HorizontalStack, Icon, Text, VerticalStack, Popover, OptionList, Tag } from '@shopify/polaris'
import React, { useState } from 'react'
import { TeamMajor, ToolsMajor, EmailMajor } from "@shopify/polaris-icons"

function ActionItemCard(props) {
    const {cardObj, onButtonClick } = props ;
    const [popoverActive, setPopoverActive] = useState(false);
    const [selectedUser, setSelectedUser] = useState([]);

    // Sample users - in real app, this would come from your users list
    const users = [
        {value: 'user1', label: 'John Doe'},
        {value: 'user2', label: 'Jane Smith'},
        {value: 'user3', label: 'Mike Johnson'},
    ];

    const togglePopoverActive = () => {
        setPopoverActive((active) => !active);
    };

    const handleUserSelect = (value) => {
        setSelectedUser(value);
        setPopoverActive(false);
    };

    const activator = (
        <Button onClick={togglePopoverActive} plain removeUnderline>
            Assign Task
        </Button>
    );

    const assignedUser = selectedUser.length > 0 ? users.find(u => u.value === selectedUser[0]) : null;

    return (
        <div
            onClick={e => {
                // Prevent flyout if clicking on assign button, popover, or tag
                if (
                    e.target.closest('.Polaris-Button') ||
                    e.target.closest('.Polaris-Popover') ||
                    e.target.closest('.Polaris-Tag')
                ) {
                    return;
                }
                onButtonClick(cardObj);
            }}
            style={{cursor: 'pointer'}}
        >
        <Card padding={"5"}>
            <VerticalStack gap={"3"}>
                <Box width='30px'>
                <Badge status="critical-strong-experimental">P0</Badge>
                </Box>
                <Box>
                    <Text variant="headingSm">3 APIs have no authentication</Text>
                    <Text variant='bodyMd' color='subdued'>
                        Publicly accessible business logic
                    </Text>
                </Box>
                <HorizontalStack gap={"2"}>
                    <HorizontalStack gap={"1"}>
                        <Box><Icon source={TeamMajor} color="subdued" /></Box>
                        <Text variant='bodyMd'>Platform</Text>
                    </HorizontalStack>
                    <HorizontalStack gap={"1"}>
                        <Box><Icon source={ToolsMajor} color="subdued" /></Box>
                        <Text variant='bodyMd'>Low</Text>
                    </HorizontalStack>
                </HorizontalStack>
                <Divider />
                <HorizontalStack gap={"3"} align="space-between" wrap={false}>
                    <Box>
                        {assignedUser ? (
                            <Tag onRemove={() => setSelectedUser([])}>
                                {assignedUser.label}
                            </Tag>
                        ) : (
                            <Popover
                                active={popoverActive}
                                activator={activator}
                                onClose={() => setPopoverActive(false)}
                                autofocusTarget="first-node"
                            >
                                <OptionList
                                    title="Assign to"
                                    onChange={handleUserSelect}
                                    options={users}
                                    selected={selectedUser}
                                />
                            </Popover>
                        )}
                    </Box>
                    <HorizontalStack gap={"2"}>
                        <Icon source={EmailMajor} color="subdued"/>
                        <Avatar size="extraSmall" shape="square" source="/public/logo_jira.svg" />
                    </HorizontalStack>
                </HorizontalStack>
            </VerticalStack>
        </Card>
        </div>
    )
}

export default ActionItemCard