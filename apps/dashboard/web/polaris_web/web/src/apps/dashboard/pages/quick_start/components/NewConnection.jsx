import { Avatar, Badge, Box, Button, InlineGrid, InlineStack, LegacyCard, ProgressBar, ResourceItem, ResourceList, Scrollable, Text, BlockStack, VideoThumbnail } from '@shopify/polaris'
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
        <InlineGrid gap="1000" columns="2">
            <BlockStack gap="400">
                <Text variant='bodyMd'>
                    Akto is an open source, instant API security platform that takes only 60 secs to get started. Akto is used by security teams to maintain a continuous inventory of APIs, test APIs for vulnerabilities and find runtime issues. Akto offers tests for all OWASP top 10 and HackerOne Top 10 categories including BOLA, authentication, SSRF, XSS, security configurations, etc.
                </Text>
                <Button onClick={() => setTasksCompleted(1)}  variant="primary">Mark as complete</Button>
            </BlockStack>
            <VideoThumbnail
                videoLength={195}
                thumbnailUrl={thumbnailUrl}
                onClick={() => window.open("https://www.youtube.com/watch?v=fRyusl8ppdY&ab_channel=Akto")}
            />
        </InlineGrid>
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
            <Box padding={"200"} borderWidth='1' borderColor='border-secondary' borderRadius='2'>
                <Avatar customer size="xs" name={label} source={icon} shape="square"/>
            </Box>
        )
        return (
            <ResourceItem id={label} onClick={()=> connectorSelected(item)} media={media}>
                <div className='connector-item'>
                    <Text fontWeight="semibold" variant="bodySm">
                        {label}
                        {badge && badge.length > 0 ? <Badge size='small' tone='info'>{badge}</Badge> : null}
                    </Text>
                    <div className='see-docs'>
                    <Button

                        onClick={(event) => { 
                                event.stopPropagation(); 
                                openDocs(docsUrl); 
                            }}
                        variant="plain">
                        Go to docs
                    </Button>
                    </div>
                </div>
                <Text variant='bodySm' color="subdued">{text}</Text>
            </ResourceItem>
        );
    }

    const allConnectorsLabel = (
        <BlockStack gap="300">
            <SearchField getSearchedItems={searchFunc} placeholder={`Search within ${connectorsList.length} connectors available.`} items={connectorsList} />
            <Scrollable style={{height: '300px'}} focusable shadow>
                <div className='items-list'>
                    <ResourceList 
                        renderItem={renderItem}
                        items={listItems}
                    />
                </div>
            </Scrollable>
        </BlockStack>
    )

    const trafficScreenLabel = (
        <div className='connector-container'>
            <BlockStack gap="400">
                <DropdownSearch optionsList={dropdownList} value={connectors?.label} avatarIcon={connectors?.icon} setSelected={(item)=> setConnector(item)} dropdownSearchKey="value"/>
                {connectors?.component}
            </BlockStack>
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
                    <BlockStack gap="500">
                        <p>Use this personalized guide to get your traffic and start testing.</p>
                        <InlineStack gap="300">
                            <Text variant='bodyMd' color='subdued' fontWeight="medium">{tasksCompleted} of {totalTasks} tasks completed</Text>
                            <Box width='36vw'>
                                <ProgressBar tone='success' progress={calculateWidth()} size='small'/>
                            </Box>
                        </InlineStack>
                    </BlockStack>
                </LegacyCard.Section>

                <LegacyCard.Section>
                    <BlockStack gap="500">
                        {tasksList.map((element,index) => (
                            <BlockStack gap="500" key={element?.id}>
                                <InlineStack gap="300">
                                    <Button   onClick={() => setTasksCompleted(index)} variant="monochromePlain">
                                        <Avatar customer name='circle' size="xs"
                                            source={tasksCompleted > index ? "/public/circle_check.svg" : "/public/circle_icon.svg"}
                                        />
                                    </Button>
                                    <Text variant='bodyMd' fontWeight={tasksCompleted === index ? "semibold" : "medium"}>{element?.label}</Text>
                                    {tasksCompleted === index ? element?.component : null}
                                </InlineStack>
                            </BlockStack>
                        
                        ))}
                    </BlockStack>
                </LegacyCard.Section>
            </LegacyCard>
        </div>
    );
}

export default NewConnection