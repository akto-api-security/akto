import { Avatar, Badge, Box, Button, HorizontalGrid, HorizontalStack, LegacyCard, ProgressBar, ResourceItem, ResourceList, Scrollable, Text, VerticalStack, VideoThumbnail } from '@shopify/polaris'
import React, { useState } from 'react'
import quickStartFunc from '../transform'
import DropdownSearch from '../../../components/shared/DropdownSearch'
import SearchField from "../../../components/shared/SearchField"
import "../QuickStart.css"
import PersistStore from '../../../../main/PersistStore'

function NewConnection() {

    const totalTasks = 3
    const tasksCompleted = PersistStore(state => state.quickstartTasksCompleted)
    const setTasksCompleted = PersistStore(state => state.setQuickstartTasksCompleted)

    const calculateWidth = () => {
        let width = Math.floor((tasksCompleted * 100)/ totalTasks)
        return width
    }

    const connectorsList = quickStartFunc.getConnectorsList()
    const [listItems, setListItems] = useState(connectorsList)
    const [connectors, setConnectors] = useState(null)
    
    const dropdownList = quickStartFunc.convertListForMenu(connectorsList)
    const thumbnailUrl = "https://img.youtube.com/vi/fRyusl8ppdY/sddefault.jpg"

    const knowMoreLabel = (
        <HorizontalGrid gap="10" columns="2">
            <VerticalStack gap="4">
                <Text variant='bodyMd'>
                    Akto is an open source, instant API security platform that takes only 60 secs to get started. Akto is used by security teams to maintain a continuous inventory of APIs, test APIs for vulnerabilities and find runtime issues. Akto offers tests for all OWASP top 10 and HackerOne Top 10 categories including BOLA, authentication, SSRF, XSS, security configurations, etc.
                </Text>
                <Button onClick={() => setTasksCompleted(1)} primary>Mark as complete</Button>
            </VerticalStack>
            <VideoThumbnail
                videoLength={195}
                thumbnailUrl={thumbnailUrl}
                onClick={() => window.open("https://www.youtube.com/watch?v=fRyusl8ppdY&ab_channel=Akto")}
            />
        </HorizontalGrid>
    )

    const searchFunc = (items) => {
        setListItems(items)
    }

    const connectorSelected = (item) =>{
        setConnectors(item)
        setTasksCompleted(2)
    }

    const openDocs = (docsUrl) => {
        if(docsUrl && docsUrl.length > 0){
            window.open(docsUrl)
        }
    }

    const setConnector = (label) => {
        let obj = connectorsList.find(element => element.label === label)
        setConnectors(obj)
    }

    function renderItem(item){
        const {icon,label,text,badge, docsUrl} = item
        const media = (
            <Box padding={"2"} borderWidth='1' borderColor='border-subdued' borderRadius='2'>
                <Avatar customer size="extraSmall" name={label} source={icon} shape="square"/>
            </Box>
        )
        return(
            <ResourceItem id={label} onClick={()=> connectorSelected(item)} media={media}>
                <div className='connector-item'>
                    <Text fontWeight="semibold" variant="bodySm">
                        {label}
                        {badge && badge.length > 0 ? <Badge size='small' status='info'>{badge}</Badge> : null}
                    </Text>
                    <div className='see-docs'>
                    <Button plain onClick={(event) => { 
                            event.stopPropagation(); 
                            openDocs(docsUrl); 
                        }}    
                    >
                        Go to docs
                    </Button>
                    </div>
                </div>
                <Text variant='bodySm' color="subdued">{text}</Text>
            </ResourceItem>
        )
    }

    const allConnectorsLabel = (
        <VerticalStack gap="3">
            <SearchField getSearchedItems={searchFunc} placeholder={`Search within ${connectorsList.length} connectors available.`} items={connectorsList} />
            <Scrollable style={{height: '300px'}} focusable shadow>
                <div className='items-list'>
                    <ResourceList 
                        renderItem={renderItem}
                        items={listItems}
                    />
                </div>
            </Scrollable>
        </VerticalStack>
    )

    const trafficScreenLabel = (
        <div className='connector-container'>
            <VerticalStack gap="4">
                <DropdownSearch optionsList={dropdownList} value={connectors?.label} avatarIcon={connectors?.icon} setSelected={(item)=> setConnector(item)} dropdownSearchKey="value"/>
                {connectors?.component}
            </VerticalStack>
        </div>
    )

    const tasksList = [
        {
            id: "know_more",
            label: "Know Akto",
            component: knowMoreLabel,
        },
        {
            id: "all_connectors",
            label: "Select Data Source",
            component: allConnectorsLabel
        },
        {
            id: "connectors_screen",
            label: "Connector traffic data",
            component: trafficScreenLabel,
        }
    ]
        
    return (
        <div style={{padding: '32px 15vw'}}>
            <LegacyCard title="Your quick start guide">
                <LegacyCard.Section>
                    <VerticalStack gap="5">
                        <p>Use this personalized guide to get your traffic and start testing.</p>
                        <HorizontalStack gap="3">
                            <Text variant='bodyMd' color='subdued' fontWeight="medium">{tasksCompleted} of {totalTasks} tasks completed</Text>
                            <Box width='36vw'>
                                <ProgressBar color='success' progress={calculateWidth()} size='small'/>
                            </Box>
                        </HorizontalStack>
                    </VerticalStack>
                </LegacyCard.Section>

                <LegacyCard.Section>
                    <VerticalStack gap="5">
                        {tasksList.map((element,index) => (
                            <VerticalStack gap="5" key={element?.id}>
                                <HorizontalStack gap="3">
                                    <Button plain monochrome onClick={() => setTasksCompleted(index)}>
                                        <Avatar customer name='circle' size="extraSmall"
                                            source={tasksCompleted > index ? "/public/circle_check.svg" : "/public/circle_icon.svg"}
                                        />
                                    </Button>
                                    <Text variant='bodyMd' fontWeight={tasksCompleted === index ? "semibold" : "medium"}>{element?.label}</Text>
                                    {tasksCompleted === index ? element?.component : null}
                                </HorizontalStack>
                            </VerticalStack>
                        
                        ))}
                    </VerticalStack>
                </LegacyCard.Section>
            </LegacyCard>
        </div>
    )
}

export default NewConnection