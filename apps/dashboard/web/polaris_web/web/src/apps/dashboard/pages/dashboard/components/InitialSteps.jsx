import { Avatar, Box, Button, Card, Divider, HorizontalStack, ProgressBar, Text, VerticalStack } from '@shopify/polaris'
import React, { useState } from 'react'
import { ChevronDownMinor, ChevronUpMinor } from "@shopify/polaris-icons"
import { useNavigate } from 'react-router-dom';

function InitialSteps() {

    const [stepsCompleted, setStepsCompleted] = useState(0) ;
    const [dropdownActive, setDropdownActive] = useState(-1) ;
    const navigate = useNavigate() ;

    const steps = [
        {
            text: 'Lorem ipsum dolor sit amet, consectetur adipiscing elit. Cras ac ipsum pellentesque sem sagittis scelerisque. Maecenas pharetra nulla vel risus hendrerit, sit amet scelerisque leo varius. Mauris vel egestas quam, a vulputate tellus. Etiam a lacus ex. Aenean porttitor odio vel tortor ornare pulvinar 1',
            title: 'Automated traffic',
            url: '/dashboard/quick-start'
        },
        {
            text: 'Lorem ipsum dolor sit amet, consectetur adipiscing elit. Cras ac ipsum pellentesque sem sagittis scelerisque. Maecenas pharetra nulla vel risus hendrerit, sit amet scelerisque leo varius. Mauris vel egestas quam, a vulputate tellus. Etiam a lacus ex. Aenean porttitor odio vel tortor ornare pulvinar 2',
            title: 'Slack alerts',
            url: '/dashboard/settings/integrations/slack'
        },
        {
            text: 'Lorem ipsum dolor sit amet, consectetur adipiscing elit. Cras ac ipsum pellentesque sem sagittis scelerisque. Maecenas pharetra nulla vel risus hendrerit, sit amet scelerisque leo varius. Mauris vel egestas quam, a vulputate tellus. Etiam a lacus ex. Aenean porttitor odio vel tortor ornare pulvinar 3',
            title: 'SSO',
            url: '/dashboard/settings/integrations/github_sso'
        },
        {
            text: 'Lorem ipsum dolor sit amet, consectetur adipiscing elit. Cras ac ipsum pellentesque sem sagittis scelerisque. Maecenas pharetra nulla vel risus hendrerit, sit amet scelerisque leo varius. Mauris vel egestas quam, a vulputate tellus. Etiam a lacus ex. Aenean porttitor odio vel tortor ornare pulvinar 4',
            title: 'Invite team members',
            url: '/dashboard/settings/users'
        },
        {
            text: 'Lorem ipsum dolor sit amet, consectetur adipiscing elit. Cras ac ipsum pellentesque sem sagittis scelerisque. Maecenas pharetra nulla vel risus hendrerit, sit amet scelerisque leo varius. Mauris vel egestas quam, a vulputate tellus. Etiam a lacus ex. Aenean porttitor odio vel tortor ornare pulvinar 5',
            title: 'CI/CD',
            url: '/dashboard/settings/integrations/ci-cd',
        }
    ]

    const markAsComplete = (index) => {
        if(index === stepsCompleted){
            setStepsCompleted(index + 1) ;
            setDropdownActive(index + 1)
        }else{
            return ;
        }
    }

    const expandContent = (index) => {
        if(index === dropdownActive){
            setDropdownActive(-1)
        }else{
            setDropdownActive(index)
        }
    }

    function StepConnection({step,index}) {
        return(
            <VerticalStack gap={4}>
                <HorizontalStack gap={2}>
                    <Avatar customer name='circle' size="extraSmall"
                        source={stepsCompleted > index ? "/public/circle_check.svg" : "/public/circle_icon.svg"}
                    />
                    <Box width="85%">
                        <HorizontalStack align="space-between">
                            <Text variant="bodyMd" color={stepsCompleted > index ? 'subdued' : ''} fontWeight={stepsCompleted > index ? "" : 'medium'}>
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
                            <Button plain onClick={()=> markAsComplete(index)} size="slim">Skip step</Button>
                        </HorizontalStack>
                        <br/>
                    </VerticalStack>
                : null }
                </Box>
            </VerticalStack>
        )
    }

    return (
        <div style={{paddingTop: '76px' , paddingRight: '32px'}}>
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
        </div>
    )
}

export default InitialSteps