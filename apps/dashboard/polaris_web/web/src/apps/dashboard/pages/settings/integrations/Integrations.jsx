import React from 'react'
import {
    Page,
    LegacyCard,
    ResourceList,
    Avatar,
    ResourceItem,
    Text,
    Badge,
    Button,
  } from '@shopify/polaris';
import {useState} from 'react';
import '../settings.css'
import LayoutWithTabs from '../../../components/layouts/LayoutWithTabs';
import {Outlet, useNavigate} from 'react-router-dom'

function Integrations() {

    const [sortValue, setSortValue] = useState('DATE_MODIFIED_DESC');
    const navigate = useNavigate()

    let burpSuiteObj = {
        id: 'burp',
        name:'Burp Suite',
        source: '/public/burp_logo.svg'
    }
    let postmanObj={
        id: 'postman',
        name:'Postman',
        source: '/public/postman_logo.svg'
    }
    let aktoApiObj={
        id: 'akto_apis',
        name:'Akto API',
        source: '/public/aktoApis_logo.svg'
    }
    let ciCdObj={
        id: 'ci_cd',
        name:'CI/CD',
        source: '/public/ciCd_logo.svg'
    }
    let aktoGptObj={
        id: 'akto_gpt',
        name:'Akto GPT',
        source: '/public/gpt_logo.svg'
    }
    let slackObj={
        id: 'slack',
        name:'Slack',   
        source: '/public/slack_logo.svg'
    }
    let webhooksObj={
        id: 'webhooks',
        name:'Webhooks',
        source: '/public/webhooks_logo.svg'
    }

    let currObjs = [burpSuiteObj,postmanObj,aktoApiObj,ciCdObj,aktoGptObj,slackObj,webhooksObj]
    const [currItems , setCurrentItems] = useState(currObjs)
    const tabs = [
        {
            id: 'all',
            content: <span>All <Badge status='new'>7</Badge></span>,
            component: <TabsList />
        },
        {
            id: 'traffic',
            content: <span>Traffic Source <Badge status='new'>1</Badge></span>,
            component: <TabsList />
        },
        {
            id: 'manage',
            content: <span>Api Management <Badge status='new'>1</Badge></span>,
            component: <TabsList />
        },
        {
            id: 'ai',
            content: <span>AI <Badge status='new'>1</Badge></span>,
            component: <TabsList />
        },
        {
            id: 'alerts',
            content: <span>Alerts <Badge status='new'>2</Badge></span>,
            component: <TabsList />
        },
        {
            id: 'automation',
            content: <span>Automation <Badge status='new'>2</Badge></span>,
            component: <TabsList />
        }
    ]

    const handleCurrTab = (tab) =>{
        switch (tab.id) {
            case 'traffic':
              currObjs = [burpSuiteObj]
              setCurrentItems(currObjs)
              break;

            case 'manage':
              currObjs= [postmanObj]
              setCurrentItems(currObjs)
              break;

            case 'ai':
                currObjs= [aktoGptObj]
                setCurrentItems(currObjs)
                break;

            case 'alerts':
                currObjs= [slackObj,webhooksObj]
                setCurrentItems(currObjs)
                break;

            case 'automation':
                currObjs= [aktoApiObj,ciCdObj]
                setCurrentItems(currObjs)
                break;

            default:
                currObjs = [burpSuiteObj,postmanObj,aktoApiObj,ciCdObj,aktoGptObj,slackObj,webhooksObj]
                setCurrentItems(currObjs)
                break;
          }
    }
    const handleTab = (tab)=>{
        navigate(tab)
    }

    function renderItem(item) {
        const {id, source, name} = item;
        const media = <Avatar customer size="medium" name={name} source={source}/>;
        const sourceActions = (item) => {
            return [
              {
                content: 'Configure',
                onClick: () => handleTab(item),
              },
            ];
          };
    
        return (
          <ResourceItem
            id={id}
            media={media}
            shortcutActions={sourceActions(id)}
            persistActions
          >
            <Text fontWeight="bold" as="h3">
              {name}
            </Text>
          </ResourceItem>
        );
      }

    function TabsList (){
        return(
            <ResourceList
                items={currItems}
                renderItem={renderItem}
                sortValue={sortValue}
                sortOptions={[
                    {label: 'Newest update', value: 'DATE_MODIFIED_DESC'},
                    {label: 'Oldest update', value: 'DATE_MODIFIED_ASC'},
                  ]}
                onSortChange={(selected) => {
                    setSortValue(selected);
                    console.log(`Sort option changed to ${selected}.`);
                }}
                headerContent={`Showing all Integrations`}
            />
        )
    }

  const redirectDocs = () => {
    window.open("https://docs.akto.io/readme")
  }  
  return (
    <Page
      title="Integrations"
      primaryAction={<Button primary onClick={redirectDocs}>See Docs</Button>}
      divider
    >
        <LegacyCard>
            <LayoutWithTabs tabs={tabs} currTab={handleCurrTab}/>
        </LegacyCard>
    </Page>
  )
}

export default Integrations