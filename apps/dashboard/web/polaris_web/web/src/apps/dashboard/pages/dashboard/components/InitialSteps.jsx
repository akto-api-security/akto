import { Avatar, Box, Button, Card, Divider, HorizontalStack, ProgressBar, Text, VerticalStack } from '@shopify/polaris'
import React, { useEffect, useState } from 'react'
import { ChevronDownMinor, ChevronUpMinor } from "@shopify/polaris-icons"
import { useNavigate } from 'react-router-dom';
import api from '../api';
import transform from '../transform';
import func from '@/util/func';

function InitialSteps({initialSteps}) {

    const [stepsCompleted, setStepsCompleted] = useState(0) ;
    const [dropdownActive, setDropdownActive] = useState(-1) ;
    const navigate = useNavigate() ;

    const [connectionsSteps, setConnectionSteps] = useState({}) 

    const steps = [
        {
            text: 'Use mirroring to send duplicate stream of traffic to Akto. No performance impact, only mirrored traffic is used to analyze APIs.',
            title: 'Automated traffic',
            url: '/dashboard/quick-start',
            id: 'automatedTraffic',
        },
        {
            id: 'cicdIntegrations',
            text: 'Send alerts to your slack to get notified when new endpoints are discovered.',
            title: 'Slack alerts',
            url: '/dashboard/settings/integrations/slack'
        },
        {
            id: 'githubSso',
            text: 'Enable Login via GitHub on your Akto dashboard.',
            title: 'SSO',
            url: '/dashboard/settings/integrations/github_sso'
        },
        {
            id: 'inviteMembers',
            text: 'You can invite your team members to Akto.',
            title: 'Invite team members',
            url: '/dashboard/settings/users'
        },
        {
            id: 'slackAlerts',
            text: 'Seamlessly enhance your web application security with CI/CD integration, empowering you to efficiently detect vulnerabilities, analyze and intercept web traffic, and fortify your digital defenses.',
            title: 'CI/CD',
            url: '/dashboard/settings/integrations/ci-cd',
        }
    ]

    const markAsComplete = async(id) => {
        await api.skipConnection(id) ;
        await api.getIntegratedConnections().then((resp)=> {
            setConnectionSteps(resp);
        })
    }

    const expandContent = (index) => {
        if(index === dropdownActive){
            setDropdownActive(-1)
        }else{
            setDropdownActive(index)
        }
    }

    useEffect(()=> {
        setConnectionSteps(initialSteps)
    },[initialSteps])

    useEffect(()=> {
        setStepsCompleted(transform.getConnectedSteps(connectionsSteps))
    },[connectionsSteps])

    const isCompleted = (id) => {
        if(!connectionsSteps || Object.keys(connectionsSteps).length === 0) return false
        return connectionsSteps[id].integrated || ((func.timeNow() - connectionsSteps[id].lastSkipped) <= func.recencyPeriod)
    }

    function StepConnection({step,index}) {
        return(
            <VerticalStack gap={4}>
                <HorizontalStack gap={2}>
                    <Avatar customer name='circle' size="extraSmall"
                        source={isCompleted(step.id) ? "/public/circle_check.svg" : "/public/circle_icon.svg"}
                    />
                    <Box width="85%">
                        <HorizontalStack align="space-between">
                            <Text variant="bodyMd" color={isCompleted(step.id) ? 'subdued' : ''} fontWeight={isCompleted(step.id) ? "" : 'medium'}>
                                {step.title}
                            </Text>
                            <Button plain monochrome icon={index === dropdownActive ? ChevronUpMinor : ChevronDownMinor} onClick={()=> expandContent(index)} />        
                        </HorizontalStack>
                    </Box>
                </HorizontalStack>
                <Box paddingInlineStart={6}>
                {index === dropdownActive ?
                    <VerticalStack gap={2}>
                            <Text variant="bodyMd">{step.text}</Text>
                        <HorizontalStack gap={2}>
                            <Button onClick={()=> navigate(step.url)} size="slim">Configure now</Button>
                            <Button plain onClick={()=> markAsComplete(step.id)} size="slim">Skip step</Button>
                        </HorizontalStack>
                        <br/>
                    </VerticalStack>
                : null }
                </Box>
            </VerticalStack>
        )
    }

    return (
        <Card padding={0}>
            <VerticalStack gap={3}>
                <Box padding={5} background="bg-info-subdued-hover">
                    <VerticalStack gap={3}>
                        <Text variant="bodyLg" fontWeight="semibold">Get started checklist</Text>
                        <HorizontalStack gap={1}>
                            <Box width='85%'>
                                <ProgressBar size="small" progress={((stepsCompleted * 100) / 5)} color="primary" />
                            </Box>
                            <Text color="subdued" variant="bodyMd">{((stepsCompleted * 100) / 5)}%</Text>
                        </HorizontalStack>
                    </VerticalStack>
                </Box>
                <Box padding={3} paddingInlineEnd={5} paddingInlineStart={5}>
                    {steps.map((step, index)=>(
                        <StepConnection step={step} index={index} key={step.title}/>
                    ))}
                </Box>
                <Divider/>
            </VerticalStack>
        
        </Card>
    )
}

export default InitialSteps