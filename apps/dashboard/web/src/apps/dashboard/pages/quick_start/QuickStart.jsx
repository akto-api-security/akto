import { Box, Button, HorizontalGrid, LegacyCard, ProgressBar, RadioButton, VideoThumbnail } from '@shopify/polaris'
import React, { useState } from 'react'

function QuickStart() {

    const totalTasks = 3
    const [tasksCompleted, setTasksCompleted] = useState(0)
    const maxWidth = 600

    const calculateWidth = () => {
        let width = Math.floor((tasksCompleted * 100)/ totalTasks)
        return width
    }

    

    const knowMoreLabel = (
        tasksCompleted === 0 ?
            <HorizontalGrid gap="10" columns="2">
                <Box>
                    <br/>
                    Lorem ipsum dolor sit amet, consectetur adipiscing elit. Proin tempus risus purus, vitae tempus magna ullamcorper non. Maecenas placerat tempor aliquet. Etiam magna dolor, interdum ac turpis sed, ornare fringilla nibh.
                    <br/>
                    <br/>
                    <Button onClick={() => setTasksCompleted(1)} primary>Mark as complete</Button>
                </Box>
                <Box>
                    <br/>
                    <VideoThumbnail
                        videoLength={80}
                        thumbnailUrl="https://burst.shopifycdn.com/photos/business-woman-smiling-in-office.jpg?width=1850"
                        onClick={() => console.log('clicked')}
                    />
                </Box>
                <br/>
            </HorizontalGrid>
        : <br/>
    )

    const allConnectorsLabel = (
        tasksCompleted === 1 ?
        <br/> 
        : <br/>
    )

    const trafficScreenLabel = (
        tasksCompleted === 2 ?
        <br/> 
        : <br/>
    )

    const tasksList = [
        {
            id: "know_more",
            label: "Know Akto",
            helpText: knowMoreLabel,
        },
        {
            id: "all_connectors",
            label: "Select Data Source",
            helpText: allConnectorsLabel
        },
        {
            id: "connectors_screen",
            label: "Connector traffic data",
            helpText: trafficScreenLabel,
        }
    ]
        
    return (
        <div style={{padding: '32px 15vw'}}>
            <LegacyCard title="Your quick start guide">
                <LegacyCard.Section>
                    <p>Use this personalized guide to get your traffic and start testing.</p>
                    <div style={{display: 'flex', gap: '15px', marginTop: '20px', alignItems: 'center'}}>
                        <span>{tasksCompleted} of {totalTasks} completed</span>
                        <div style={{width: `${maxWidth}px`}}>
                            <ProgressBar progress={calculateWidth()} size='small'/>
                        </div>
                    </div>
                </LegacyCard.Section>

                <LegacyCard.Section>
                    {tasksList.map((element,index) =>(
                        <RadioButton key={element.id} id={element.id} label={element.label} helpText={element.helpText} checked={tasksCompleted > index}/>
                    ))}
                </LegacyCard.Section>
            </LegacyCard>
        </div>
    )
}

export default QuickStart