import { Divider, Text, VerticalStack, Avatar, Icon, Box } from '@shopify/polaris';
import React, { useState } from 'react';
import { ClockMajor, TeamMajor, ToolsMajor, EmailMajor } from '@shopify/polaris-icons';
import FlyoutHeadingComponent from '../../../components/shared/FlyoutHeadingComponent';
import AssignTaskToUser from './AssignTaskToUser';

const temporaryItemDetails = {
    title: '3 APIs have no authentication',
    priority: 'critical',
    priorityValue: 'P0',
    moreInfo: [
        {
            icon: TeamMajor,
            text: 'Platform'
        },
        {
            icon: ToolsMajor,  
            text: 'Low'
        },
        {
            icon: ClockMajor,
            text: '2 hours ago'
        }
    ],
    secondaryActions: [
        {
            iconComp: () => <Box><Icon source={EmailMajor} color="base" /></Box>,
            onClick: () => { /* TODO: Add email action here */ }
        },
        {
            iconComp: () => <Box className='reduce-size'><Avatar size="extraSmall" shape="square" source="/public/logo_jira.svg"/></Box>,
            onClick: () => { /* TODO: Add Jira action here */ }
        }
    ],
    primaryActionComp: <AssignTaskToUser />
}   

function ActionItemDetails({ item }) {
    if (!item) return null;

    return (
        <VerticalStack gap={"5"}>
            <FlyoutHeadingComponent itemDetails={temporaryItemDetails} />
            <Divider borderWidth="1" />
            {/* <Text variant="bodyMd">{item?.fullDescription}</Text> */}
            <Text variant="bodyMd">3 production APIs are accessible without any authentication. Anyone — including attackers — can invoke these endpoints, putting sensitive data and business logic at risk. This breaks compliance with SOC2 and GDPR. Immediate fix is to enforce JWT/OAuth or API gateway rules.</Text>
        </VerticalStack>
        
    );
}

export default ActionItemDetails; 