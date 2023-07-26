import { Avatar, Badge, Box, Button, HorizontalGrid, LegacyCard, ProgressBar, RadioButton, ResourceItem, ResourceList, Scrollable, Text, VideoThumbnail } from '@shopify/polaris'
import React, { useState } from 'react'
import quickStartFunc from '../tranform'
import DropdownSearch from '../../../components/shared/DropdownSearch'
import SearchField from "../../../components/shared/SearchField"
import "../QuickStart.css"

function NewConnection() {

    const totalTasks = 3
    const [tasksCompleted, setTasksCompleted] = useState(0)
    const maxWidth = 600

    const calculateWidth = () => {
        let width = Math.floor((tasksCompleted * 100)/ totalTasks)
        return width
    }

    const connectorsList = quickStartFunc.getConnectorsList()
    const [listItems, setListItems] = useState(connectorsList)
    const [connectors, setConnectors] = useState(null)
    
    const dropdownList = quickStartFunc.convertListForMenu(connectorsList)

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
        const media = <Avatar customer size="small" name={label} source={icon}/>;
        return(
            <ResourceItem id={label} onClick={()=> connectorSelected(item)} media={media}>
                <div className='connector-item'>
                    <Text fontWeight="bold" as="h3" on>
                        {label}
                        {badge && badge.length > 0 ? <Badge size='small' status='success'>{badge}</Badge> : null}
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
                <div>{text}</div>
            </ResourceItem>
        )
    }

    const headerComponent = (
        <SearchField getSearchedItems={searchFunc} placeholder={`Search within ${connectorsList.length} connectors available.`} items={connectorsList} />
    )

    const allConnectorsLabel = (
        tasksCompleted === 1 ?
        <Box>
            <Scrollable style={{height: '300px'}} focusable shadow>
                <div className='items-list'>
                    <ResourceList 
                        renderItem={renderItem}
                        items={listItems}
                        filterControl={headerComponent}
                    />
                </div>
            </Scrollable>
            <br/>
        </Box> 
        : <br/>
    )

    const trafficScreenLabel = (
        tasksCompleted === 2 ?
        <div className='connector-container'>
            <DropdownSearch optionsList={dropdownList} value={connectors.label} avatarIcon={connectors.icon} setSelected={(item)=> setConnector(item)}/>
            <br/>
            {connectors.component}
            <br/> 
        </div>
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

export default NewConnection