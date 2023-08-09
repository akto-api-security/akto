import React, { useEffect, useState } from 'react'
import {LegacyCard} from '@shopify/polaris';
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
      let postmanData = await settingFunctions.getPostmanCredentials();
      let postmanCred = postmanData.postmanCred
      if (postmanCred['api_key'] && postmanCred['workspace_id']) {
        setPostmanKey(postmanCred.api_key);
        setSelected(postmanCred.workspace_id);
        fetchWorkSpaces();
      }
    }
    
    async function fetchWorkSpaces() {
      if (postmanKey !== null && postmanKey.length > 0) {
        let allWorkSpaces = await settingFunctions.fetchPostmanWorkspaces(postmanKey);
        let arr = []
        allWorkSpaces.map((val)=>{
            let obj = {
                label: val.name,
                value: val.id
            }
            arr.push(obj)
        })
        setWorkspaces(arr);
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
        fetchWorkSpaces()
    }, [postmanKey]);
    
    const seeWork = () => {
        window.open("https://docs.akto.io/traffic-connections/postman")
    }

    async function saveCollection(){
        await settingFunctions.addOrUpdatePostmanCred(postmanKey,selected)
        setToast(true,false,"Collection Saved")
    }
    const PostmanCard = (
        <LegacyCard title="Integrate Postman" 
            secondaryFooterActions={[{content: 'See how it works',onAction: seeWork}]}
            primaryFooterAction={{content: 'Save', onAction: saveCollection}}
        >
          <LegacyCard.Section title="Postman API key">
            <PasswordTextField text={postmanKey} helpText="Paste your Postman api key here."
                                setField={setPostmanKey} onFunc={true} field={postmanKey}
            />
          </LegacyCard.Section>    
          <LegacyCard.Section title="Select Postman workspace">
            <Dropdown helpText="Select the Postman workspace you wish to import." menuItems={workspaces} selected={handleSelectChange} initial={selected}/>
          </LegacyCard.Section> 
        </LegacyCard>
    )

    let cardContent = "Seamlessly enhance your web application security with Postman integration, empowering you to efficiently detect vulnerabilities, analyze and intercept web traffic, and fortify your digital defenses. "
    return (
        <IntegrationsLayout title= "Postman" cardContent={cardContent} component={PostmanCard} docsUrl="https://docs.akto.io/traffic-connections/postman"/> 
    )
}

export default Postman