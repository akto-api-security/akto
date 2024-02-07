import React, { useEffect, useState } from 'react'
import {Divider, LegacyCard, Text} from '@shopify/polaris';
import settingFunctions from '../module';
import IntegrationsLayout from './IntegrationsLayout';
import PasswordTextField from '../../../components/layouts/PasswordTextField';
import Dropdown from '../../../components/layouts/Dropdown';
import Store from '../../../store';

function Postman() {
    
    const [postmanKey, setPostmanKey] = useState('');
    const [workspaces, setWorkspaces] = useState([]);
    const [selected, setSelected] = useState('');

    const handleSelectChange = (id) =>{
      setSelected(id)
    }
    
    async function fetchPostmanCred() {
      settingFunctions.getPostmanCredentials().then((resp)=> {
        let postmanCred = resp.postmanCred
        if (postmanCred['api_key'] && postmanCred['workspace_id']) {
          setPostmanKey(postmanCred.api_key);
          setSelected(postmanCred.workspace_id);
          fetchWorkSpaces(postmanCred.api_key);
        }
      })
    }
    
    async function fetchWorkSpaces(key) {
      if (key !== null && key.length > 0) {
        let allWorkSpaces = await settingFunctions.fetchPostmanWorkspaces(key);
        let arr = []
        allWorkSpaces.map((val)=>{
            let obj = {
                label: val.name,
                value: val.id
            }
            arr.push(obj)
        })
        setWorkspaces(arr);
        if(arr.length === 0){
          setSelected('')
        }
      }else{
        setWorkspaces([])
        setSelected('')
      }
    }
    
    const setToastConfig = Store(state => state.setToastConfig)
    const setToast = (isActive, isError, message) => {
        setToastConfig({
          isActive: isActive,
          isError: isError,
          message: message
        })
    }
    
    useEffect(() => {
        fetchPostmanCred()
    }, []);

    useEffect(()=> {
      fetchWorkSpaces(postmanKey)
    },[postmanKey])
    
    const seeWork = () => {
        window.open("https://docs.akto.io/traffic-connections/postman")
    }

    async function saveCollection(){
        await settingFunctions.addOrUpdatePostmanCred(postmanKey,selected)
        setToast(true,false,"Collection Saved")
    }
    const PostmanCard = (
        <LegacyCard
            secondaryFooterActions={[{content: 'See how it works',onAction: seeWork}]}
            primaryFooterAction={{content: 'Save', onAction: saveCollection}}
        >
          <LegacyCard.Section>
            <Text variant="headingMd">Integrate Postman</Text>
          </LegacyCard.Section>

          <LegacyCard.Section>
            <PasswordTextField text={postmanKey} helpText="Paste your Postman api key here."
                                setField={setPostmanKey} onFunc={true} field={postmanKey} 
                                label="Postman API key"
            />
            <br/>
            <Dropdown 
              helpText="Select the Postman workspace you wish to import." 
              menuItems={workspaces} selected={handleSelectChange} 
              initial={selected}
              label="Select Postman workspace"/>
          </LegacyCard.Section> 
          
          <Divider />
          <br/>
        </LegacyCard>
    )

    let cardContent = "Seamlessly enhance your web application security with Postman integration, empowering you to efficiently detect vulnerabilities, analyze and intercept web traffic, and fortify your digital defenses. "
    return (
        <IntegrationsLayout title= "Postman" cardContent={cardContent} component={PostmanCard} docsUrl="https://docs.akto.io/traffic-connections/postman"/> 
    )
}

export default Postman