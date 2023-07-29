import React, { useEffect, useState } from 'react'
import api from '../api'
import quickStartFunc from '../tranform'
import { Avatar, Banner, Button, HorizontalStack, LegacyCard, Text, Tooltip, VerticalStack } from '@shopify/polaris'
import {ClipboardMinor} from "@shopify/polaris-icons"
import func from '@/util/func'
import SampleData from '../../../components/shared/SampleData'
import { useNavigate } from 'react-router-dom'
import QuickStartStore from '../quickStartStore'
import Store from '../../../store'

function AwsSource() {

    const [hasRequiredAccess, setHasRequiredAccess] = useState(false)
    const [selectedLBs, setSelectedLBs] = useState([])
    const [existingSelectedLBs, setExistingSelectedLBs] = useState([])
    const [initialLBCount, setInitialLBCount] = useState(0)
    const [aktoDashboardRoleName, setAktoDashboardRoleName] = useState(null)
    const [availableLBs, setAvailableLBs] = useState([])

    const [policyLines, setPolicyLines] = useState(quickStartFunc.getPolicyLines())
    const active = QuickStartStore(state => state.active)
    const navigate = useNavigate()
    const isLocalDeploy = Store(state => state.isLocalDeploy)

    const setToastConfig = Store(state => state.setToastConfig)
    const setToast = (isActive, isError, message) => {
        setToastConfig({
          isActive: isActive,
          isError: isError,
          message: message
        })
    }

    const fetchLBs = async() => {
      if(!isLocalDeploy){
        await api.fetchLBs().then((resp)=> {
          if (!resp.dashboardHasNecessaryRole) {
            let policyLinesCopy = policyLines
            for (let i = 0; i < policyLinesCopy.length; i++) {
              let line = policyLinesCopy[i];
              line = line.replaceAll('AWS_REGION', resp.awsRegion);
              line = line.replaceAll('AWS_ACCOUNT_ID', resp.awsAccountId);
              line = line.replaceAll('MIRRORING_STACK_NAME', resp.aktoMirroringStackName);
              line = line.replaceAll('DASHBOARD_STACK_NAME', resp.aktoDashboardStackName);
              policyLinesCopy[i] = line;
            }
            setPolicyLines(policyLinesCopy)
          }
          setHasRequiredAccess(resp.dashboardHasNecessaryRole)
          setSelectedLBs(resp.selectedLBs);
          for(let i=0; i<resp.availableLBs.length; i++){
              let lb = resp.availableLBs[i];
              let alreadySelected = false;
              for(let j=0; j< resp.selectedLBs.length; j++){
                  if(resp.selectedLBs[j].resourceName === lb.resourceName){
                      alreadySelected = true;
                  }
              }
              lb['alreadySelected'] = alreadySelected;
          }
          setAvailableLBs(resp.availableLBs);
          setExistingSelectedLBs(resp.selectedLBs);
          setInitialLBCount(resp.selectedLBs.length);
          setAktoDashboardRoleName(resp.aktoDashboardRoleName); 
        })
      }
    }

    useEffect(()=> {
      fetchLBs()
      if(!isLocalDeploy && !hasRequiredAccess && active === "update"){
        navigate("/dashboard/quick-start/aws-setup")
      }else{
        navigate("/dashboard/quick-start")
      }
    },[])

    const docsUrl = "https://docs.akto.io/getting-started/quick-start-with-akto-self-hosted/aws-deploy"
    const openLink = (url)=> {
      window.open(url)
    }

    const urlAws = "https://us-east-1.console.aws.amazon.com/iam/home#/roles/" + aktoDashboardRoleName  + "$createPolicy?step=edit"
    const formattedJson = func.convertPolicyLines(policyLines)
    const dataObj = {
      json: formattedJson,
    }

    const steps = [
      {
        text: "Grab the policy JSON below and navigate to Akto Dashboard's current role by clicking ",
        textComponent: <a target='_blank' href={urlAws}>here</a>, 
      },
      {
        text: "We will create an inline policy, navigate to JSON tab and paste the copied JSON here."
      },
      {
        text: "Click on 'Review policy'."
      },
      {
        text: "Now lets name the policy as 'AktoDashboardPolicy'."
      },
      {
        text: "Finally create the policy by clicking on 'Create policy'."
      },
    ]

    const copyRequest = () => {
      let jsonString  = JSON.stringify(formattedJson, null, 2)
      navigator.clipboard.writeText(jsonString)
      setToast(true, false, "Policy copied to clipboard.")
    }

    const noAccessComponent = (
      <VerticalStack gap="1">
          {steps.map((element,index) => (
              <VerticalStack gap="1" key={index}>
                  <HorizontalStack gap="1" wrap={false} key={element.text}>
                      <span>{index + 1}.</span>
                      <span>{element.text}</span>
                      <span>{element.textComponent}</span>
                  </HorizontalStack>
                  <HorizontalStack gap="3">
                      <div/>
                      {/* <SampleData data={dataObj} /> */}
                      {element?.component}
                  </HorizontalStack>
              </VerticalStack>
          ))}
          <span>6. Click <Button plain onClick={() => navigate(0)}>here</Button> to refresh.</span>
      </VerticalStack>
    )
    const noAccessObject= {
      text: "Your dashboard's instance needs relevant access to setup traffic mirroring, pleasedo the following steps:",
      component: noAccessComponent,
      title: "NoAccess"
    }
    
    const localDeployComponent = (
      <div>
        <Banner title='Set Up Mirroring' status='warning'>
          <span>To setup traffic mirroring from AWS deploy in AWS:</span>
          <br/>
          <Button plain onClick={() => openLink(docsUrl)}>Go to docs</Button>
        </Banner>
      </div>
    )
    const localDeployObj = {
      text: "Use AWS packet mirroring to send duplicate stream of traffic to Akto. No performance impact, only mirrored traffic is used to analyze APIs.",
      component: localDeployComponent,
      title: "Local_Depoy"
    }
     

    const displayObj = isLocalDeploy ? localDeployObj : hasRequiredAccess ? null : noAccessObject
    const headerTitle = (
      <HorizontalStack gap="3">
        <Avatar customer size="medium" name="AWS Logo" source="/public/aws.svg"/>
        <Text variant='headingMd' as='h5'>
          AWS Setup guide
        </Text>
      </HorizontalStack>
    )
    return (
      active === 'update' && displayObj.title === 'NoAccess' ?
        <div style={{marginTop: '2vw', padding: '0 4vw'}}>
          <LegacyCard title={headerTitle}>
            <LegacyCard.Section>
              <Text>{displayObj.text}</Text>
              <br/>
              {displayObj.component}
            </LegacyCard.Section>
            <LegacyCard.Section>
              <div className='copyRequest'>
                <Text>Policy JSON</Text>
                <Tooltip dismissOnMouseOut preferredPosition='above' content="Copy JSON">
                <Button icon={ClipboardMinor} plain onClick={()=> copyRequest()} />
                </Tooltip>
              </div>
              <SampleData data={dataObj} />
            </LegacyCard.Section>
          </LegacyCard>
        </div>
      :
        <div className='card-items'>
          <Text>{displayObj?.text}</Text>
          {displayObj?.component}
          {displayObj.title === "NoAccess" ?
          <VerticalStack gap="1">  
            <div className='copyRequest'>
              <Text>Policy JSON</Text>
              <Tooltip dismissOnMouseOut preferredPosition='above' content="Copy JSON">
                <Button icon={ClipboardMinor} plain  />
              </Tooltip>
            </div>
            <SampleData data={dataObj} />
          </VerticalStack>
           : null }
        </div>
       
    )
}

export default AwsSource